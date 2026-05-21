package com.recipeassistant.model;

public class RecipeResponse {
    private boolean success;
    private String content;
    private String error;
    private Boolean hasUserKey;
    private Boolean defaultKeyAvailable;
    private Integer defaultTrialsRemaining;
    private Integer defaultRecipesMax;
    private Integer defaultRecipesUsed;
    private String keySource;

    public RecipeResponse() {}

    public RecipeResponse(boolean success, String content, String error) {
        this.success = success;
        this.content = content;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Boolean getHasUserKey() {
        return hasUserKey;
    }

    public void setHasUserKey(Boolean hasUserKey) {
        this.hasUserKey = hasUserKey;
    }

    public Boolean getDefaultKeyAvailable() {
        return defaultKeyAvailable;
    }

    public void setDefaultKeyAvailable(Boolean defaultKeyAvailable) {
        this.defaultKeyAvailable = defaultKeyAvailable;
    }

    public Integer getDefaultTrialsRemaining() {
        return defaultTrialsRemaining;
    }

    public void setDefaultTrialsRemaining(Integer defaultTrialsRemaining) {
        this.defaultTrialsRemaining = defaultTrialsRemaining;
    }

    public String getKeySource() {
        return keySource;
    }

    public void setKeySource(String keySource) {
        this.keySource = keySource;
    }

    public Integer getDefaultRecipesMax() {
        return defaultRecipesMax;
    }

    public void setDefaultRecipesMax(Integer defaultRecipesMax) {
        this.defaultRecipesMax = defaultRecipesMax;
    }

    public Integer getDefaultRecipesUsed() {
        return defaultRecipesUsed;
    }

    public void setDefaultRecipesUsed(Integer defaultRecipesUsed) {
        this.defaultRecipesUsed = defaultRecipesUsed;
    }
}
