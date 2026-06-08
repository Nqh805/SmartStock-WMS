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

    // Kiểm tra tồn tại Serial
    boolean existsBySerialNumber(String serialNumber);

    // Đếm số lượng mã đã nạp cho 1 lô (Dùng khi tít súng quét serial)
    long countByImportBatchId(Long batchId);

    // Đếm số lượng mã đã nạp cho danh sách lô (Dùng để hiển thị phân số trên bảng
    // Lô hàng)
    @Query("SELECT p.importBatch.id, COUNT(p) FROM ProductItem p WHERE p.importBatch.id IN :batchIds GROUP BY p.importBatch.id")
    List<Object[]> countItemsByBatchIds(@Param("batchIds") List<Long> batchIds);

    // Tìm serial thuộc lô
    List<ProductItem> findByImportBatchId(Long batchId);

    // Tìm Serial chi tiết (Dùng cho module RMA)
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

    // Tìm theo trạng thái
    List<ProductItem> findByImportBatchIdAndStatus(Long batchId, ItemStatus status);

    // Tìm cơ bản
    Optional<ProductItem> findBySerialNumber(String serialNumber);

    // Đếm theo sản phẩm (Có thể dùng cho báo cáo hoặc check tồn tổng)
    long countByProductIdAndStatus(Long productId, ItemStatus status);
}