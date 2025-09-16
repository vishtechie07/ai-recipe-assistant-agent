package com.recipeassistant.model;

public class RecipeResponse {
    private boolean success;
    private String content;
    private String error;

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
}
