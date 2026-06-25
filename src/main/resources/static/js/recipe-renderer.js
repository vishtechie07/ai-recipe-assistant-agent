/* Structured recipe rendering — no text parsing */
window.RecipeRenderer = (function () {
    function escapeHtml(text) {
        if (text == null) return '';
        const div = document.createElement('div');
        div.textContent = String(text);
        return div.innerHTML;
    }

    function formatNutritionLabel(key) {
        const labels = { calories: 'Calories', protein: 'Protein', carbs: 'Carbs', fat: 'Fat' };
        if (labels[key]) return labels[key];
        return key.replace(/([A-Z])/g, ' $1').replace(/^./, s => s.toUpperCase());
    }

    function renderStructuredRecipe(recipe) {
        const root = document.createElement('div');
        root.className = 'recipe-display';

        if (recipe.name) {
            const header = document.createElement('div');
            header.className = 'recipe-header';
            header.innerHTML = `<h1 class="recipe-title">${escapeHtml(recipe.name)}</h1>`;
            root.appendChild(header);
        }

        const metaItems = [];
        if (recipe.preparationTime) metaItems.push(['Prep', recipe.preparationTime]);
        if (recipe.cookingTime) metaItems.push(['Cook', recipe.cookingTime]);
        if (recipe.servings) metaItems.push(['Serves', recipe.servings]);
        if (metaItems.length) {
            const meta = document.createElement('div');
            meta.className = 'recipe-meta';
            metaItems.forEach(([label, value]) => {
                meta.innerHTML += `<div class="meta-item"><div class="meta-label">${escapeHtml(label)}</div><div class="meta-value">${escapeHtml(value)}</div></div>`;
            });
            root.appendChild(meta);
        }

        appendListSection(root, 'Ingredients', 'fa-list', recipe.ingredients, 'ingredients-list');
        appendListSection(root, 'Instructions', 'fa-utensils', recipe.instructions, 'instructions-list', true);

        if (recipe.tips && recipe.tips.length) {
            const section = document.createElement('div');
            section.className = 'recipe-section';
            section.innerHTML = `<div class="tips-section"><h2 class="section-title"><i class="fas fa-lightbulb" aria-hidden="true"></i>Cooking Tips</h2><ul></ul></div>`;
            const ul = section.querySelector('ul');
            recipe.tips.forEach(tip => {
                const li = document.createElement('li');
                li.textContent = tip;
                ul.appendChild(li);
            });
            root.appendChild(section);
        }

        if (recipe.nutrition && Object.keys(recipe.nutrition).length) {
            const section = document.createElement('div');
            section.className = 'recipe-section';
            const grid = document.createElement('div');
            grid.className = 'nutrition-section';
            grid.innerHTML = '<h2 class="section-title"><i class="fas fa-chart-pie" aria-hidden="true"></i>Nutrition</h2>';
            const items = document.createElement('div');
            items.className = 'nutrition-grid';
            Object.entries(recipe.nutrition).forEach(([label, value]) => {
                items.innerHTML += `<div class="nutrition-item"><div class="nutrition-value">${escapeHtml(value)}</div><div class="nutrition-label">${escapeHtml(formatNutritionLabel(label))}</div></div>`;
            });
            grid.appendChild(items);
            section.appendChild(grid);
            root.appendChild(section);
        }

        return root;
    }

    function appendListSection(root, title, icon, items, listClass, ordered) {
        if (!items || !items.length) return;
        const section = document.createElement('div');
        section.className = 'recipe-section';
        section.innerHTML = `<h2 class="section-title"><i class="fas ${icon}" aria-hidden="true"></i>${title}</h2>`;
        const list = document.createElement(ordered ? 'ol' : 'ul');
        list.className = listClass;
        items.forEach(item => {
            const li = document.createElement('li');
            li.textContent = item;
            list.appendChild(li);
        });
        section.appendChild(list);
        root.appendChild(section);
    }

    function renderTipsBanner() {
        const banner = document.createElement('div');
        banner.className = 'tips-mode-banner';
        banner.innerHTML = '<i class="fas fa-lightbulb" aria-hidden="true"></i> Tips only — not a full recipe. Use <strong>Generate Recipe</strong> for ingredients and steps.';
        return banner;
    }

    function renderCookingTips(tipsResult) {
        const root = document.createElement('div');
        root.className = 'recipe-display';
        const section = document.createElement('div');
        section.className = 'recipe-section';
        section.innerHTML = '<h2 class="section-title"><i class="fas fa-lightbulb" aria-hidden="true"></i>Cooking Tips</h2>';
        const ul = document.createElement('ul');
        ul.className = 'ingredients-list';
        (tipsResult.tips || []).forEach(tip => {
            const li = document.createElement('li');
            li.textContent = tip;
            ul.appendChild(li);
        });
        section.appendChild(ul);
        root.appendChild(section);
        return root;
    }

    function recipeToPlainText(recipe) {
        if (!recipe) return '';
        const lines = [];
        if (recipe.name) lines.push(recipe.name, '');
        if (recipe.preparationTime) lines.push('Prep: ' + recipe.preparationTime);
        if (recipe.cookingTime) lines.push('Cook: ' + recipe.cookingTime);
        if (recipe.servings) lines.push('Serves: ' + recipe.servings);
        if (recipe.ingredients?.length) {
            lines.push('', 'Ingredients:');
            recipe.ingredients.forEach(i => lines.push('- ' + i));
        }
        if (recipe.instructions?.length) {
            lines.push('', 'Instructions:');
            recipe.instructions.forEach((step, idx) => lines.push((idx + 1) + '. ' + step));
        }
        if (recipe.tips?.length) {
            lines.push('', 'Tips:');
            recipe.tips.forEach(t => lines.push('- ' + t));
        }
        return lines.join('\n');
    }

    function renderPrintDocument(recipe) {
        const inner = renderStructuredRecipe(recipe).outerHTML;
        return `<!DOCTYPE html><html><head><title>${escapeHtml(recipe.name || 'Recipe')}</title>
            <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css" rel="stylesheet">
            <link href="/css/recipe.css" rel="stylesheet"></head>
            <body class="print-body"><div class="recipe-display print-pad">${inner}</div>
            <script>window.onload=()=>window.print()</script></body></html>`;
    }

    return {
        escapeHtml,
        formatNutritionLabel,
        renderStructuredRecipe,
        renderTipsBanner,
        renderCookingTips,
        recipeToPlainText,
        renderPrintDocument
    };
})();
