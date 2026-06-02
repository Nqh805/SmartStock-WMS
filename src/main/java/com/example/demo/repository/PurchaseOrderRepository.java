package com.example.demo.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.Order.PurchaseOrder;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    // Tìm kiếm theo Mã đơn hoặc Tên đơn (Bỏ qua viết hoa/thường)
    @Query("SELECT p FROM PurchaseOrder p WHERE " +
            "LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<PurchaseOrder> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p FROM PurchaseOrder p WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND "
            +
            "(:startDate IS NULL OR p.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR p.createdAt <= :endDate)")
    Page<PurchaseOrder> searchWithFilters(
            @Param("keyword") String keyword,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            Pageable pageable);

    boolean existsByCode(String code);

    List<PurchaseOrder> findByStatus(PurchaseOrder.PurchaseStatus status);
}