package com.example.demo.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.demo.entity.Partner.Customer;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPhone(String phone);

    // 2. Tìm khách hàng chính xác theo Email
    Optional<Customer> findByEmail(String email);

    // 3. Tìm kiếm tương đối (LIKE) theo Tên hoặc Số điện thoại
    // 🚀 BỔ SUNG: (:keyword IS NULL OR ...)
    @Query("SELECT c FROM Customer c WHERE " +
            "(:keyword IS NULL OR " +
            "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "c.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<Customer> searchCustomers(@Param("keyword") String keyword, Pageable pageable);

    // 4. Lấy danh sách khách hàng theo phân loại (Doanh nghiệp / Khách lẻ)
    List<Customer> findByType(Customer.CustomerType type);
}