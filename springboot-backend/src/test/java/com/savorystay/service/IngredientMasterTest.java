package com.savorystay.service;

import com.savorystay.common.IdGenerator;
import com.savorystay.common.IngredientNormalization;
import com.savorystay.common.UnitConverter;
import com.savorystay.entity.Ingredient;
import com.savorystay.entity.MenuItemIngredient;
import com.savorystay.repository.IngredientRepository;
import com.savorystay.repository.MenuItemIngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngredientMasterTest {

    private static final String REST_A = "REST_A";
    private static final String REST_B = "REST_B";

    @Mock IngredientRepository ingredientRepository;
    @Mock MenuItemIngredientRepository menuItemIngredientRepository;
    @Mock AuditService auditService;

    private IngredientService service;

    @BeforeEach
    void setUp() {
        // Minimal service construction — only fields needed for master tests
        service = new IngredientService(
                ingredientRepository, menuItemIngredientRepository,
                null, null, null, null, auditService);
    }

    private Ingredient ingredient(String name, String unit) {
        Ingredient i = Ingredient.builder()
                .id(IdGenerator.newId("ING"))
                .restaurantId(REST_A)
                .name(name)
                .normalizedName(IngredientNormalization.normalize(name))
                .displayName(name)
                .unit(unit)
                .active(true)
                .build();
        return i;
    }

    // ─── NORMALIZATION ────────────────────────────────────────────

    @Test
    void normalizeNameTrimsAndLowercases() {
        assertEquals("rice", IngredientNormalization.normalize(" Rice "));
        assertEquals("rice", IngredientNormalization.normalize("RICE"));
        assertEquals("rice", IngredientNormalization.normalize("RiCe"));
        assertEquals("chicken breast", IngredientNormalization.normalize("  Chicken   Breast "));
    }

    @Test
    void normalizeNameCollapsesWhitespace() {
        assertEquals("chicken breast", IngredientNormalization.normalize("  chicken   breast  "));
        assertEquals("a b c", IngredientNormalization.normalize("a  b  c"));
    }

    @Test
    void isDuplicateDetectsCaseInsensitive() {
        assertTrue(IngredientNormalization.isDuplicate("Rice", "rice"));
        assertTrue(IngredientNormalization.isDuplicate("RICE", "rice"));
        assertFalse(IngredientNormalization.isDuplicate("Rice", "Chicken"));
    }

    @Test
    void normalizeBlankNameReturnsEmpty() {
        assertEquals("", IngredientNormalization.normalize(null));
        assertEquals("", IngredientNormalization.normalize(""));
        assertEquals("", IngredientNormalization.normalize("   "));
    }

    // ─── CREATE WITH UNIQUENESS ──────────────────────────────────

    @Test
    void createSetsNormalizedName() {
        when(ingredientRepository.findByRestaurantIdAndNormalizedName(REST_A, "rice"))
                .thenReturn(Optional.empty());
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        Ingredient result = service.create(REST_A, ingredient("Rice", "kg"));

        assertEquals("rice", result.getNormalizedName());
        assertTrue(result.getActive());
        verify(ingredientRepository).save(result);
    }

    @Test
    void createRejectsDuplicateNormalizedName() {
        Ingredient existing = ingredient("rice", "kg");
        when(ingredientRepository.findByRestaurantIdAndNormalizedName(REST_A, "rice"))
                .thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.create(REST_A, ingredient("RICE", "kg")));

        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void createRejectsWhitespaceVariant() {
        Ingredient existing = ingredient("chicken", "kg");
        when(ingredientRepository.findByRestaurantIdAndNormalizedName(REST_A, "chicken"))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalArgumentException.class, () ->
                service.create(REST_A, ingredient("  Chicken  ", "kg")));
    }

    @Test
    void createRejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                service.create(REST_A, ingredient("  ", "kg")));
    }

    @Test
    void differentRestaurantsCanUseSameName() {
        Ingredient restBIIng = ingredient("Rice", "kg");
        restBIIng.setRestaurantId(REST_B);
        when(ingredientRepository.findByRestaurantIdAndNormalizedName(REST_B, "rice"))
                .thenReturn(Optional.empty());
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        // REST_B can create "Rice" because it doesn't exist there
        Ingredient result = service.create(REST_B, restBIIng);
        assertNotNull(result);
    }

    @Test
    void similarButDifferentNamesCanCoexist() {
        when(ingredientRepository.findByRestaurantIdAndNormalizedName(REST_A, "chicken"))
                .thenReturn(Optional.empty());
        when(ingredientRepository.findByRestaurantIdAndNormalizedName(REST_A, "chicken breast"))
                .thenReturn(Optional.empty());
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        Ingredient saved1 = service.create(REST_A, ingredient("Chicken", "kg"));
        Ingredient saved2 = service.create(REST_A, ingredient("Chicken Breast", "kg"));

        // Both should be saved successfully — they have different normalized names
        verify(ingredientRepository, times(2)).save(any(Ingredient.class));
        assertEquals("chicken", saved1.getNormalizedName());
        assertEquals("chicken breast", saved2.getNormalizedName());
    }

    // ─── SOFT DELETE ─────────────────────────────────────────────

    @Test
    void deactivateSetsActiveFalse() {
        Ingredient ing = ingredient("Salt", "kg");
        ing.setActive(true);
        when(ingredientRepository.findByIdAndRestaurantId(ing.getId(), REST_A))
                .thenReturn(Optional.of(ing));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        Ingredient result = service.deactivate(REST_A, ing.getId());

        assertFalse(result.getActive());
    }

    @Test
    void reactivateSetsActiveTrue() {
        Ingredient ing = ingredient("Salt", "kg");
        ing.setActive(false);
        when(ingredientRepository.findByIdAndRestaurantId(ing.getId(), REST_A))
                .thenReturn(Optional.of(ing));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        Ingredient result = service.reactivate(REST_A, ing.getId());

        assertTrue(result.getActive());
    }

    @Test
    void toggleActiveFlipsState() {
        Ingredient ing = ingredient("Pepper", "g");
        ing.setActive(true);
        when(ingredientRepository.findByIdAndRestaurantId(ing.getId(), REST_A))
                .thenReturn(Optional.of(ing));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(inv -> inv.getArgument(0));

        Ingredient result = service.toggleActive(REST_A, ing.getId());
        assertFalse(result.getActive());

        when(ingredientRepository.findByIdAndRestaurantId(ing.getId(), REST_A))
                .thenReturn(Optional.of(result));
        Ingredient result2 = service.toggleActive(REST_A, ing.getId());
        assertTrue(result2.getActive());
    }

    // ─── DELETE PREVENTION ───────────────────────────────────────

    @Test
    void deleteRejectsIngredientInUse() {
        Ingredient ing = ingredient("Chicken", "kg");
        when(ingredientRepository.findByIdAndRestaurantId(ing.getId(), REST_A))
                .thenReturn(Optional.of(ing));
        when(ingredientRepository.countRecipeUsage(ing.getId())).thenReturn(3L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.delete(REST_A, ing.getId()));

        assertTrue(ex.getMessage().contains("3 recipe(s)"));
        verify(ingredientRepository, never()).deleteById(anyString());
    }

    @Test
    void deleteAllowsUnusedIngredient() {
        Ingredient ing = ingredient("Rare Spice", "g");
        when(ingredientRepository.findByIdAndRestaurantId(ing.getId(), REST_A))
                .thenReturn(Optional.of(ing));
        when(ingredientRepository.countRecipeUsage(ing.getId())).thenReturn(0L);

        service.delete(REST_A, ing.getId());

        verify(ingredientRepository).deleteById(ing.getId());
    }

    // ─── SEARCH ──────────────────────────────────────────────────

    @Test
    void searchReturnsMatchingActiveIngredients() {
        Ingredient chicken = ingredient("Chicken", "kg");
        Ingredient breast = ingredient("Chicken Breast", "kg");
        when(ingredientRepository.searchActive(REST_A, "chick"))
                .thenReturn(List.of(chicken, breast));

        List<Ingredient> results = service.search(REST_A, "chick");

        assertEquals(2, results.size());
    }

    @Test
    void searchBlankQueryReturnsAllActive() {
        Ingredient rice = ingredient("Rice", "kg");
        when(ingredientRepository.findByRestaurantIdAndActiveTrueOrderByNameAsc(REST_A))
                .thenReturn(List.of(rice));

        List<Ingredient> results = service.search(REST_A, "");

        assertEquals(1, results.size());
    }

    // ─── UNIT CONVERSION ─────────────────────────────────────────

    @Test
    void convertKgToGrams() {
        BigDecimal result = UnitConverter.convert(new BigDecimal("1"), "kg", "g");
        assertEquals(0, new BigDecimal("1000").compareTo(result));
    }

    @Test
    void convertGramsToKg() {
        BigDecimal result = UnitConverter.convert(new BigDecimal("500"), "g", "kg");
        assertEquals(0, new BigDecimal("0.500").compareTo(result));
    }

    @Test
    void convertLitresToMl() {
        BigDecimal result = UnitConverter.convert(new BigDecimal("1"), "litre", "ml");
        assertEquals(0, new BigDecimal("1000").compareTo(result));
    }

    @Test
    void convertSameUnitReturnsSameValue() {
        BigDecimal result = UnitConverter.convert(new BigDecimal("42"), "kg", "kg");
        assertEquals(0, new BigDecimal("42").compareTo(result));
    }

    @Test
    void convertIncompatibleUnitsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                UnitConverter.convert(new BigDecimal("1"), "kg", "litre"));
    }

    @Test
    void areCompatibleDetectsSameGroup() {
        assertTrue(UnitConverter.areCompatible("kg", "g"));
        assertTrue(UnitConverter.areCompatible("ml", "litre"));
        assertTrue(UnitConverter.areCompatible("piece", "count"));
        assertFalse(UnitConverter.areCompatible("kg", "litre"));
        assertFalse(UnitConverter.areCompatible("g", "ml"));
    }

    // ─── RECIPE VALIDATION ──────────────────────────────────────

    @Test
    void findSimilarReturnsMatchingIngredients() {
        Ingredient chicken = ingredient("Chicken", "kg");
        Ingredient breast = ingredient("Chicken Breast", "kg");
        when(ingredientRepository.findByRestaurantIdOrderByNameAsc(REST_A))
                .thenReturn(List.of(chicken, breast));

        List<Ingredient> similar = service.findSimilar(REST_A, "chick");

        assertFalse(similar.isEmpty());
    }

    @Test
    void usageCountReturnsCorrectNumber() {
        when(ingredientRepository.countRecipeUsage("ING_123")).thenReturn(5L);

        long count = service.getUsageCount("ING_123");

        assertEquals(5L, count);
    }
}
