package com.example.demo.repository;

import com.example.demo.entity.Order.RmaTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RmaTicketRepository extends JpaRepository<RmaTicket, Long> {
    @Query(value = "SELECT r FROM RmaTicket r LEFT JOIN r.customer c WHERE " +
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:startDate IS NULL OR r.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR r.createdAt <= :endDate)", countQuery = "SELECT COUNT(r) FROM RmaTicket r LEFT JOIN r.customer c WHERE "
                    +
                    "(:keyword IS NULL OR :keyword = '' OR " +
                    "LOWER(r.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(r.serialNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
                    "(:startDate IS NULL OR r.createdAt >= :startDate) AND " +
                    "(:endDate IS NULL OR r.createdAt <= :endDate)")
    Page<RmaTicket> searchWithFilters(
            @Param("keyword") String keyword,
            @Param("startDate") java.time.LocalDateTime startDate,
            @Param("endDate") java.time.LocalDateTime endDate,
            Pageable pageable);
}