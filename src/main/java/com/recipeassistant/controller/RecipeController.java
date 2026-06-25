package com.recipeassistant.controller;

import com.recipeassistant.model.*;
import com.recipeassistant.service.*;
import com.recipeassistant.util.ClientIpResolver;
import com.recipeassistant.util.ContentHashUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
public class RecipeController {

    private static final Logger log = LoggerFactory.getLogger(RecipeController.class);
    private static final String GENERIC_ERROR = "An error occurred. Please try again later.";
    private static final String TRIAL_DEVICE_NOTICE =
        "Free trial is tracked per device (browser cookie), not per session. Clearing cookies resets the trial.";

    @Autowired private OpenAIService openAIService;
    @Autowired private SecurityAuditService securityAuditService;
    @Autowired private EncryptionService encryptionService;
    @Autowired private OpenAiKeyService openAiKeyService;
    @Autowired private TrialClientService trialClientService;
    @Autowired private RecipeLibraryService recipeLibraryService;
    @Autowired private GenerationCancellationService cancellationService;

    @Value("${app.asset-version:1.0.0}")
    private String assetVersion;

    @GetMapping("/")
    public String index(Model model, HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        String clientId = trialClientService.ensureClientId(request, response);
        model.addAttribute("recipeRequest", new RecipeRequest());
        model.addAttribute("hasApiKey", openAiKeyService.hasUserKey(session));
        model.addAttribute("defaultKeyAvailable", openAiKeyService.isDefaultKeyConfigured());
        model.addAttribute("defaultTrialsRemaining", openAiKeyService.getDefaultTrialsRemaining(clientId));
        model.addAttribute("defaultRecipesMax", openAiKeyService.getMaxDefaultRecipesPerSession());
        model.addAttribute("defaultRecipesUsed", openAiKeyService.getDefaultRecipesUsed(clientId));
        model.addAttribute("trialDeviceNotice", TRIAL_DEVICE_NOTICE);
        model.addAttribute("assetVersion", assetVersion);
        return "index";
    }

