package com.example.demo.repository;

import com.example.demo.entity.Order.SalesOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

        // CLEAN CODE: Tận dụng cơ chế tự sinh truy vấn của Spring Data.
        // Câu lệnh này tương đương với: SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE
        // false END FROM SalesOrder s WHERE s.code = :code
        boolean existsByCode(String code);

        @Query("SELECT s FROM SalesOrder s LEFT JOIN s.customer c WHERE (:keyword IS NULL OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR c.phone LIKE CONCAT('%', :keyword, '%'))")
        Page<SalesOrder> searchSalesOrders(@Param("keyword") String keyword, Pageable pageable);

        @Query("SELECT s FROM SalesOrder s LEFT JOIN s.customer c WHERE " +
                        "(:keyword IS NULL OR :keyword = '' OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR c.phone LIKE CONCAT('%', :keyword, '%')) AND "
                        +
                        "(:startDate IS NULL OR s.createdAt >= :startDate) AND " +
                        "(:endDate IS NULL OR s.createdAt <= :endDate)")
        Page<SalesOrder> searchWithFilters(
                        @Param("keyword") String keyword,
                        @Param("startDate") java.time.LocalDateTime startDate,
                        @Param("endDate") java.time.LocalDateTime endDate,
                        Pageable pageable);
}