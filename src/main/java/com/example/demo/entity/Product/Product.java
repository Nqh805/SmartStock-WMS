package com.example.demo.entity.Product;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "product")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "barcode", unique = true)
    private String barCode;

    @Column(name = "sku_code", unique = true)
    private String skuCode;

    @Column(name = "img_url")
    private String imgURL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProductStatus status;

    // 1. GIÁ BÁN NIÊM YẾT (Dùng cho luồng Bán hàng)
    @Column(name = "base_price", precision = 15, scale = 2)
    private BigDecimal basePrice;

    // 2. GIÁ VỐN TRUNG BÌNH - MAC (Dùng cho Kế toán tính lãi lỗ)
    @Column(name = "cost_price", precision = 15, scale = 2)
    private BigDecimal costPrice;

    // 3. GIÁ NHẬP THAM CHIẾU
    @Column(name = "standard_cost", precision = 15, scale = 2)
    private BigDecimal standardCost;

    @Column(name = "tax_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal taxRate = new BigDecimal("10.00");

    @Column(name = "unit", nullable = false, length = 50)
    private String unit = "Cái";

    @Column(name = "brand", length = 100)
    private String brand;

    @Column(name = "warranty_months", nullable = false)
    private Integer warrantyMonths = 0;

    @Column(name = "min_stock_level", nullable = false)
    private Integer minStockLevel = 0;

    @Column(name = "technical_specs", length = 1000)
    private String technicalSpecs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Transient
    private Integer totalQuantity;

    @Transient
    private BigDecimal finalSellingPrice;

    public BigDecimal getFinalSellingPrice() {
        if (this.basePrice == null)
            return BigDecimal.ZERO;
        if (this.taxRate == null || this.taxRate.compareTo(BigDecimal.ZERO) == 0)
            return this.basePrice;

        BigDecimal taxAmount = this.basePrice.multiply(this.taxRate).divide(new BigDecimal("100"));
        return this.basePrice.add(taxAmount);
    }
}