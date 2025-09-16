package com.recipeassistant.repository;

import com.recipeassistant.model.Recipe;
import com.recipeassistant.model.User;
import com.recipeassistant.model.UserFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UUID> {
    
    /**
     * Find favorite by user and recipe
     */
    Optional<UserFavorite> findByUserAndRecipe(User user, Recipe recipe);
    
    /**
     * Check if recipe is favorited by user
     */
    boolean existsByUserAndRecipe(User user, Recipe recipe);
    
    
    /**
     * Delete favorite by user and recipe
     */
    void deleteByUserAndRecipe(User user, Recipe recipe);
    
}
