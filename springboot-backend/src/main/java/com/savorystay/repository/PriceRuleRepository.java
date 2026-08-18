package com.savorystay.repository;

import com.savorystay.entity.PriceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceRuleRepository extends JpaRepository<PriceRule, Long> {

    List<PriceRule> findByMenuItemIdOrderByEffectiveFromDesc(String menuItemId);

    /**
     * Current price: latest rule with effective_from <= now.
     */
    @Query("SELECT pr FROM PriceRule pr WHERE pr.menuItemId = :menuItemId AND pr.effectiveFrom <= :now " +
           "ORDER BY pr.effectiveFrom DESC")
    Optional<PriceRule> findCurrentEffective(@Param("menuItemId") String menuItemId,
                                             @Param("now") LocalDateTime now);

    List<PriceRule> findByMenuItemIdIn(List<String> menuItemIds);

    /**
     * All currently-effective rules for a set of menu items, newest first.
     * Consumers keep the first row per menu item id to avoid N+1 queries.
     */
    @Query("SELECT pr FROM PriceRule pr WHERE pr.menuItemId IN :ids AND pr.effectiveFrom <= :now " +
           "ORDER BY pr.menuItemId ASC, pr.effectiveFrom DESC")
    List<PriceRule> findEffectiveIn(@Param("ids") List<String> ids,
                                    @Param("now") LocalDateTime now);
}