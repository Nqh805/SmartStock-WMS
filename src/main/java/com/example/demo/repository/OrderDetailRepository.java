package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.Order.OrderDetail;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    List<OrderDetail> findByOrderHeaderId(Long orderHeaderId);

    OrderDetail findByOrderHeaderIdAndProductId(Long orderHeaderId, Long productId);

    Optional<OrderDetail> findByImportBatchId(Long batchId);
}
