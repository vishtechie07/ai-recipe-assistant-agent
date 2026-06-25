document.addEventListener('DOMContentLoaded', function () {
    const R = window.RecipeRenderer;
    const STORAGE_LAST_RECIPE = 'ra_last_recipe';
    const STORAGE_FAVORITES = 'ra_favorites_v1';
    const STORAGE_MIGRATED = 'ra_library_migrated_v1';
    const COLLECTION_ICONS = ['fa-utensils', 'fa-heart', 'fa-leaf', 'fa-fire', 'fa-star', 'fa-book'];
    const LOADING_MESSAGES = [
        'Reading your ingredients…',
        'Picking flavors…',
        'Writing the recipe…',
        'Almost ready…'
    ];

    const els = {
        apiKeyInput: document.getElementById('apiKey'),
        setApiKeyBtn: document.getElementById('setApiKeyBtn'),
        clearApiKeyBtn: document.getElementById('clearApiKeyBtn'),
        apiKeyStatus: document.getElementById('apiKeyStatus'),
        apiKeyError: document.getElementById('apiKeyError'),
        apiKeyErrorMessage: document.getElementById('apiKeyErrorMessage'),
        apiKeyPanel: document.getElementById('apiKeyPanel'),
        apiKeyPanelTitle: document.getElementById('apiKeyPanelTitle'),
        apiKeyPanelSubtitle: document.getElementById('apiKeyPanelSubtitle'),
        apiKeySummaryStatus: document.getElementById('apiKeySummaryStatus'),
        trialExhaustedInline: document.getElementById('trialExhaustedInline'),
        apiKeyNoTrialInfo: document.getElementById('apiKeyNoTrialInfo'),
        headerTrialBadge: document.getElementById('headerTrialBadge'),
        headerTrialText: document.getElementById('headerTrialText'),
        headerTrialProgress: document.getElementById('headerTrialProgress'),
        trialHowBtn: document.getElementById('trialHowBtn'),
        trialInfoModal: document.getElementById('trialInfoModal'),
        closeTrialInfoBtn: document.getElementById('closeTrialInfoBtn'),
        preferencesPanel: document.getElementById('preferencesPanel'),
        generationBlockedNotice: document.getElementById('generationBlockedNotice'),
        generationBlockedText: document.getElementById('generationBlockedText'),
        openApiKeyFromNotice: document.getElementById('openApiKeyFromNotice'),
        form: document.getElementById('recipeForm'),
        generateBtn: document.getElementById('generateBtn'),
        tipsBtn: document.getElementById('tipsBtn'),
        cancelBtn: document.getElementById('cancelGenerateBtn'),
        results: document.getElementById('results'),
        resultsLoading: document.getElementById('resultsLoading'),
        resultsLoadingText: document.getElementById('resultsLoadingText'),
        resultsError: document.getElementById('resultsError'),
        resultsErrorMessage: document.getElementById('resultsErrorMessage'),
        retryBtn: document.getElementById('retryBtn'),
        resultsPanel: document.getElementById('resultsPanel'),
        formError: document.getElementById('formError'),
        formErrorMessage: document.getElementById('formErrorMessage'),
        libraryModal: document.getElementById('libraryModal'),
        confirmModal: document.getElementById('confirmModal'),
        confirmModalMessage: document.getElementById('confirmModalMessage'),
        confirmModalOk: document.getElementById('confirmModalOk'),
        confirmModalCancel: document.getElementById('confirmModalCancel'),
        toggleApiKeyVisibility: document.getElementById('toggleApiKeyVisibility'),
        ingredientChips: document.getElementById('ingredientChips')
    };

    let apiKeyStatusData = null;
    let currentRecipe = null;
    let currentRecipeId = null;
    let currentContentHash = null;
    let currentInSaved = false;
    let libraryCollections = [];
    let libraryChapterCards = [];
    let activeCollectionId = null;
    let activeCollectionTitle = '';
    let activeCollectionSlug = '';
    let confirmResolve = null;
    let lastFocusedElement = null;
    let activeAbort = null;
    let lastFailedAction = null;
    let loadingMessageTimer = null;
    let recipeMoreMenuListenerBound = false;

    checkApiKeyStatus();
    refreshLibraryCount();
    migrateLocalLibraryIfNeeded();
    restoreLastRecipe();

    function getCsrfToken() {
        const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    function readLocalFavoritesMap() {
        try {
            return JSON.parse(localStorage.getItem(STORAGE_FAVORITES) || '{}');
        } catch {
            return {};
        }
    }

    async function apiRequest(method, endpoint, data, options = {}) {
        const headers = { 'Content-Type': 'application/json' };
        const csrf = getCsrfToken();
        if (csrf) headers['X-XSRF-TOKEN'] = csrf;
        const fetchOptions = { method, headers, credentials: 'same-origin', signal: options.signal };
        if (data !== undefined) fetchOptions.body = JSON.stringify(data);
        const response = await fetch(endpoint, fetchOptions);
        let result = {};
        try {
            result = await response.json();
        } catch {
            if (!response.ok) {
                throw new Error(response.status === 429
                    ? 'Too many requests. Please wait a minute.'
                    : 'Something went wrong (' + response.status + ').');
            }
        }
        if (!response.ok || result.success === false) {
            throw new Error(result.error || result.message || 'Request failed');
        }
        return result;
    }

    async function refreshLibraryCount() {
        try {
            const result = await apiRequest('GET', '/library/collections');
            libraryCollections = result.collections || [];
            document.getElementById('libraryCount').textContent = result.totalRecipes ?? 0;
        } catch {
            document.getElementById('libraryCount').textContent = '0';
        }
    }

    async function migrateLocalLibraryIfNeeded() {
        if (localStorage.getItem(STORAGE_MIGRATED)) return;
        const map = readLocalFavoritesMap();
        const favorites = Object.values(map).filter(f => f.structuredRecipe);
        if (!favorites.length) {
            localStorage.setItem(STORAGE_MIGRATED, '1');
            return;
        }
        try {
            const result = await apiRequest('POST', '/library/migrate-local', { favorites });
            localStorage.setItem(STORAGE_MIGRATED, '1');
            localStorage.removeItem(STORAGE_FAVORITES);
            if (result.content) showNotification(result.content, 'success');
            await refreshLibraryCount();
        } catch {
            /* retry next visit */
        }
    }

    function isOnServerTrial() {
        return apiKeyStatusData?.defaultKeyAvailable && !apiKeyStatusData?.hasUserKey;
    }

    function regenerateButtonLabel() {
        return 'Try another';
    }

    function syncPreferencesPanel() {
        const hasPrefs = document.getElementById('cuisine').value
            || document.getElementById('dietaryRestrictions').value.trim();
        if (hasPrefs) els.preferencesPanel.open = true;
    }

    function showResultsPanel() {
        els.resultsPanel.classList.remove('hidden');
    }

    function hideResultsPanel() {
        els.resultsPanel.classList.add('hidden');
        hideResultsError();
        els.cancelBtn.classList.add('hidden');
        els.resultsLoading.classList.add('hidden');
        els.resultsPanel.setAttribute('aria-busy', 'false');
    }

    function closeRecipeMoreMenu() {
        const menu = document.getElementById('recipeMoreMenu');
        const btn = document.getElementById('recipeMoreBtn');
        if (!menu || !btn) return;
        menu.classList.add('hidden');
        btn.setAttribute('aria-expanded', 'false');
    }

    function toggleRecipeMoreMenu() {
        const menu = document.getElementById('recipeMoreMenu');
        const btn = document.getElementById('recipeMoreBtn');
        if (!menu || !btn) return;
        const isHidden = menu.classList.toggle('hidden');
        btn.setAttribute('aria-expanded', isHidden ? 'false' : 'true');
    }

    function bindRecipeMoreMenuDismiss() {
        if (recipeMoreMenuListenerBound) return;
        recipeMoreMenuListenerBound = true;
        document.addEventListener('click', e => {
            if (!e.target.closest('.recipe-actions-more')) closeRecipeMoreMenu();
        });
        document.addEventListener('keydown', e => {
            if (e.key === 'Escape') closeRecipeMoreMenu();
        });
    }

    function persistLastRecipe() {
        if (!currentRecipe) return;
        try {
            sessionStorage.setItem(STORAGE_LAST_RECIPE, JSON.stringify({
                recipe: currentRecipe,
                ingredients: document.getElementById('ingredients').value,
                cuisine: document.getElementById('cuisine').value,
                dietaryRestrictions: document.getElementById('dietaryRestrictions').value,
                contentHash: currentContentHash,
                recipeId: currentRecipeId,
                inSaved: currentInSaved
            }));
        } catch { /* quota */ }
    }

    function restoreLastRecipe() {
        try {
            const raw = sessionStorage.getItem(STORAGE_LAST_RECIPE);
            if (!raw) return;
            const saved = JSON.parse(raw);
            if (!saved.recipe) return;
            document.getElementById('ingredients').value = saved.ingredients || '';
            document.getElementById('cuisine').value = saved.cuisine || '';
            document.getElementById('dietaryRestrictions').value = saved.dietaryRestrictions || '';
            currentRecipe = saved.recipe;
            currentContentHash = saved.contentHash || null;
            currentRecipeId = saved.recipeId || null;
            currentInSaved = !!saved.inSaved;
            syncPreferencesPanel();
            els.results.replaceChildren();
            els.results.appendChild(R.renderStructuredRecipe(saved.recipe));
            appendActionButtons();
            showFavoriteButton(currentInSaved);
            showResultsPanel();
        } catch { /* ignore */ }
    }

    function openApiKeyPanel() {
        els.apiKeyPanel.classList.remove('hidden');
        els.apiKeyPanel.open = true;
        els.apiKeyInput.focus();
    }

    function checkApiKeyStatus() {
        fetch('/api-key-status', { credentials: 'same-origin' })
            .then(r => r.json())
            .then(updateApiKeyUi)
            .catch(() => showApiKeyInput());
    }

    function updateTrialDisplay(data) {
        const max = data.defaultRecipesMax ?? 5;
        const used = data.defaultRecipesUsed ?? 0;
        const remaining = data.defaultTrialsRemaining ?? 0;
        const onServerTrial = data.defaultKeyAvailable && !data.hasUserKey;
        const trialActive = onServerTrial && remaining > 0;

        els.headerTrialBadge.classList.toggle('hidden', !onServerTrial || data.hasUserKey);

        if (onServerTrial && !data.hasUserKey) {
            els.headerTrialText.textContent = remaining + ' of ' + max + ' free left';
            els.headerTrialProgress.style.width = (max > 0 ? Math.min(100, (remaining / max) * 100) : 0) + '%';
        }

        els.apiKeyPanel.classList.toggle('hidden', trialActive);

        if (data.hasUserKey) {
            els.apiKeyPanel.classList.remove('hidden');
            els.apiKeyPanelTitle.textContent = 'Your API Key';
            els.apiKeyPanelSubtitle.textContent = '';
            els.apiKeySummaryStatus.textContent = 'Unlimited recipes with your key';
            els.trialExhaustedInline.classList.add('hidden');
            return;
        }

        if (data.defaultKeyAvailable) {
            if (remaining === 0) {
                els.apiKeyPanel.classList.remove('hidden');
                els.apiKeyPanel.open = true;
                els.apiKeyPanelTitle.textContent = 'API Key';
                els.apiKeyPanelSubtitle.textContent = '(trial used)';
                els.apiKeySummaryStatus.textContent = 'Add your key to continue';
                els.trialExhaustedInline.classList.remove('hidden');
            }
            els.apiKeyNoTrialInfo.classList.add('hidden');
        } else {
            els.apiKeyPanel.classList.remove('hidden');
            els.apiKeyPanel.open = true;
            els.apiKeyPanelTitle.textContent = 'API Key';
            els.apiKeyPanelSubtitle.textContent = '(required)';
            els.apiKeySummaryStatus.textContent = 'No server key — add yours to start';
            els.apiKeyNoTrialInfo.classList.remove('hidden');
            els.trialExhaustedInline.classList.add('hidden');
        }
    }

    function canGenerate(data) {
        if (!data) return false;
        if (data.hasUserKey) return true;
        return data.defaultKeyAvailable && (data.defaultTrialsRemaining ?? 0) > 0;
    }

    function updateGenerationControls(data) {
        apiKeyStatusData = data;
        if (activeAbort) return;
        const allowed = canGenerate(data);
        els.generateBtn.disabled = !allowed;
        els.tipsBtn.disabled = !allowed;
        if (allowed) {
            els.generationBlockedNotice.classList.add('hidden');
            return;
        }
        els.generationBlockedNotice.classList.remove('hidden');
        els.generationBlockedText.textContent = !data.defaultKeyAvailable && !data.hasUserKey
            ? 'Add an OpenAI API key to generate recipes.'
            : 'Free trial used on this device.';
    }

    function updateApiKeyUi(data) {
        els.apiKeyError.classList.add('hidden');
        updateTrialDisplay(data);
        updateGenerationControls(data);
        if (data.hasUserKey) showApiKeyStatus();
        else showApiKeyInput();
    }

    function showApiKeyStatus() {
        els.apiKeyInput.classList.add('hidden');
        els.apiKeyStatus.classList.remove('hidden');
        els.apiKeyNoTrialInfo.classList.add('hidden');
    }

    function showApiKeyInput() {
        els.apiKeyInput.classList.remove('hidden');
        els.apiKeyStatus.classList.add('hidden');
    }

    function showApiKeyError(message) {
        els.apiKeyErrorMessage.textContent = message;
        els.apiKeyError.classList.remove('hidden');
    }

    function startLoadingMessages(initialMessage) {
        stopLoadingMessages();
        let index = 0;
        els.resultsLoadingText.textContent = initialMessage;
        loadingMessageTimer = setInterval(() => {
            index = (index + 1) % LOADING_MESSAGES.length;
            els.resultsLoadingText.textContent = LOADING_MESSAGES[index];
        }, 3500);
    }

    function stopLoadingMessages() {
        if (loadingMessageTimer) {
            clearInterval(loadingMessageTimer);
            loadingMessageTimer = null;
        }
    }

    function showLoading(message) {
        showResultsPanel();
        hideResultsError();
        hideFormError();
        startLoadingMessages(message);
        els.resultsLoading.classList.remove('hidden');
        els.results.classList.add('hidden');
        els.cancelBtn.classList.remove('hidden');
        els.resultsPanel.setAttribute('aria-busy', 'true');
        els.generateBtn.disabled = true;
        els.tipsBtn.disabled = true;
        scrollToResults();
    }

    function hideLoading() {
        stopLoadingMessages();
        els.resultsLoading.classList.add('hidden');
        els.results.classList.remove('hidden');
        els.cancelBtn.classList.add('hidden');
        els.resultsPanel.setAttribute('aria-busy', 'false');
        activeAbort = null;
        if (apiKeyStatusData) updateGenerationControls(apiKeyStatusData);
        const hasContent = els.results.childElementCount > 0;
        const hasError = !els.resultsError.classList.contains('hidden');
        if (!hasContent && !hasError) hideResultsPanel();
    }

    function showFormValidationError(message) {
        els.formErrorMessage.textContent = message;
        els.formError.classList.remove('hidden');
    }

    function hideFormError() {
        els.formError.classList.add('hidden');
    }

    function isIngredientValidationError(message) {
        if (!message) return false;
        const lower = message.toLowerCase();
        return lower.includes('food ingredient') || lower.includes('pantry') || lower.includes("doesn't look like");
    }

    function showResultsError(message, showRetry) {
        showResultsPanel();
        els.resultsErrorMessage.textContent = message;
        els.resultsError.classList.remove('hidden');
        els.retryBtn.classList.toggle('hidden', !showRetry);
    }

    function hideResultsError() {
        els.resultsError.classList.add('hidden');
        els.resultsErrorMessage.textContent = '';
        els.retryBtn.classList.add('hidden');
        lastFailedAction = null;
    }

    function scrollToResults() {
        if (els.resultsPanel.classList.contains('hidden')) showResultsPanel();
        els.resultsPanel.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    }

    function showRecipeResult(recipe, result) {
        currentRecipe = recipe;
        currentContentHash = result?.contentHash || currentContentHash;
        currentRecipeId = result?.recipeId || currentRecipeId;
        currentInSaved = !!result?.favorited;
        hideResultsError();
        els.results.replaceChildren();
        els.results.appendChild(R.renderStructuredRecipe(recipe));
        appendActionButtons();
        showFavoriteButton(currentInSaved);
        els.results.classList.add('recipe-fade-in');
        showResultsPanel();
        persistLastRecipe();
        scrollToResults();
        if (result) {
            checkApiKeyStatus();
            refreshLibraryCount();
        }
    }

    function showTipsResult(tipsResult) {
        currentRecipe = null;
        currentRecipeId = null;
        currentContentHash = null;
        currentInSaved = false;
        hideResultsError();
        closeRecipeMoreMenu();
        els.results.replaceChildren();
        els.results.appendChild(R.renderTipsBanner());
        els.results.appendChild(R.renderCookingTips(tipsResult));
        sessionStorage.removeItem(STORAGE_LAST_RECIPE);
        showResultsPanel();
        checkApiKeyStatus();
        scrollToResults();
    }

    function appendActionButtons() {
        bindRecipeMoreMenuDismiss();
        const actions = document.createElement('div');
        actions.className = 'recipe-actions-bar';
        actions.innerHTML = `
            <button type="button" id="favoriteBtn" class="recipe-action-primary">
                <i class="fas fa-heart" aria-hidden="true"></i><span>Save to Saved</span>
            </button>
            <button type="button" id="regenerateBtn" class="recipe-action-secondary" title="Regenerate with the same ingredients">
                <i class="fas fa-rotate-right" aria-hidden="true"></i><span>${R.escapeHtml(regenerateButtonLabel())}</span>
            </button>
            <div class="recipe-actions-more">
                <button type="button" id="recipeMoreBtn" class="recipe-action-menu-btn" aria-expanded="false" aria-haspopup="true">
                    <i class="fas fa-ellipsis" aria-hidden="true"></i><span>More</span>
                </button>
                <div id="recipeMoreMenu" class="recipe-actions-menu hidden" role="menu">
                    <button type="button" id="printBtn" role="menuitem">
                        <i class="fas fa-print" aria-hidden="true"></i><span>Print</span>
                    </button>
                    <button type="button" id="copyBtn" role="menuitem">
                        <i class="fas fa-copy" aria-hidden="true"></i><span>Copy text</span>
                    </button>
                    <button type="button" id="addToCollectionBtn" role="menuitem">
                        <i class="fas fa-folder-plus" aria-hidden="true"></i><span>Add to collection</span>
                    </button>
                </div>
            </div>`;
        els.results.appendChild(actions);
        document.getElementById('favoriteBtn').addEventListener('click', toggleSaved);
        document.getElementById('regenerateBtn').addEventListener('click', () => els.form.requestSubmit());
        document.getElementById('recipeMoreBtn').addEventListener('click', e => {
            e.stopPropagation();
            toggleRecipeMoreMenu();
        });
        document.getElementById('printBtn').addEventListener('click', () => { closeRecipeMoreMenu(); printRecipe(); });
        document.getElementById('copyBtn').addEventListener('click', () => { closeRecipeMoreMenu(); copyToClipboard(); });
        document.getElementById('addToCollectionBtn').addEventListener('click', () => { closeRecipeMoreMenu(); openAddToCollectionModal(); });
    }

    function printRecipe() {
        if (!currentRecipe) return showNotification('Generate a recipe first.', 'error');
        const w = window.open('', '_blank');
        if (!w) return showNotification('Allow popups to print.', 'error');
        w.document.write(R.renderPrintDocument(currentRecipe));
        w.document.close();
    }

    function copyToClipboard() {
        const text = currentRecipe ? R.recipeToPlainText(currentRecipe) : '';
        if (!text) return showNotification('Nothing to copy.', 'error');
        navigator.clipboard.writeText(text)
            .then(() => showNotification('Copied to clipboard!', 'success'))
            .catch(() => showNotification('Could not copy.', 'error'));
    }

    async function makeRequest(endpoint, data, options = {}) {
        return apiRequest('POST', endpoint, data, options);
    }

    async function runGeneration(endpoint, data, loadingMessage, failedAction) {
        const controller = new AbortController();
        activeAbort = () => controller.abort();
        lastFailedAction = failedAction;
        showLoading(loadingMessage);
        try {
            return await makeRequest(endpoint, data, { signal: controller.signal });
        } finally {
            hideLoading();
        }
    }

    function formPayload() {
        return {
            ingredients: document.getElementById('ingredients').value.trim(),
            cuisine: document.getElementById('cuisine').value,
            dietaryRestrictions: document.getElementById('dietaryRestrictions').value
        };
    }

    function addIngredientChip(name) {
        const field = document.getElementById('ingredients');
        const parts = field.value.split(',').map(s => s.trim()).filter(Boolean);
        if (!parts.some(p => p.toLowerCase() === name.toLowerCase())) {
            parts.push(name);
            field.value = parts.join(', ');
        }
        field.focus();
    }

    els.ingredientChips.addEventListener('click', e => {
        const chip = e.target.closest('.ingredient-chip');
        if (chip) addIngredientChip(chip.dataset.ingredient);
    });

    els.trialHowBtn.addEventListener('click', () => {
        lastFocusedElement = document.activeElement;
        els.trialInfoModal.classList.remove('hidden');
        els.closeTrialInfoBtn.focus();
    });

    function closeTrialInfoModal() {
        els.trialInfoModal.classList.add('hidden');
        if (lastFocusedElement) lastFocusedElement.focus();
    }

    els.closeTrialInfoBtn.addEventListener('click', closeTrialInfoModal);
    els.trialInfoModal.addEventListener('click', e => {
        if (e.target === els.trialInfoModal) closeTrialInfoModal();
    });

    els.setApiKeyBtn.addEventListener('click', async () => {
        const apiKey = els.apiKeyInput.value.trim();
        if (!apiKey) return showApiKeyError('Please enter your OpenAI API key.');
        els.setApiKeyBtn.disabled = true;
        try {
            await makeRequest('/set-api-key', { apiKey });
            checkApiKeyStatus();
            els.apiKeyInput.value = '';
            showNotification('API key saved.', 'success');
        } catch (e) {
            showApiKeyError(e.message);
        } finally {
            els.setApiKeyBtn.disabled = false;
        }
    });

    els.clearApiKeyBtn.addEventListener('click', async () => {
        try {
            await makeRequest('/clear-api-key', {});
            checkApiKeyStatus();
            showNotification('API key removed.', 'info');
        } catch (e) {
            showApiKeyError(e.message);
        }
    });

    els.openApiKeyFromNotice.addEventListener('click', openApiKeyPanel);

    els.toggleApiKeyVisibility.addEventListener('click', () => {
        const show = els.apiKeyInput.type === 'password';
        els.apiKeyInput.type = show ? 'text' : 'password';
        els.toggleApiKeyVisibility.querySelector('i').className = show ? 'fas fa-eye-slash' : 'fas fa-eye';
    });

    els.cancelBtn.addEventListener('click', async () => {
        try {
            await apiRequest('POST', '/cancel-generation', {});
        } catch (_) { /* best-effort */ }
        if (activeAbort) activeAbort();
        hideLoading();
        showNotification('Cancelled.', 'info');
    });

    els.retryBtn.addEventListener('click', () => {
        if (lastFailedAction === 'tips') els.tipsBtn.click();
        else if (lastFailedAction === 'generate') els.form.requestSubmit();
    });

    els.form.addEventListener('submit', async e => {
        e.preventDefault();
        const data = formPayload();
        if (!data.ingredients) return showFormValidationError('Please enter at least one ingredient.');
        hideFormError();
        try {
            const result = await runGeneration('/generate-recipe', data, 'Generating your recipe…', 'generate');
            showRecipeResult(result.recipe, result);
        } catch (err) {
            if (err.name === 'AbortError') return;
            if (isIngredientValidationError(err.message)) {
                showFormValidationError(err.message);
                return;
            }
            showResultsError(err.message, true);
            scrollToResults();
        }
    });

    els.tipsBtn.addEventListener('click', async () => {
        const data = formPayload();
        if (!data.ingredients) return showFormValidationError('Enter ingredients for tips.');
        hideFormError();
        try {
            const result = await runGeneration('/get-cooking-tips', data, 'Getting cooking tips…', 'tips');
            showTipsResult(result.cookingTips);
        } catch (err) {
            if (err.name === 'AbortError') return;
            if (isIngredientValidationError(err.message)) {
                showFormValidationError(err.message);
                return;
            }
            showResultsError(err.message, true);
            scrollToResults();
        }
    });

    document.getElementById('clearRecipeBtn').addEventListener('click', async () => {
        if (!(await showConfirm('Clear the form and current recipe?'))) return;
        els.results.replaceChildren();
        document.getElementById('ingredients').value = '';
        document.getElementById('cuisine').value = '';
        document.getElementById('dietaryRestrictions').value = '';
        els.preferencesPanel.open = false;
        hideFormError();
        hideResultsPanel();
        sessionStorage.removeItem(STORAGE_LAST_RECIPE);
        currentRecipe = null;
        currentRecipeId = null;
        currentContentHash = null;
        currentInSaved = false;
        showNotification('Cleared.', 'info');
    });

    async function toggleSaved() {
        if (!currentRecipeId) return showNotification('Generate a recipe first.', 'error');
        try {
            const result = await makeRequest('/library/saved/toggle', { recipeId: currentRecipeId });
            currentInSaved = !!result.favorited;
            showFavoriteButton(currentInSaved);
            showNotification(result.content, currentInSaved ? 'success' : 'info');
            await refreshLibraryCount();
        } catch (e) {
            showNotification(e.message, 'error');
        }
    }

    function showFavoriteButton(isSaved) {
        const btn = document.getElementById('favoriteBtn');
        if (!btn) return;
        btn.classList.toggle('is-saved', isSaved);
        btn.innerHTML = isSaved
            ? '<i class="fas fa-heart" aria-hidden="true"></i><span>Remove from Saved</span>'
            : '<i class="fas fa-heart" aria-hidden="true"></i><span>Save to Saved</span>';
    }

    async function openAddToCollectionModal() {
        if (!currentRecipeId) return showNotification('Generate a recipe first.', 'error');
        try {
            const result = await apiRequest('GET', '/library/collections');
            const list = document.getElementById('addToCollectionList');
            list.replaceChildren();
            (result.collections || []).filter(c => c.slug !== 'saved').forEach(col => {
                const btn = document.createElement('button');
                btn.type = 'button';
                btn.className = 'w-full text-left px-3 py-2 rounded-lg border border-gray-200 hover:bg-primary-50 text-sm font-medium';
                btn.textContent = col.title + ' (' + col.recipeCount + ')';
                btn.addEventListener('click', async () => {
                    try {
                        await apiRequest('POST', '/library/collections/' + col.id + '/recipes', { recipeId: currentRecipeId });
                        showNotification('Added to ' + col.title, 'success');
                        document.getElementById('addToCollectionModal').classList.add('hidden');
                        await refreshLibraryCount();
                    } catch (e) {
                        showNotification(e.message, 'error');
                    }
                });
                list.appendChild(btn);
            });
            document.getElementById('addToCollectionModal').classList.remove('hidden');
        } catch (e) {
            showNotification(e.message, 'error');
        }
    }

    document.getElementById('closeAddToCollectionBtn').addEventListener('click', () => {
        document.getElementById('addToCollectionModal').classList.add('hidden');
    });

    function collectionIcon(slug, index) {
        if (slug === 'saved') return 'fa-heart';
        if (slug === 'my-recipes') return 'fa-utensils';
        return COLLECTION_ICONS[index % COLLECTION_ICONS.length];
    }

    function renderLibraryShelf() {
        const grid = document.getElementById('libraryShelfGrid');
        const empty = document.getElementById('emptyLibraryShelf');
        grid.replaceChildren();
        if (!libraryCollections.length) {
            empty.classList.remove('hidden');
            return;
        }
        empty.classList.add('hidden');
        libraryCollections.forEach((col, index) => {
            const wrap = document.createElement('div');
            wrap.className = 'relative';
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'library-collection-card library-tone-' + (index % 6);
            btn.innerHTML = `<div class="card-icon"><i class="fas ${collectionIcon(col.slug, index)}" aria-hidden="true"></i></div>
                <div class="card-title">${R.escapeHtml(col.title)}</div>
                <div class="card-count">${collectionSubtitle(col)} · ${col.recipeCount} recipe${col.recipeCount === 1 ? '' : 's'}</div>`;
            btn.addEventListener('click', () => openLibraryChapter(col));
            wrap.appendChild(btn);
            if (!col.systemDefault) {
                const del = document.createElement('button');
                del.type = 'button';
                del.className = 'absolute top-2 right-2 text-gray-400 hover:text-red-600 text-xs p-1';
                del.title = 'Delete collection';
                del.innerHTML = '<i class="fas fa-trash" aria-hidden="true"></i>';
                del.addEventListener('click', async ev => {
                    ev.stopPropagation();
                    if (!(await showConfirm('Delete collection “' + col.title + '”? Recipes stay in My Recipes.'))) return;
                    try {
                        await apiRequest('DELETE', '/library/collections/' + col.id);
                        await refreshLibraryCount();
                        renderLibraryShelf();
                        showNotification('Collection deleted.', 'info');
                    } catch (e) {
                        showNotification(e.message, 'error');
                    }
                });
                wrap.appendChild(del);
            }
            grid.appendChild(wrap);
        });
    }

    function collectionSubtitle(col) {
        if (col.slug === 'my-recipes') return 'Every recipe you generate';
        if (col.slug === 'saved') return 'Your starred picks';
        return col.description || 'Custom collection';
    }

    async function openLibraryChapter(col) {
        try {
            const result = await apiRequest('GET', '/library/collections/' + col.id);
            activeCollectionId = col.id;
            activeCollectionTitle = col.title;
            activeCollectionSlug = col.slug;
            libraryChapterCards = result.recipeCards || [];
            showLibraryChapterView();
        } catch (e) {
            showNotification(e.message, 'error');
        }
    }

    function showLibraryShelfView() {
        document.getElementById('libraryShelfView').classList.remove('hidden');
        document.getElementById('libraryChapterView').classList.add('hidden');
        document.getElementById('libraryBackBtn').classList.add('hidden');
        document.getElementById('libraryTitleText').textContent = 'My Cookbook';
        activeCollectionId = null;
        activeCollectionSlug = '';
    }

    function showLibraryChapterView() {
        document.getElementById('libraryShelfView').classList.add('hidden');
        document.getElementById('libraryChapterView').classList.remove('hidden');
        document.getElementById('libraryBackBtn').classList.remove('hidden');
        document.getElementById('libraryTitleText').textContent = activeCollectionTitle;
        const list = document.getElementById('libraryChapterList');
        const empty = document.getElementById('emptyLibraryChapter');
        list.replaceChildren();
        if (!libraryChapterCards.length) {
            empty.classList.remove('hidden');
            return;
        }
        empty.classList.add('hidden');
        const removeLabel = activeCollectionSlug === 'my-recipes' ? 'Delete from library' : 'Remove from collection';
        libraryChapterCards.forEach(card => {
            const row = document.createElement('div');
            row.className = 'bg-gray-50 rounded-lg p-4 border border-gray-200';
            const savedTag = card.inSaved ? ' <i class="fas fa-heart text-red-400 text-xs" title="In Saved"></i>' : '';
            row.innerHTML = `<div class="flex justify-between gap-2 mb-2">
                <h3 class="font-semibold text-gray-900">${R.escapeHtml(card.title || 'Untitled')}${savedTag}</h3>
                <div class="flex gap-2 shrink-0 flex-wrap justify-end">
                    <button type="button" class="view-lib text-primary-600 text-sm font-medium">Open</button>
                    <button type="button" class="remove-lib text-red-600 text-sm font-medium">${R.escapeHtml(removeLabel)}</button>
                </div></div>
                <p class="text-sm text-gray-600 line-clamp-2">${R.escapeHtml(card.ingredients || '')}</p>`;
            row.querySelector('.view-lib').addEventListener('click', () => loadLibraryRecipe(card.id));
            row.querySelector('.remove-lib').addEventListener('click', () => removeFromChapter(card));
            list.appendChild(row);
        });
    }

    async function loadLibraryRecipe(recipeId) {
        try {
            const result = await apiRequest('GET', '/library/recipes/' + recipeId);
            if (!result.recipe) return showNotification('Could not load recipe.', 'error');
            currentRecipe = result.recipe;
            currentRecipeId = result.recipeId;
            currentContentHash = result.contentHash;
            currentInSaved = !!result.favorited;
            document.getElementById('ingredients').value = result.savedIngredients || '';
            document.getElementById('cuisine').value = result.savedCuisine || '';
            document.getElementById('dietaryRestrictions').value = result.savedDietaryRestrictions || '';
            showRecipeResult(result.recipe, result);
            closeLibraryModal();
        } catch (e) {
            showNotification(e.message, 'error');
        }
    }

    async function removeFromChapter(card) {
        const isMyRecipes = activeCollectionSlug === 'my-recipes';
        const msg = isMyRecipes
            ? 'Delete this recipe from your entire library?'
            : 'Remove from this collection?';
        if (!(await showConfirm(msg))) return;
        try {
            if (isMyRecipes) {
                await apiRequest('DELETE', '/library/recipes/' + card.id);
                if (currentRecipeId === card.id) {
                    currentRecipeId = null;
                    currentInSaved = false;
                    showFavoriteButton(false);
                }
            } else {
                await apiRequest('DELETE', '/library/collections/' + activeCollectionId + '/recipes/' + card.id);
            }
            await openLibraryChapter({ id: activeCollectionId, title: activeCollectionTitle });
            await refreshLibraryCount();
            showNotification(isMyRecipes ? 'Recipe deleted.' : 'Removed from collection.', 'info');
        } catch (e) {
            showNotification(e.message, 'error');
        }
    }

    function trapFocus(modal, onClose) {
        const focusable = modal.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        function onKey(e) {
            if (e.key === 'Escape') {
                onClose();
                return;
            }
            if (e.key !== 'Tab' || focusable.length === 0) return;
            if (e.shiftKey && document.activeElement === first) {
                e.preventDefault();
                last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
                e.preventDefault();
                first.focus();
            }
        }
        modal.addEventListener('keydown', onKey);
        return () => modal.removeEventListener('keydown', onKey);
    }

    let releaseLibraryTrap = null;

    document.getElementById('libraryBtn').addEventListener('click', async () => {
        await refreshLibraryCount();
        showLibraryShelfView();
        renderLibraryShelf();
        lastFocusedElement = document.activeElement;
        els.libraryModal.classList.remove('hidden');
        releaseLibraryTrap = trapFocus(els.libraryModal, closeLibraryModal);
        document.getElementById('closeLibraryBtn').focus();
    });

    function closeLibraryModal() {
        els.libraryModal.classList.add('hidden');
        showLibraryShelfView();
        if (releaseLibraryTrap) releaseLibraryTrap();
        if (lastFocusedElement) lastFocusedElement.focus();
    }

    document.getElementById('closeLibraryBtn').addEventListener('click', closeLibraryModal);
    document.getElementById('libraryBackBtn').addEventListener('click', () => {
        showLibraryShelfView();
        renderLibraryShelf();
    });
    els.libraryModal.addEventListener('click', e => {
        if (e.target === els.libraryModal) closeLibraryModal();
    });

    document.getElementById('newCollectionForm').addEventListener('submit', async e => {
        e.preventDefault();
        const title = document.getElementById('newCollectionTitle').value.trim();
        if (!title) return;
        try {
            await apiRequest('POST', '/library/collections', { title });
            document.getElementById('newCollectionTitle').value = '';
            await refreshLibraryCount();
            renderLibraryShelf();
            showNotification('Collection created.', 'success');
        } catch (err) {
            showNotification(err.message, 'error');
        }
    });

    function showNotification(message, type) {
        const colors = { success: 'bg-green-600', error: 'bg-red-600', info: 'bg-gray-800' };
        const toast = document.createElement('div');
        toast.className = (colors[type] || colors.success) + ' text-white px-4 py-2.5 rounded-lg shadow-lg pointer-events-auto text-sm text-center';
        toast.textContent = message;
        document.getElementById('toastRegion').appendChild(toast);
        setTimeout(() => toast.remove(), 3500);
    }

    function showConfirm(message) {
        return new Promise(resolve => {
            confirmResolve = resolve;
            els.confirmModalMessage.textContent = message;
            els.confirmModal.classList.remove('hidden');
            els.confirmModalOk.focus();
        });
    }

    function closeConfirm(result) {
        els.confirmModal.classList.add('hidden');
        if (confirmResolve) confirmResolve(result);
        confirmResolve = null;
    }

    els.confirmModalOk.addEventListener('click', () => closeConfirm(true));
    els.confirmModalCancel.addEventListener('click', () => closeConfirm(false));
});
