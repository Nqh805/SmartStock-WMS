package com.example.demo.entity.Partner;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "supplier")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "tax_code", unique = true)
    private String taxCode;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "supplier_status", nullable = false)
    private SupplierStatus supplierStatus;

    private String phone;

    private String address;

    @Column(name = "supplier_payable", precision = 10, scale = 2)
    private BigDecimal supplierPayable;

    @Column(name = "reliability_score")
    private Integer reliabilityScore = 100;

    @Column(name = "failed_delivery_count")
    private Integer failedDeliveryCount;

    @Column(name = "late_delivery_count")
    private Integer lateDeliveryCount;
}