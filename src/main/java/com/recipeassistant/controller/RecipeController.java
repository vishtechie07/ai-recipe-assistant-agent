package com.recipeassistant.controller;

import com.recipeassistant.model.RecipeRequest;
import com.recipeassistant.model.RecipeResponse;
import com.recipeassistant.service.OpenAIService;
import com.recipeassistant.service.OpenAiKeyService;
import com.recipeassistant.service.SecurityAuditService;
import com.recipeassistant.service.EncryptionService;
import com.recipeassistant.service.DatabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.UUID;

@Controller
public class RecipeController {

    @Autowired
    private OpenAIService openAIService;
    
    @Autowired
    private SecurityAuditService securityAuditService;
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private OpenAiKeyService openAiKeyService;

    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        model.addAttribute("recipeRequest", new RecipeRequest());
        model.addAttribute("hasApiKey", openAiKeyService.hasUserKey(session));
        model.addAttribute("defaultKeyAvailable", openAiKeyService.isDefaultKeyConfigured());
        model.addAttribute("defaultTrialsRemaining", openAiKeyService.getDefaultTrialsRemaining(session));
        model.addAttribute("defaultRecipesMax", openAiKeyService.getMaxDefaultRecipesPerSession());
        model.addAttribute("defaultRecipesUsed", openAiKeyService.getDefaultRecipesUsed(session));
        return "index";
    }

    @PostMapping("/set-api-key")
    @ResponseBody
    public ResponseEntity<RecipeResponse> setApiKey(@RequestBody ApiKeyRequest request, 
                                                   HttpSession session, 
                                                   HttpServletRequest httpRequest) {
        try {
            // Validate API key format
            if (request.getApiKey() == null || !request.getApiKey().startsWith("sk-")) {
                securityAuditService.logApiKeyValidationFailed(getClientIpAddress(httpRequest), "Invalid format");
                return ResponseEntity.badRequest()
                    .body(new RecipeResponse(false, null, "Invalid API key format. Must start with 'sk-'"));
            }
            
            // Validate the API key by making a simple test call
            String testResult = openAIService.testApiKey(request.getApiKey());
            if (testResult != null) {
                // Encrypt and store API key securely
                String encryptedApiKey = encryptionService.encrypt(request.getApiKey());
                session.setAttribute(OpenAiKeyService.SESSION_USER_KEY, encryptedApiKey);
                session.setAttribute("api_key_set_time", System.currentTimeMillis());
                
                // Log successful API key setup (without exposing the key)
                securityAuditService.logApiKeySet(getClientIpAddress(httpRequest));
                
                return ResponseEntity.ok(new RecipeResponse(true, "API key encrypted and stored successfully!", null));
            } else {
                return ResponseEntity.badRequest()
                    .body(new RecipeResponse(false, null, "Invalid API key. Please check and try again."));
            }
        } catch (Exception e) {
            securityAuditService.logApiKeyValidationFailed(getClientIpAddress(httpRequest), e.getMessage());
            return ResponseEntity.internalServerError()
                .body(new RecipeResponse(false, null, "Error validating API key: " + e.getMessage()));
        }
    }

    @PostMapping("/clear-api-key")
    @ResponseBody
    public RecipeResponse clearApiKey(HttpSession session) {
        session.removeAttribute(OpenAiKeyService.SESSION_USER_KEY);
        session.removeAttribute("api_key_set_time");
        return new RecipeResponse(true, "API key cleared successfully!", null);
    }

    @GetMapping("/api-key-status")
    @ResponseBody
    public RecipeResponse getApiKeyStatus(HttpSession session) {
        RecipeResponse response = new RecipeResponse();
        boolean hasUserKey = openAiKeyService.hasUserKey(session);
        populateTrialFields(response, session);
        response.setHasUserKey(hasUserKey);

        if (hasUserKey) {
            response.setSuccess(true);
            response.setContent("Your API key is set. Unlimited recipe generation.");
            response.setKeySource("user");
            return response;
        }

        if (openAiKeyService.isDefaultKeyConfigured()) {
            int remaining = openAiKeyService.getDefaultTrialsRemaining(session);
            response.setKeySource("default");
            if (remaining > 0) {
                response.setSuccess(true);
                response.setContent("Free trial: " + remaining + " recipe(s) remaining. Add your key for unlimited use.");
            } else {
                response.setSuccess(false);
                response.setError("Free trial used up for this session. Add your OpenAI API key to continue.");
            }
            return response;
        }

        response.setSuccess(false);
        response.setError("No API key configured. Add your OpenAI API key to generate recipes.");
        return response;
    }

    @PostMapping("/generate-recipe")
    @ResponseBody
    public ResponseEntity<RecipeResponse> generateRecipe(@Valid @RequestBody RecipeRequest request, 
                                                        BindingResult bindingResult,
                                                        HttpSession session,
                                                        HttpServletRequest httpRequest) {
        // Check for validation errors
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");
            securityAuditService.logInvalidInput(getClientIpAddress(httpRequest), "/generate-recipe", errorMessage);
            return ResponseEntity.badRequest()
                .body(new RecipeResponse(false, null, errorMessage));
        }
        
        OpenAiKeyService.ResolvedKey resolved = openAiKeyService.resolveKey(session, encryptionService, true);
        if (!resolved.isValid()) {
            int status = openAiKeyService.isDefaultKeyConfigured() && !openAiKeyService.hasUserKey(session)
                && openAiKeyService.getDefaultTrialsRemaining(session) == 0 ? 429 : 401;
            RecipeResponse body = new RecipeResponse(false, null, resolved.getErrorMessage());
            populateTrialFields(body, session);
            return ResponseEntity.status(status).body(body);
        }

        try {
            String recipe = openAIService.generateRecipe(
                resolved.getApiKey(), request.getIngredients(), request.getCuisine(), request.getDietaryRestrictions());
            if (resolved.isIncrementTrialOnSuccess()) {
                openAiKeyService.incrementDefaultRecipeCount(session);
            }
            securityAuditService.logRecipeGeneration(getClientIpAddress(httpRequest), request.getIngredients());
            RecipeResponse body = new RecipeResponse(true, recipe, null);
            body.setKeySource(resolved.getSource().name().toLowerCase());
            body.setHasUserKey(openAiKeyService.hasUserKey(session));
            populateTrialFields(body, session);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new RecipeResponse(false, null, "Error generating recipe: " + e.getMessage()));
        }
    }

    @PostMapping("/get-cooking-tips")
    @ResponseBody
    public ResponseEntity<RecipeResponse> getCookingTips(@Valid @RequestBody RecipeRequest request, 
                                                        BindingResult bindingResult,
                                                        HttpSession session,
                                                        HttpServletRequest httpRequest) {
        // Check for validation errors
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");
            return ResponseEntity.badRequest()
                .body(new RecipeResponse(false, null, errorMessage));
        }
        
        OpenAiKeyService.ResolvedKey resolved = openAiKeyService.resolveKey(session, encryptionService, false);
        if (!resolved.isValid()) {
            int status = openAiKeyService.isDefaultKeyConfigured() && !openAiKeyService.hasUserKey(session)
                && openAiKeyService.getDefaultTrialsRemaining(session) == 0 ? 429 : 401;
            return ResponseEntity.status(status)
                .body(new RecipeResponse(false, null, resolved.getErrorMessage()));
        }

        try {
            String tips = openAIService.getCookingTips(resolved.getApiKey(), request.getIngredients());
            return ResponseEntity.ok(new RecipeResponse(true, tips, null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new RecipeResponse(false, null, "Error getting cooking tips: " + e.getMessage()));
        }
    }

    // Inner class for API key requests
    public static class ApiKeyRequest {
        private String apiKey;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
    
    private void populateTrialFields(RecipeResponse response, HttpSession session) {
        response.setDefaultKeyAvailable(openAiKeyService.isDefaultKeyConfigured());
        response.setDefaultTrialsRemaining(openAiKeyService.getDefaultTrialsRemaining(session));
        response.setDefaultRecipesMax(openAiKeyService.getMaxDefaultRecipesPerSession());
        response.setDefaultRecipesUsed(openAiKeyService.getDefaultRecipesUsed(session));
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
    
    // Database API Endpoints
    
    @PostMapping("/save-recipe")
    @ResponseBody
    public ResponseEntity<RecipeResponse> saveRecipe(@RequestBody SaveRecipeRequest request, 
                                                   HttpSession session,
                                                   HttpServletRequest httpRequest) {
        try {
            // For now, use a default user email (can be enhanced with user authentication later)
            String userEmail = "default@recipeassistant.com";
            
            // Extract recipe title from the content (simple extraction)
            String title = extractRecipeTitle(request.getContent());
            
            // Save recipe to database
            databaseService.saveRecipe(
                title,
                request.getContent(),
                request.getIngredients(),
                request.getCuisine(),
                request.getDietaryRestrictions(),
                userEmail
            );
            
            securityAuditService.logRecipeGeneration(getClientIpAddress(httpRequest), request.getIngredients());
            return ResponseEntity.ok(new RecipeResponse(true, "Recipe saved to database successfully!", null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new RecipeResponse(false, null, "Error saving recipe: " + e.getMessage()));
        }
    }
    
    @GetMapping("/get-user-recipes")
    @ResponseBody
    public ResponseEntity<RecipeResponse> getUserRecipes(HttpSession session) {
        try {
            String userEmail = "default@recipeassistant.com"; // Default user for now
            var recipes = databaseService.getUserRecipes(userEmail);
            
            // Convert recipes to a simple format for frontend
            StringBuilder recipesList = new StringBuilder();
            for (var recipe : recipes) {
                recipesList.append("Recipe: ").append(recipe.getTitle()).append("\n");
                recipesList.append("Cuisine: ").append(recipe.getCuisine()).append("\n");
                recipesList.append("Created: ").append(recipe.getCreatedAt()).append("\n\n");
            }
            
            return ResponseEntity.ok(new RecipeResponse(true, recipesList.toString(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new RecipeResponse(false, null, "Error retrieving recipes: " + e.getMessage()));
        }
    }
    
    @GetMapping("/database-status")
    @ResponseBody
    public ResponseEntity<RecipeResponse> getDatabaseStatus() {
        try {
            // Simple database connectivity test
            String userEmail = "default@recipeassistant.com";
            var stats = databaseService.getUserStats(userEmail);
            
            return ResponseEntity.ok(new RecipeResponse(true, 
                "Database connected successfully. Total recipes: " + stats.get("totalRecipes") + 
                ", Favorites: " + stats.get("favoriteRecipes"), null));
        } catch (Exception e) {
            return ResponseEntity.status(503)
                .body(new RecipeResponse(false, null, "Database connection failed: " + e.getMessage()));
        }
    }
    
    @GetMapping("/get-user-favorites")
    @ResponseBody
    public ResponseEntity<RecipeResponse> getUserFavorites(HttpSession session) {
        try {
            String userEmail = "default@recipeassistant.com"; // Default user for now
            var favorites = databaseService.getUserFavorites(userEmail);
            
            // Convert favorites to a simple format for frontend
            StringBuilder favoritesList = new StringBuilder();
            for (var recipe : favorites) {
                favoritesList.append("Favorite: ").append(recipe.getTitle()).append("\n");
                favoritesList.append("Cuisine: ").append(recipe.getCuisine()).append("\n");
                favoritesList.append("Created: ").append(recipe.getCreatedAt()).append("\n\n");
            }
            
            return ResponseEntity.ok(new RecipeResponse(true, favoritesList.toString(), null));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new RecipeResponse(false, null, "Error retrieving favorites: " + e.getMessage()));
        }
    }
    
    @PostMapping("/add-to-favorites")
    @ResponseBody
    public ResponseEntity<RecipeResponse> addToFavorites(@RequestBody FavoriteRequest request, 
                                                       HttpSession session) {
        try {
            String userEmail = "default@recipeassistant.com"; // Default user for now
            UUID recipeId = UUID.fromString(request.getRecipeId());
            boolean success = databaseService.addToFavorites(recipeId, userEmail);
            
            if (success) {
                return ResponseEntity.ok(new RecipeResponse(true, "Recipe added to favorites!", null));
            } else {
                return ResponseEntity.badRequest()
                    .body(new RecipeResponse(false, null, "Recipe not found or already favorited"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new RecipeResponse(false, null, "Error adding to favorites: " + e.getMessage()));
        }
    }
    
    @PostMapping("/remove-from-favorites")
    @ResponseBody
    public ResponseEntity<RecipeResponse> removeFromFavorites(@RequestBody FavoriteRequest request, 
                                                            HttpSession session) {
        try {
            String userEmail = "default@recipeassistant.com"; // Default user for now
            UUID recipeId = UUID.fromString(request.getRecipeId());
            boolean success = databaseService.removeFromFavorites(recipeId, userEmail);
            
            if (success) {
                return ResponseEntity.ok(new RecipeResponse(true, "Recipe removed from favorites!", null));
            } else {
                return ResponseEntity.badRequest()
                    .body(new RecipeResponse(false, null, "Recipe not found in favorites"));
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(new RecipeResponse(false, null, "Error removing from favorites: " + e.getMessage()));
        }
    }
    
    // Helper method to extract recipe title
    private String extractRecipeTitle(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "Untitled Recipe";
        }
        
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && 
                !line.toLowerCase().contains("ingredients") && 
                !line.toLowerCase().contains("instructions") && 
                !line.toLowerCase().contains("prep time") && 
                !line.toLowerCase().contains("cook time") && 
                !line.toLowerCase().contains("servings")) {
                return line.length() > 50 ? line.substring(0, 50) + "..." : line;
            }
        }
        return "Untitled Recipe";
    }
    
    // Inner classes for new requests
    public static class SaveRecipeRequest {
        private String content;
        private String ingredients;
        private String cuisine;
        private String dietaryRestrictions;
        
        // Getters and setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getIngredients() { return ingredients; }
        public void setIngredients(String ingredients) { this.ingredients = ingredients; }
        public String getCuisine() { return cuisine; }
        public void setCuisine(String cuisine) { this.cuisine = cuisine; }
        public String getDietaryRestrictions() { return dietaryRestrictions; }
        public void setDietaryRestrictions(String dietaryRestrictions) { this.dietaryRestrictions = dietaryRestrictions; }
    }
    
    public static class FavoriteRequest {
        private String recipeId;
        
        public String getRecipeId() { return recipeId; }
        public void setRecipeId(String recipeId) { this.recipeId = recipeId; }
    }
}
