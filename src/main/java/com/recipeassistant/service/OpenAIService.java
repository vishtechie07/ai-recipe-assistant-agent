package com.recipeassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recipeassistant.model.CookingTipsResult;
import com.recipeassistant.model.StructuredRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
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

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${openai.api.models-url:https://api.openai.com/v1/models}")
    private String modelsUrl;

    @Value("${openai.api.model.recipe:gpt-4o-mini}")
    private String recipeModel;

    @Value("${openai.api.model.tips:gpt-4o-mini}")
    private String tipsModel;

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
        String json = callOpenAI(apiKey, recipeModel, prompt, RECIPE_JSON_SCHEMA, cancellationCheck);
        return parseJson(json, StructuredRecipe.class);
    }

    public CookingTipsResult getCookingTips(String apiKey, String ingredients, Runnable cancellationCheck) {
        String prompt = buildCookingTipsPrompt(ingredients);
        String json = callOpenAI(apiKey, tipsModel, prompt, TIPS_JSON_SCHEMA, cancellationCheck);
        return parseJson(json, CookingTipsResult.class);
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

    private <T> T parseJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse OpenAI JSON response", e);
        }
    }

    private String callOpenAI(String apiKey, String model, String prompt, String jsonSchema,
                              Runnable cancellationCheck) {
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
            requestBody.put("max_tokens", 2000);
            requestBody.put("temperature", 0.7);
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
