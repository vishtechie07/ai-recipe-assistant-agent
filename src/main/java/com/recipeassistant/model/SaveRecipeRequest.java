package com.recipeassistant.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SaveRecipeRequest {

    @NotBlank(message = "Recipe content is required")
    @Size(max = 50000, message = "Recipe content is too long")
    private String content;

    @Size(max = 1000, message = "Ingredients description too long")
    private String ingredients;

    @Size(max = 100, message = "Cuisine name too long")
    private String cuisine;

    @Size(max = 500, message = "Dietary restrictions too long")
    private String dietaryRestrictions;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getIngredients() {
        return ingredients;
    }

    public void setIngredients(String ingredients) {
        this.ingredients = ingredients;
    }

    public String getCuisine() {
        return cuisine;
    }

    public void setCuisine(String cuisine) {
        this.cuisine = cuisine;
    }

    public String getDietaryRestrictions() {
        return dietaryRestrictions;
    }

    public void setDietaryRestrictions(String dietaryRestrictions) {
        this.dietaryRestrictions = dietaryRestrictions;
    }
}
