package com.savorystay.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;

/**
 * Centralized unit conversion for ingredient recipes.
 * Supports compatible groups: weight (g, kg) and volume (ml, litre).
 * Does NOT convert between weight and volume (1 kg ≠ 1 litre).
 */
public final class UnitConverter {

    private UnitConverter() {}

    /** Conversion factors to base unit within each group. */
    private static final Map<String, BigDecimal> WEIGHT_FACTORS = Map.of(
            "g",  BigDecimal.ONE,
            "kg", new BigDecimal("1000")
    );

    private static final Map<String, BigDecimal> VOLUME_FACTORS = Map.of(
            "ml",    BigDecimal.ONE,
            "litre", new BigDecimal("1000")
    );

    private static final Set<String> COUNT_UNITS = Set.of("piece", "count", "pcs");

    /**
     * Convert a quantity from one unit to another within the same compatible group.
     *
     * @throws IllegalArgumentException if units are incompatible or unknown
     */
    public static BigDecimal convert(BigDecimal quantity, String fromUnit, String toUnit) {
        if (quantity == null) return BigDecimal.ZERO;
        if (fromUnit.equalsIgnoreCase(toUnit)) return quantity;

        String from = fromUnit.toLowerCase();
        String to = toUnit.toLowerCase();

        // Same group check
        if (WEIGHT_FACTORS.containsKey(from) && WEIGHT_FACTORS.containsKey(to)) {
            BigDecimal baseGrams = quantity.multiply(WEIGHT_FACTORS.get(from));
            return baseGrams.divide(WEIGHT_FACTORS.get(to), 3, RoundingMode.HALF_UP);
        }

        if (VOLUME_FACTORS.containsKey(from) && VOLUME_FACTORS.containsKey(to)) {
            BigDecimal baseMl = quantity.multiply(VOLUME_FACTORS.get(from));
            return baseMl.divide(VOLUME_FACTORS.get(to), 3, RoundingMode.HALF_UP);
        }

        if (COUNT_UNITS.contains(from) && COUNT_UNITS.contains(to)) {
            return quantity; // pieces map 1:1
        }

        throw new IllegalArgumentException(
                "Cannot convert from '" + fromUnit + "' to '" + toUnit + "': incompatible unit groups. " +
                "Weight (g/kg), volume (ml/litre), and count (piece/count) cannot be mixed."
        );
    }

    /**
     * Check if two units are compatible (can be converted between each other).
     */
    public static boolean areCompatible(String unit1, String unit2) {
        if (unit1 == null || unit2 == null) return false;
        String u1 = unit1.toLowerCase();
        String u2 = unit2.toLowerCase();
        if (u1.equals(u2)) return true;
        return (WEIGHT_FACTORS.containsKey(u1) && WEIGHT_FACTORS.containsKey(u2))
            || (VOLUME_FACTORS.containsKey(u1) && VOLUME_FACTORS.containsKey(u2))
            || (COUNT_UNITS.contains(u1) && COUNT_UNITS.contains(u2));
    }

    /**
     * Convert a recipe quantity to the ingredient's base unit for forecasting.
     * e.g. 500g rice with base unit kg → 0.5
     */
    public static BigDecimal toBaseUnit(BigDecimal quantity, String fromUnit, String baseUnit) {
        return convert(quantity, fromUnit, baseUnit);
    }
}
