package com.recipeassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class RecipeAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecipeAssistantApplication.class, args);
    }
}
