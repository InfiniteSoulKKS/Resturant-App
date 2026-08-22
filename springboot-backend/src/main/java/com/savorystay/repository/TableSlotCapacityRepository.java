package com.savorystay.repository;

import com.savorystay.entity.TableSlotCapacity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TableSlotCapacityRepository extends JpaRepository<TableSlotCapacity, Long> {

    List<TableSlotCapacity> findByRestaurantIdAndBusinessDateAndTimeSlot(
            String restaurantId, LocalDate businessDate, String timeSlot);

    Optional<TableSlotCapacity> findByRestaurantIdAndBusinessDateAndTimeSlotAndTableType(
            String restaurantId, LocalDate businessDate, String timeSlot, String tableType);

    List<TableSlotCapacity> findByRestaurantIdAndBusinessDate(
            String restaurantId, LocalDate businessDate);

    /**
     * Find or create table slot capacity records with row locking.
     * The SELECT FOR UPDATE pattern ensures concurrent checkouts serialize.
     */
    @Query(value = "SELECT * FROM table_slot_capacity ts " +
           "WHERE ts.restaurant_id = :restaurantId " +
           "AND ts.business_date = :businessDate " +
           "AND ts.time_slot = :timeSlot " +
           "FOR UPDATE", nativeQuery = true)
    List<TableSlotCapacity> findByRestaurantAndDateAndSlotForUpdate(
            @Param("restaurantId") String restaurantId,
            @Param("businessDate") LocalDate businessDate,
            @Param("timeSlot") String timeSlot);

    @Query(value = "SELECT * FROM table_slot_capacity ts " +
           "WHERE ts.restaurant_id = :restaurantId " +
           "AND ts.business_date = :businessDate " +
           "AND ts.time_slot = :timeSlot " +
           "AND ts.table_type = :tableType " +
           "FOR UPDATE", nativeQuery = true)
    Optional<TableSlotCapacity> findByRestaurantAndDateAndSlotAndTypeForUpdate(
            @Param("restaurantId") String restaurantId,
            @Param("businessDate") LocalDate businessDate,
            @Param("timeSlot") String timeSlot,
            @Param("tableType") String tableType);
}
