package com.example.demo.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.Product.ImportBatch;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
        boolean existsByBatchCode(String batchCode);

        List<ImportBatch> findByLocationId(Long locationId);

        @Query("SELECT SUM(b.quantity) FROM ImportBatch b WHERE b.product.id = :productId")
        Integer sumQuantityByProductId(@Param("productId") Long productId);

        // 1. Hàm lấy TẤT CẢ (Kèm FETCH các bảng liên quan)
        @Query(value = "SELECT b FROM ImportBatch b " +
                        "LEFT JOIN FETCH b.product " +
                        "LEFT JOIN FETCH b.wareHouse " +
                        "LEFT JOIN FETCH b.location " +
                        "LEFT JOIN FETCH b.purchaseOrder", countQuery = "SELECT COUNT(b) FROM ImportBatch b")
        Page<ImportBatch> findAllWithDetails(Pageable pageable);

        // 2. Hàm TÌM KIẾM CHÍNH XÁC (Dùng dấu = thay cho LIKE)
        @Query(value = "SELECT b FROM ImportBatch b " +
                        "LEFT JOIN FETCH b.product " +
                        "LEFT JOIN FETCH b.wareHouse " +
                        "LEFT JOIN FETCH b.location " +
                        "LEFT JOIN FETCH b.purchaseOrder po " + // Alias po
                        "WHERE (:keyword IS NULL OR " +
                        "LOWER(b.batchCode) = LOWER(:keyword) OR " +
                        "LOWER(b.product.name) = LOWER(:keyword) OR " +
                        "LOWER(po.code) = LOWER(:keyword))",

                        countQuery = "SELECT COUNT(b) FROM ImportBatch b " +
                                        "LEFT JOIN b.product p " +
                                        "LEFT JOIN b.purchaseOrder po " +
                                        "WHERE (:keyword IS NULL OR " +
                                        "LOWER(b.batchCode) = LOWER(:keyword) OR " +
                                        "LOWER(p.name) = LOWER(:keyword) OR " +
                                        "LOWER(po.code) = LOWER(:keyword))")
        Page<ImportBatch> searchBatches(@Param("keyword") String keyword, Pageable pageable);

        List<ImportBatch> findByProductIdAndQuantityGreaterThanOrderByImportDateAscIdAsc(Long productId,
                        Integer minQuantity);

        // Bác thêm hàm này vào ImportBatchRepository.java nhé:
        @org.springframework.data.jpa.repository.Query("SELECT DISTINCT b FROM ImportBatch b " +
                        "JOIN FETCH b.product " +
                        "JOIN FETCH b.wareHouse " +
                        "ORDER BY b.importDate DESC, b.id DESC")
        java.util.List<com.example.demo.entity.Product.ImportBatch> findAllWithProductAndWarehouse();
}