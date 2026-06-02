package com.example.demo.entity.Partner;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "customer")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_name", nullable = false)
    private String name;

    @Column(unique = true)
    private String phone;

    private String email;
    private String address;

    @Column(name = "customer_receivable")
    private BigDecimal customerReceivable; // Công nợ khách hàng

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerType type;

    public enum CustomerType {
        BUSINESS,
        INDIVIDUAL
    }
}