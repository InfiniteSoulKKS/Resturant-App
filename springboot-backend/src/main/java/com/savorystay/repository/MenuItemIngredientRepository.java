package com.savorystay.repository;

import com.savorystay.entity.MenuItemIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuItemIngredientRepository extends JpaRepository<MenuItemIngredient, Long> {
    List<MenuItemIngredient> findByMenuItemId(String menuItemId);
    List<MenuItemIngredient> findByMenuItemIdIn(List<String> menuItemIds);
    void deleteByMenuItemId(String menuItemId);
}
