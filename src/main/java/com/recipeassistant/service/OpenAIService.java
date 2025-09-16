package com.recipeassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OpenAIService {

    @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String testApiKey(String apiKey) {
        try {
            String testPrompt = "Hello, this is a test message. Please respond with 'API key is valid' if you can see this.";
            return callOpenAI(apiKey, testPrompt);
        } catch (Exception e) {
            return null;
        }
    }

    public String generateRecipe(String apiKey, String ingredients, String cuisine, String dietaryRestrictions) {
        String prompt = buildRecipePrompt(ingredients, cuisine, dietaryRestrictions);
        return callOpenAI(apiKey, prompt);
    }

    public String getCookingTips(String apiKey, String ingredients) {
        String prompt = buildCookingTipsPrompt(ingredients);
        return callOpenAI(apiKey, prompt);
    }

    private String buildRecipePrompt(String ingredients, String cuisine, String dietaryRestrictions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a professional chef and recipe creator. ");
        prompt.append("Create a detailed, step-by-step recipe using the following ingredients: ").append(ingredients).append(". ");
        
        if (cuisine != null && !cuisine.trim().isEmpty()) {
            prompt.append("The recipe should be in the ").append(cuisine).append(" cuisine style. ");
        }
        
        if (dietaryRestrictions != null && !dietaryRestrictions.trim().isEmpty()) {
            prompt.append("Please ensure the recipe is suitable for: ").append(dietaryRestrictions).append(". ");
        }
        
        prompt.append("Include the following sections:\n");
        prompt.append("1. Recipe Name\n");
        prompt.append("2. Preparation Time\n");
        prompt.append("3. Cooking Time\n");
        prompt.append("4. Servings\n");
        prompt.append("5. Ingredients (with quantities)\n");
        prompt.append("6. Instructions (numbered steps)\n");
        prompt.append("7. Cooking Tips\n");
        prompt.append("8. Nutritional Information (if applicable)\n");
        prompt.append("\nFormat the response in a clear, easy-to-read manner with proper sections and formatting.");
        
        return prompt.toString();
    }

    private String buildCookingTipsPrompt(String ingredients) {
        return "You are a professional chef. Provide helpful cooking tips and techniques for working with these ingredients: " + 
               ingredients + ". Include tips on preparation, cooking methods, flavor combinations, and any special considerations. " +
               "Format the response in a clear, bullet-pointed list.";
    }

    private String callOpenAI(String apiKey, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");
            
            Map<String, String> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);
            
            requestBody.put("messages", new Object[]{message});
            requestBody.put("max_tokens", 1500);
            requestBody.put("temperature", 0.7);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            JsonNode jsonResponse = objectMapper.readTree(response.getBody());
            return jsonResponse.get("choices").get(0).get("message").get("content").asText();

        } catch (Exception e) {
            throw new RuntimeException("Error calling OpenAI API: " + e.getMessage(), e);
        }
    }
}