    @PostMapping("/set-api-key")
    @ResponseBody
    public ResponseEntity<RecipeResponse> setApiKey(@Valid @RequestBody ApiKeyRequest request,
                                                   BindingResult bindingResult,
                                                   HttpSession session,
                                                   HttpServletRequest httpRequest) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest()
                .body(new RecipeResponse(false, null, validationMessage(bindingResult)));
        }
        try {
            if (openAIService.validateApiKey(request.getApiKey())) {
                session.setAttribute(OpenAiKeyService.SESSION_USER_KEY, encryptionService.encrypt(request.getApiKey()));
                securityAuditService.logApiKeySet(ClientIpResolver.resolve(httpRequest));
                return ResponseEntity.ok(new RecipeResponse(true, "API key encrypted and stored successfully!", null));
            }
            return ResponseEntity.badRequest()
                .body(new RecipeResponse(false, null, "Invalid API key. Please check and try again."));
        } catch (Exception e) {
            log.warn("API key validation failed for {}", ClientIpResolver.resolve(httpRequest));
            securityAuditService.logApiKeyValidationFailed(ClientIpResolver.resolve(httpRequest), "validation error");
            return ResponseEntity.internalServerError().body(new RecipeResponse(false, null, GENERIC_ERROR));
        }
    }

    @PostMapping("/clear-api-key")
    @ResponseBody
    public RecipeResponse clearApiKey(HttpSession session) {
        session.removeAttribute(OpenAiKeyService.SESSION_USER_KEY);
        return new RecipeResponse(true, "API key cleared successfully!", null);
    }

    @GetMapping("/api-key-status")
    @ResponseBody
    public RecipeResponse getApiKeyStatus(HttpSession session, HttpServletRequest request, HttpServletResponse response) {
        String clientId = trialClientService.ensureClientId(request, response);
        RecipeResponse apiResponse = new RecipeResponse();
        boolean hasUserKey = openAiKeyService.hasUserKey(session);
        populateTrialFields(apiResponse, clientId);
        apiResponse.setHasUserKey(hasUserKey);
        apiResponse.setTrialDeviceNotice(TRIAL_DEVICE_NOTICE);

        if (hasUserKey) {
            apiResponse.setSuccess(true);
            apiResponse.setContent("Your API key is set. Unlimited recipe generation.");
            apiResponse.setKeySource("user");
            return apiResponse;
        }

        if (openAiKeyService.isDefaultKeyConfigured()) {
            int remaining = openAiKeyService.getDefaultTrialsRemaining(clientId);
            apiResponse.setKeySource("default");
            apiResponse.setSuccess(remaining > 0);
            apiResponse.setContent(remaining > 0
                ? "Free trial: " + remaining + " recipe(s) remaining on this device."
                : null);
            if (remaining == 0) {
                apiResponse.setError("Free trial used on this device. Add your OpenAI API key to continue.");
            }
            return apiResponse;
        }

        apiResponse.setSuccess(false);
        apiResponse.setError("No API key configured. Add your OpenAI API key to generate recipes.");
        return apiResponse;
    }

    @PostMapping("/cancel-generation")
    @ResponseBody
    public ResponseEntity<RecipeResponse> cancelGeneration(HttpServletRequest httpRequest,
                                                           HttpServletResponse httpResponse) {
        String clientId = trialClientService.ensureClientId(httpRequest, httpResponse);
        cancellationService.requestCancel(clientId);
        return ResponseEntity.ok(new RecipeResponse(true, "Cancellation requested.", null));
    }

    @PostMapping("/generate-recipe")
    @ResponseBody
    public ResponseEntity<RecipeResponse> generateRecipe(@Valid @RequestBody RecipeRequest request,
                                                        BindingResult bindingResult,
                                                        HttpSession session,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new RecipeResponse(false, null, "Invalid recipe request."));
        }

        String clientId = trialClientService.ensureClientId(httpRequest, httpResponse);
        OpenAiKeyService.ResolvedKey resolved = openAiKeyService.resolveKey(session, clientId, encryptionService, true);
        if (!resolved.isValid()) {
            return trialDenied(resolved, clientId, session);
        }

        try {
            cancellationService.register(clientId);
            Runnable checkCancelled = () -> cancellationService.throwIfCancelled(clientId);
            StructuredRecipe recipe = openAIService.generateRecipe(
                resolved.getApiKey(), request.getIngredients(), request.getCuisine(),
                request.getDietaryRestrictions(), checkCancelled);
            cancellationService.throwIfCancelled(clientId);
            if (resolved.isIncrementTrialOnSuccess()) {
                openAiKeyService.incrementDefaultRecipeCount(clientId, httpRequest);
            }
            securityAuditService.logRecipeGeneration(ClientIpResolver.resolve(httpRequest), request.getIngredients());
            String recipeJson = openAIService.recipeToJson(recipe);
            String contentHash = ContentHashUtil.sha256(recipeJson);
            Recipe saved = recipeLibraryService.persistGeneratedRecipe(
                clientId, recipe, recipeJson, contentHash,
                request.getIngredients(), request.getCuisine(), request.getDietaryRestrictions()
            );
            RecipeResponse body = buildRecipeSuccess(recipe, resolved, clientId, session);
            body.setRecipeId(saved.getId().toString());
            body.setContentHash(contentHash);
            body.setFavorited(recipeLibraryService.isInSavedCollection(clientId, saved));
            return ResponseEntity.ok(body);
        } catch (GenerationCancelledException e) {
            return ResponseEntity.status(499)
                .body(new RecipeResponse(false, null, "Generation cancelled."));
        } catch (OpenAIClientException e) {
            log.error("Recipe generation OpenAI error for {}", ClientIpResolver.resolve(httpRequest), e);
            if (e.isUnauthorized()) {
                return ResponseEntity.badRequest()
                    .body(new RecipeResponse(false, null, "Invalid OpenAI API key. Check your key in .env or the API Key field."));
            }
            if (e.isBadRequest()) {
                return ResponseEntity.internalServerError()
                    .body(new RecipeResponse(false, null, "Recipe format error. Please try again."));
            }
            return ResponseEntity.badRequest()
                .body(new RecipeResponse(false, null, "OpenAI rejected the request. Check billing or try again."));
        } catch (Exception e) {
            log.error("Recipe generation failed for {}", ClientIpResolver.resolve(httpRequest), e);
            return ResponseEntity.internalServerError().body(new RecipeResponse(false, null, GENERIC_ERROR));
        } finally {
            cancellationService.clear(clientId);
        }
    }

    @PostMapping("/get-cooking-tips")
    @ResponseBody
    public ResponseEntity<RecipeResponse> getCookingTips(@Valid @RequestBody RecipeRequest request,
                                                        BindingResult bindingResult,
                                                        HttpSession session,
                                                        HttpServletRequest httpRequest,
                                                        HttpServletResponse httpResponse) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new RecipeResponse(false, null, "Invalid request."));
        }

        String clientId = trialClientService.ensureClientId(httpRequest, httpResponse);
        OpenAiKeyService.ResolvedKey resolved = openAiKeyService.resolveKey(session, clientId, encryptionService, true);
        if (!resolved.isValid()) {
            return trialDenied(resolved, clientId, session);
        }

        try {
            cancellationService.register(clientId);
            Runnable checkCancelled = () -> cancellationService.throwIfCancelled(clientId);
            CookingTipsResult tips = openAIService.getCookingTips(
                resolved.getApiKey(), request.getIngredients(), checkCancelled);
            cancellationService.throwIfCancelled(clientId);
            if (resolved.isIncrementTrialOnSuccess()) {
                openAiKeyService.incrementDefaultRecipeCount(clientId, httpRequest);
            }
            RecipeResponse body = new RecipeResponse(true, null, null);
            body.setCookingTips(tips);
            body.setKeySource(resolved.getSource().name().toLowerCase());
            populateTrialFields(body, clientId);
            return ResponseEntity.ok(body);
        } catch (GenerationCancelledException e) {
            return ResponseEntity.status(499)
                .body(new RecipeResponse(false, null, "Generation cancelled."));
        } catch (OpenAIClientException e) {
            log.error("Cooking tips OpenAI error for {}", ClientIpResolver.resolve(httpRequest), e);
            if (e.isUnauthorized()) {
                return ResponseEntity.badRequest()
                    .body(new RecipeResponse(false, null, "Invalid OpenAI API key. Check your key in .env or the API Key field."));
            }
            return ResponseEntity.badRequest()
                .body(new RecipeResponse(false, null, "OpenAI rejected the request. Check billing or try again."));
        } catch (Exception e) {
            log.error("Cooking tips failed for {}", ClientIpResolver.resolve(httpRequest), e);
            return ResponseEntity.internalServerError().body(new RecipeResponse(false, null, GENERIC_ERROR));
        } finally {
            cancellationService.clear(clientId);
        }
    }

    private RecipeResponse buildRecipeSuccess(StructuredRecipe recipe, OpenAiKeyService.ResolvedKey resolved,
                                              String clientId, HttpSession session) {
        RecipeResponse body = new RecipeResponse(true, openAIService.recipeToPlainText(recipe), null);
        body.setRecipe(recipe);
        body.setContentHash(ContentHashUtil.sha256(openAIService.recipeToJson(recipe)));
        body.setKeySource(resolved.getSource().name().toLowerCase());
        body.setHasUserKey(openAiKeyService.hasUserKey(session));
        populateTrialFields(body, clientId);
        body.setTrialDeviceNotice(TRIAL_DEVICE_NOTICE);
        return body;
    }

    private ResponseEntity<RecipeResponse> trialDenied(OpenAiKeyService.ResolvedKey resolved, String clientId,
                                                       HttpSession session) {
        int status = openAiKeyService.isDefaultKeyConfigured() && !openAiKeyService.hasUserKey(session)
            && openAiKeyService.getDefaultTrialsRemaining(clientId) == 0 ? 429 : 401;
        RecipeResponse body = new RecipeResponse(false, null, resolved.getErrorMessage());
        populateTrialFields(body, clientId);
        body.setTrialDeviceNotice(TRIAL_DEVICE_NOTICE);
        return ResponseEntity.status(status).body(body);
    }

    private void populateTrialFields(RecipeResponse response, String clientId) {
        response.setDefaultKeyAvailable(openAiKeyService.isDefaultKeyConfigured());
        response.setDefaultTrialsRemaining(openAiKeyService.getDefaultTrialsRemaining(clientId));
        response.setDefaultRecipesMax(openAiKeyService.getMaxDefaultRecipesPerSession());
        response.setDefaultRecipesUsed(openAiKeyService.getDefaultRecipesUsed(clientId));
    }

    private String validationMessage(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .findFirst()
            .orElse("Invalid request");
    }
}
