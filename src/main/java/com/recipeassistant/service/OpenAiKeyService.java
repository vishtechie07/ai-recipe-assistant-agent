package com.recipeassistant.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpenAiKeyService {

    public static final String SESSION_USER_KEY = "encrypted_openai_api_key";
    public static final String SESSION_DEFAULT_RECIPE_COUNT = "default_recipe_count";

    @Value("${openai.api.default-key:}")
    private String defaultApiKey;

    @Value("${app.default-key.max-recipes-per-session:5}")
    private int maxDefaultRecipesPerSession;

    public boolean isDefaultKeyConfigured() {
        return defaultApiKey != null && !defaultApiKey.isBlank();
    }

    public boolean hasUserKey(HttpSession session) {
        return session.getAttribute(SESSION_USER_KEY) != null;
    }

    public int getDefaultRecipeCount(HttpSession session) {
        Integer count = (Integer) session.getAttribute(SESSION_DEFAULT_RECIPE_COUNT);
        return count == null ? 0 : count;
    }

    public int getMaxDefaultRecipesPerSession() {
        return maxDefaultRecipesPerSession;
    }

    public int getDefaultTrialsRemaining(HttpSession session) {
        if (!isDefaultKeyConfigured()) {
            return 0;
        }
        return Math.max(0, maxDefaultRecipesPerSession - getDefaultRecipeCount(session));
    }

    public int getDefaultRecipesUsed(HttpSession session) {
        return getDefaultRecipeCount(session);
    }

    public void incrementDefaultRecipeCount(HttpSession session) {
        session.setAttribute(SESSION_DEFAULT_RECIPE_COUNT, getDefaultRecipeCount(session) + 1);
    }

    public ResolvedKey resolveKey(HttpSession session, EncryptionService encryptionService, boolean countAgainstTrial) {
        String encryptedUserKey = (String) session.getAttribute(SESSION_USER_KEY);
        if (encryptedUserKey != null) {
            String userKey = encryptionService.decrypt(encryptedUserKey);
            if (userKey != null) {
                return ResolvedKey.ok(userKey, KeySource.USER, false);
            }
            return ResolvedKey.error("Error decrypting API key. Please set it again.");
        }

        if (!isDefaultKeyConfigured()) {
            return ResolvedKey.error("Please set your OpenAI API key first.");
        }

        if (getDefaultRecipeCount(session) >= maxDefaultRecipesPerSession) {
            return ResolvedKey.error(
                "Free trial limit reached (" + maxDefaultRecipesPerSession + " recipes per session). "
                    + "Add your own OpenAI API key to continue.");
        }

        return ResolvedKey.ok(defaultApiKey.trim(), KeySource.DEFAULT, countAgainstTrial);
    }

    public enum KeySource {
        USER,
        DEFAULT
    }

    public static final class ResolvedKey {
        private final boolean valid;
        private final String apiKey;
        private final KeySource source;
        private final boolean incrementTrialOnSuccess;
        private final String errorMessage;

        private ResolvedKey(boolean valid, String apiKey, KeySource source, boolean incrementTrialOnSuccess, String errorMessage) {
            this.valid = valid;
            this.apiKey = apiKey;
            this.source = source;
            this.incrementTrialOnSuccess = incrementTrialOnSuccess;
            this.errorMessage = errorMessage;
        }

        public static ResolvedKey ok(String apiKey, KeySource source, boolean incrementTrialOnSuccess) {
            return new ResolvedKey(true, apiKey, source, incrementTrialOnSuccess, null);
        }

        public static ResolvedKey error(String errorMessage) {
            return new ResolvedKey(false, null, null, false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getApiKey() {
            return apiKey;
        }

        public KeySource getSource() {
            return source;
        }

        public boolean isIncrementTrialOnSuccess() {
            return incrementTrialOnSuccess;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
