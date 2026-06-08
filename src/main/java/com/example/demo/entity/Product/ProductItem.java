package com.example.demo.entity.Product;

import com.example.demo.entity.Order.SalesOrder;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_item")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, unique = true)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ItemStatus status = ItemStatus.IN_STOCK;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "batch_id", nullable = true)
    private ImportBatch importBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_order_id")
    private SalesOrder salesOrder;

    @Transient
    public java.time.LocalDateTime getWarrantyExpirationDate() {
        if (this.salesOrder != null && this.salesOrder.getCreatedAt() != null
                && this.product != null && this.product.getWarrantyMonths() != null) {
            // Lấy ngày tạo đơn bán + Số tháng bảo hành
            return this.salesOrder.getCreatedAt().plusMonths(this.product.getWarrantyMonths());
        }
        return null;
    }

    @Transient
    public boolean isWarrantyExpired() {
        java.time.LocalDateTime expDate = getWarrantyExpirationDate();
        if (expDate != null) {
            // Trả về true nếu ngày hết hạn < ngày hiện tại
            return expDate.isBefore(java.time.LocalDateTime.now());
        }
        return false;
    }
}