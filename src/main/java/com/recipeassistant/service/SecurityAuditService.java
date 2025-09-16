package com.recipeassistant.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class SecurityAuditService {
    
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public void logApiKeySet(String clientIp) {
        logSecurityEvent("API_KEY_SET", clientIp, "API key successfully set");
    }
    
    public void logApiKeyValidationFailed(String clientIp, String reason) {
        logSecurityEvent("API_KEY_VALIDATION_FAILED", clientIp, "API key validation failed: " + reason);
    }
    
    public void logInvalidInput(String clientIp, String endpoint, String details) {
        logSecurityEvent("INVALID_INPUT", clientIp, "Invalid input at " + endpoint + ": " + details);
    }
    
    public void logRecipeGeneration(String clientIp, String ingredients) {
        logSecurityEvent("RECIPE_GENERATION", clientIp, "Recipe generated for ingredients: " + sanitizeInput(ingredients));
    }
    
    private void logSecurityEvent(String eventType, String clientIp, String details) {
        String timestamp = LocalDateTime.now().format(formatter);
        String logMessage = String.format("[SECURITY] %s | %s | %s | %s", 
            timestamp, eventType, clientIp, details);
        
        System.out.println(logMessage);
        // In production, this should be logged to a proper logging system
    }
    
    private String sanitizeInput(String input) {
        if (input == null) return "null";
        // Truncate long inputs to prevent log flooding
        return input.length() > 100 ? input.substring(0, 100) + "..." : input;
    }
}
