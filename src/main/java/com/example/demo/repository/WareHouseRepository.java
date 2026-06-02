package com.example.demo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.Warehouse.WareHouse;

@Repository
public interface WareHouseRepository extends JpaRepository<WareHouse, Long> {

    // Hàm tìm kiếm và phân trang
    @Query(value = "SELECT w FROM WareHouse w " +
            "WHERE (:keyword IS NULL OR " +
            "LOWER(w.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(w.managerName) LIKE LOWER(CONCAT('%', :keyword, '%')))",

            countQuery = "SELECT COUNT(w) FROM WareHouse w " +
                    "WHERE (:keyword IS NULL OR " +
                    "LOWER(w.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(w.managerName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<WareHouse> searchWarehouses(@Param("keyword") String keyword, Pageable pageable);

}