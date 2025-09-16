package com.recipeassistant.service;

import com.recipeassistant.model.Recipe;
import com.recipeassistant.model.User;
import com.recipeassistant.model.UserFavorite;
import com.recipeassistant.repository.RecipeRepository;
import com.recipeassistant.repository.UserFavoriteRepository;
import com.recipeassistant.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class DatabaseService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RecipeRepository recipeRepository;
    
    @Autowired
    private UserFavoriteRepository userFavoriteRepository;
    
    /**
     * Get or create user by email
     */
    public User getOrCreateUser(String email) {
        return userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = new User(email);
                    newUser.setLastLogin(LocalDateTime.now());
                    return userRepository.save(newUser);
                });
    }
    
    /**
     * Save recipe to database
     */
    public Recipe saveRecipe(String title, String instructions, String ingredients, 
                           String cuisine, String dietaryRestrictions, String userEmail) {
        User user = getOrCreateUser(userEmail);
        
        Recipe recipe = new Recipe(title, instructions, user);
        recipe.setCuisine(cuisine);
        
        // Set ingredients directly
        if (ingredients != null && !ingredients.trim().isEmpty()) {
            recipe.setIngredients(ingredients);
        }
        
        // Set dietary restrictions directly
        if (dietaryRestrictions != null && !dietaryRestrictions.trim().isEmpty()) {
            recipe.setDietaryRestrictions(dietaryRestrictions);
        }
        
        return recipeRepository.save(recipe);
    }
    
    /**
     * Get user's recipes
     */
    public List<Recipe> getUserRecipes(String userEmail) {
        User user = getOrCreateUser(userEmail);
        return recipeRepository.findByUserOrderByCreatedAtDesc(user);
    }
    
    /**
     * Get user's favorite recipes
     */
    public List<Recipe> getUserFavorites(String userEmail) {
        User user = getOrCreateUser(userEmail);
        return recipeRepository.findByUserAndIsFavoriteTrueOrderByCreatedAtDesc(user);
    }
    
    /**
     * Add recipe to favorites
     */
    public boolean addToFavorites(UUID recipeId, String userEmail) {
        User user = getOrCreateUser(userEmail);
        Optional<Recipe> recipeOpt = recipeRepository.findById(recipeId);
        
        if (recipeOpt.isPresent()) {
            Recipe recipe = recipeOpt.get();
            
            // Check if already favorited
            if (!userFavoriteRepository.existsByUserAndRecipe(user, recipe)) {
                UserFavorite favorite = new UserFavorite(user, recipe);
                userFavoriteRepository.save(favorite);
                recipe.setIsFavorite(true);
                recipeRepository.save(recipe);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Remove recipe from favorites
     */
    public boolean removeFromFavorites(UUID recipeId, String userEmail) {
        User user = getOrCreateUser(userEmail);
        Optional<Recipe> recipeOpt = recipeRepository.findById(recipeId);
        
        if (recipeOpt.isPresent()) {
            Recipe recipe = recipeOpt.get();
            userFavoriteRepository.deleteByUserAndRecipe(user, recipe);
            recipe.setIsFavorite(false);
            recipeRepository.save(recipe);
            return true;
        }
        return false;
    }
    
    
    /**
     * Get user statistics
     */
    public Map<String, Long> getUserStats(String userEmail) {
        User user = getOrCreateUser(userEmail);
        long totalRecipes = recipeRepository.countByUser(user);
        long favoriteRecipes = recipeRepository.countByUserAndIsFavoriteTrue(user);
        
        return Map.of(
            "totalRecipes", totalRecipes,
            "favoriteRecipes", favoriteRecipes
        );
    }
}
