package com.example.demo.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.Product.ItemStatus;
import com.example.demo.entity.Product.ProductItem;

@Repository
public interface ProductItemRepository extends JpaRepository<ProductItem, Long> {
    // Đếm số lượng ProductItem cho một danh sách Batch ID
    @Query("SELECT p.importBatch.id, COUNT(p) FROM ProductItem p WHERE p.importBatch.id IN :batchIds GROUP BY p.importBatch.id")
    List<Object[]> countByBatchIds(@Param("batchIds") List<Long> batchIds);

    boolean existsBySerialNumber(String serialNumber);

    long countByImportBatchId(Long batchId);

    List<ProductItem> findByImportBatchId(Long batchId);

    @Query("SELECT item FROM ProductItem item " +
            "LEFT JOIN FETCH item.product p " +
            "LEFT JOIN FETCH item.importBatch b " +
            "LEFT JOIN FETCH b.purchaseOrder po " +
            "LEFT JOIN FETCH po.supplier s " +
            "LEFT JOIN FETCH b.wareHouse w " +
            "LEFT JOIN FETCH b.location l " +
            "LEFT JOIN FETCH item.salesOrder so " +
            "LEFT JOIN FETCH so.customer c " +
            "WHERE LOWER(item.serialNumber) = LOWER(:serial)")
    Optional<ProductItem> findBySerialNumberWithFullHistory(@Param("serial") String serial);

    List<ProductItem> findByImportBatchIdAndStatus(Long batchId, ItemStatus status);

    Optional<ProductItem> findBySerialNumber(String serialNumber);

}