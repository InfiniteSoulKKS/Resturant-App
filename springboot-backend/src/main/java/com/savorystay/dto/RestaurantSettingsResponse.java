package com.savorystay.dto;

import com.savorystay.entity.RestaurantSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** View model for restaurant table/time-slot configuration. */
public record RestaurantSettingsResponse(
        String restaurantId,
        String tableConfig,
        List<Map<String, Object>> tableTypes,
        Integer totalTables,
        List<String> pickupTimeSlots,
        List<String> dineinTimeSlots) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static RestaurantSettingsResponse from(RestaurantSettings s) {
        List<Map<String, Object>> tableTypes = parseTableConfig(s.getTableConfig());
        return new RestaurantSettingsResponse(
                s.getRestaurantId(),
                s.getTableConfig(),
                tableTypes,
                s.getTotalTables(),
                parseList(s.getPickupTimeSlots()),
                parseList(s.getDineinTimeSlots()));
    }

    /** Parse the JSON table config string into a list of {type, count} maps. */
    public static List<Map<String, Object>> parseTableConfig(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<String> parseList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
