package com.savorystay.common;

/**
 * Utility methods for ingredient name normalization.
 * Ensures unique ingredient names within a restaurant by stripping
 * whitespace, lowercasing, and collapsing repeated spaces.
 */
public final class IngredientNormalization {

    private IngredientNormalization() {}

    /**
     * Normalize an ingredient name for uniqueness comparison.
     * <ul>
     *   <li>Trim leading/trailing whitespace</li>
     *   <li>Lowercase</li>
     *   <li>Collapse repeated whitespace to single space</li>
     * </ul>
     *
     * Examples:
     * <pre>
     *   " Rice "           → "rice"
     *   "RICE"             → "rice"
     *   "RiCe"             → "rice"
     *   "  Chicken   Breast " → "chicken breast"
     * </pre>
     */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) return "";
        return name.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Returns true if two ingredient names normalize to the same value.
     */
    public static boolean isDuplicate(String name1, String name2) {
        return normalize(name1).equals(normalize(name2));
    }
}
