package com.savorystay.repository;

import com.savorystay.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, String> {
    List<OrderItem> findByOrderId(String orderId);
    List<OrderItem> findByOrderIdIn(List<String> orderIds);
    void deleteByOrderId(String orderId);
}
