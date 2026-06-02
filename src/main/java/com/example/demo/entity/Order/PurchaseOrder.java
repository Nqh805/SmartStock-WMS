package com.example.demo.entity.Order;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

import com.example.demo.entity.Partner.Supplier;

@Entity
@Table(name = "purchase_order")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrder extends OrderHeader {

    @Column(name = "actual_arrival")
    private LocalDate actualArrival;

    @Column(name = "total_purchase_amount")
    private BigDecimal totalPurchaseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PurchaseStatus status = PurchaseStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_result")
    private DeliveryResult deliveryResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount = BigDecimal.ZERO; // Mặc định ban đầu bằng 0

    @Column(name = "delivery_document", length = 255)
    private String deliveryDocument;

    @Column(name = "received_note")
    private String receivedNote;

    // tính tiền còn lại
    @Transient
    public BigDecimal getRemainingAmount() {
        BigDecimal total = (this.totalPurchaseAmount != null) ? this.totalPurchaseAmount : BigDecimal.ZERO;
        BigDecimal paid = (this.paidAmount != null) ? this.paidAmount : BigDecimal.ZERO;

        return total.subtract(paid);
    }

    public enum PurchaseStatus {
        PENDING, // Chờ duyệt / Chờ giao
        IN_TRANSIT, // Nhà cung cấp đang giao tới
        COMPLETED, // Đã nhận hàng vào kho
        CANCELLED // Đã hủy
    }
}