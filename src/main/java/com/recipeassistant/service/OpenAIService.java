package com.recipeassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipeassistant.model.CookingTipsResult;
import com.recipeassistant.model.IngredientValidationLLMResponse;
import com.recipeassistant.model.IngredientValidationResult;
import com.recipeassistant.model.StructuredRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAIService {

    private static final Logger log = LoggerFactory.getLogger(OpenAIService.class);

    private static final String RECIPE_JSON_SCHEMA = """
        {
          "name": "recipe",
          "strict": true,
          "schema": {
            "type": "object",
            "additionalProperties": false,
            "required": ["name","preparationTime","cookingTime","servings","ingredients","instructions","tips","nutrition"],
            "properties": {
              "name": { "type": "string" },
              "preparationTime": { "type": "string" },
              "cookingTime": { "type": "string" },
              "servings": { "type": "string" },
              "ingredients": { "type": "array", "items": { "type": "string" } },
              "instructions": { "type": "array", "items": { "type": "string" } },
              "tips": { "type": "array", "items": { "type": "string" } },
              "nutrition": {
                "type": "object",
                "additionalProperties": false,
                "required": ["calories", "protein", "carbs", "fat"],
                "properties": {
                  "calories": { "type": "string" },
                  "protein": { "type": "string" },
                  "carbs": { "type": "string" },
                  "fat": { "type": "string" }
                }
              }
            }
          }
        }
        """;

    private static final String TIPS_JSON_SCHEMA = """
        {
          "name": "cooking_tips",
          "strict": true,
          "schema": {
            "type": "object",
            "additionalProperties": false,
            "required": ["tips"],
            "properties": {
              "tips": { "type": "array", "items": { "type": "string" } }
            }
          }
        }
        """;

    private static final String VALIDATION_JSON_SCHEMA = """
        {
          "name": "ingredient_validation",
          "strict": true,
          "schema": {
            "type": "object",
            "additionalProperties": false,
            "required": ["valid", "invalidItems", "message"],
            "properties": {
              "valid": { "type": "boolean" },
              "invalidItems": { "type": "array", "items": { "type": "string" } },
              "message": { "type": "string" }
            }
          }
        }
        """;

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${openai.api.models-url:https://api.openai.com/v1/models}")
    private String modelsUrl;

    @Value("${openai.api.model.recipe:gpt-4o-mini}")
    private String recipeModel;

    @Value("${openai.api.model.tips:gpt-4o-mini}")
    private String tipsModel;

    @Value("${openai.api.model.validation:gpt-4o-mini}")
    private String validationModel;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAIService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean validateApiKey(String apiKey) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            ResponseEntity<String> response = restTemplate.exchange(
                modelsUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("OpenAI key validation failed: status={}", e.getStatusCode());
            return false;
        } catch (Exception e) {
            log.warn("OpenAI key validation failed");
            return false;
        }
    }

    public StructuredRecipe generateRecipe(String apiKey, String ingredients, String cuisine,
                                             String dietaryRestrictions, Runnable cancellationCheck) {
        String prompt = buildRecipePrompt(ingredients, cuisine, dietaryRestrictions);
        String json = callOpenAI(apiKey, recipeModel, prompt, RECIPE_JSON_SCHEMA, cancellationCheck, 2000, 0.7);
        return parseJson(json, StructuredRecipe.class);
    }

    public CookingTipsResult getCookingTips(String apiKey, String ingredients, Runnable cancellationCheck) {
        String prompt = buildCookingTipsPrompt(ingredients);
        String json = callOpenAI(apiKey, tipsModel, prompt, TIPS_JSON_SCHEMA, cancellationCheck, 2000, 0.7);
        return parseJson(json, CookingTipsResult.class);
    }

    public IngredientValidationResult validateIngredients(String apiKey, String ingredients,
                                                          Runnable cancellationCheck) {
        String prompt = buildIngredientValidationPrompt(ingredients);
        String json = callOpenAI(apiKey, validationModel, prompt, VALIDATION_JSON_SCHEMA,
            cancellationCheck, 256, 0.1);
        IngredientValidationLLMResponse parsed = parseJson(json, IngredientValidationLLMResponse.class);
        if (parsed.valid()) {
            return IngredientValidationResult.ok();
        }
        List<String> invalid = parsed.invalidItems() != null ? parsed.invalidItems() : List.of();
        return IngredientValidationResult.fail(invalid, parsed.message());
    }

    public String recipeToPlainText(StructuredRecipe recipe) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(recipe);
        } catch (Exception e) {
            return recipe.getName();
        }
    }

    public String recipeToJson(StructuredRecipe recipe) {
        try {
            return objectMapper.writeValueAsString(recipe);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize recipe", e);
        }
    }

    private String buildRecipePrompt(String ingredients, String cuisine, String dietaryRestrictions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a detailed home-cooking recipe using: ").append(ingredients).append(". ");
        if (cuisine != null && !cuisine.isBlank()) {
            prompt.append("Cuisine style: ").append(cuisine).append(". ");
        }
        if (dietaryRestrictions != null && !dietaryRestrictions.isBlank()) {
            prompt.append("Dietary requirements: ").append(dietaryRestrictions).append(". ");
        }
        prompt.append("Return JSON only matching the schema. Use realistic times and clear step-by-step instructions.");
        return prompt.toString();
    }

    private String buildCookingTipsPrompt(String ingredients) {
        return "Provide 5-8 practical cooking tips for these ingredients: " + ingredients
            + ". Return JSON only matching the schema.";
    }

    private String buildIngredientValidationPrompt(String ingredients) {
        return """
            You validate recipe ingredient lists. Input is a comma-separated list the user wants to cook with.

            Rules:
            - Accept real food: produce, meat, seafood, dairy, grains, legumes, spices, herbs, oils, sauces, regional staples, and obvious typos that clearly mean food (e.g. chiken -> chicken).
            - Reject non-food objects (car, temple, phone, laptop), places, abstract words, URLs, numbers-only tokens, and nonsense.
            - Do NOT creatively reinterpret invalid words as food (never map "car" to carrot or "temple" to a dish theme).
            - If ANY item is invalid, set valid=false and list every invalid token exactly as the user typed it in invalidItems.
            - message: one short, friendly sentence for the user explaining the problem.

            Ingredients to validate: %s
            """.formatted(ingredients);
    }

    private <T> T parseJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI JSON response", e);
        }
    }

    private String callOpenAI(String apiKey, String model, String prompt, String jsonSchema,
                              Runnable cancellationCheck, int maxTokens, double temperature) {
        if (cancellationCheck != null) {
            cancellationCheck.run();
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", new Object[]{Map.of("role", "user", "content", prompt)});
            requestBody.put("max_tokens", maxTokens);
            requestBody.put("temperature", temperature);
            if (jsonSchema != null) {
                requestBody.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", objectMapper.readTree(jsonSchema)
                ));
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            if (cancellationCheck != null) {
                cancellationCheck.run();
            }
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new OpenAIClientException(response.getStatusCode());
            }
            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            return jsonResponse.get("choices").get(0).get("message").get("content").asText();
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("OpenAI API error: status={}", e.getStatusCode());
            throw new OpenAIClientException(e.getStatusCode());
        } catch (OpenAIClientException e) {
            throw e;
        } catch (GenerationCancelledException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI request failed", e);
            throw new RuntimeException("OpenAI request failed", e);
        }
    }
}
