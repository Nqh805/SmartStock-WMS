package com.example.demo.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.Partner.Supplier;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Integer> {

    // Truy vấn tìm kiếm theo tên hoặc mã số thuế
    @Query("SELECT s FROM Supplier s WHERE " +
            "(:keyword IS NULL OR s.name LIKE %:keyword% OR s.taxCode LIKE %:keyword%)")
    Page<Supplier> searchSuppliers(@Param("keyword") String keyword, Pageable pageable);

    Optional<Supplier> findById(Long id);
}