package com.recipeassistant.service;

import com.recipeassistant.model.IngredientValidationResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IngredientValidator {

    private final OpenAIService openAIService;

    public IngredientValidator(OpenAIService openAIService) {
        this.openAIService = openAIService;
    }

    public IngredientValidationResult validate(String apiKey, String raw, Runnable cancellationCheck) {
        if (raw == null || raw.isBlank()) {
            return IngredientValidationResult.fail(List.of(), "Please enter at least one food ingredient.");
        }
        return openAIService.validateIngredients(apiKey, raw.trim(), cancellationCheck);
    }
}
