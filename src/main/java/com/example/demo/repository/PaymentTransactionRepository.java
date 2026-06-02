package com.example.demo.repository;

import com.example.demo.entity.Order.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByOrderHeaderIdOrderByPaymentDateDesc(Long orderHeaderId);
}