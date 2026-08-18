package com.savorystay.repository;

import com.savorystay.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, String> {

    List<Ingredient> findByRestaurantIdOrderByNameAsc(String restaurantId);

    /** Active ingredients only — used for recipe selectors. */
    List<Ingredient> findByRestaurantIdAndActiveTrueOrderByNameAsc(String restaurantId);

    Optional<Ingredient> findByIdAndRestaurantId(String id, String restaurantId);

    Optional<Ingredient> findByRestaurantIdAndName(String restaurantId, String name);

    /** Find by normalized name for duplicate detection. */
    Optional<Ingredient> findByRestaurantIdAndNormalizedName(String restaurantId, String normalizedName);

    void deleteByRestaurantId(String restaurantId);

    /** Search by name fragment (case-insensitive) within a restaurant. */
    @Query("SELECT i FROM Ingredient i WHERE i.restaurantId = :restaurantId AND i.active = true " +
           "AND LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY i.name ASC")
    List<Ingredient> searchActive(@Param("restaurantId") String restaurantId, @Param("query") String query);

    /** Search by name fragment including inactive ingredients (for admin view). */
    @Query("SELECT i FROM Ingredient i WHERE i.restaurantId = :restaurantId " +
           "AND LOWER(i.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY i.name ASC")
    List<Ingredient> searchAll(@Param("restaurantId") String restaurantId, @Param("query") String query);

    /** Find by category within a restaurant. */
    List<Ingredient> findByRestaurantIdAndCategoryAndActiveTrueOrderByNameAsc(String restaurantId, String category);

    /** Count how many menu items reference this ingredient (via menu_item_ingredients). */
    @Query("SELECT COUNT(m) FROM MenuItemIngredient m WHERE m.ingredientId = :ingredientId")
    long countRecipeUsage(@Param("ingredientId") String ingredientId);
}
