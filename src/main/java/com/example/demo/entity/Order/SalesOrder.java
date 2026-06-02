package com.example.demo.entity.Order;

import com.example.demo.entity.Partner.Customer;
// Không cần import Employee nữa vì đã có ở OrderHeader cha
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_order")
@PrimaryKeyJoinColumn(name = "id")
@Data
@EqualsAndHashCode(callSuper = true)
public class SalesOrder extends OrderHeader {

    @Column(name = "pickup_time")
    private LocalDateTime pickupTime;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    private DeliveryMethod deliveryMethod = DeliveryMethod.IN_STORE;

    @Column(name = "destination")
    private String destination;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SalesStatus status = SalesStatus.COMPLETED;

    @Column(name = "cod", precision = 38, scale = 2)
    private BigDecimal cod;

    @Column(name = "shipping_fee", precision = 15, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "total_sales_amount")
    private BigDecimal totalSalesAmount;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Transient
    public BigDecimal getRemainingAmount() {
        BigDecimal total = (this.totalSalesAmount != null) ? this.totalSalesAmount : BigDecimal.ZERO;
        BigDecimal paid = (this.paidAmount != null) ? this.paidAmount : BigDecimal.ZERO;
        return total.subtract(paid);
    }

    public enum DeliveryMethod {
        IN_STORE,
        SHIPPING,
        PRE_ORDER
    }

    public enum SalesStatus {
        PENDING, // Đang chờ khách đến lấy (Dùng cho đặt trước)
        COMPLETED, // Bán tại quầy thành công
        CANCELLED, // Hủy đơn / Trả hàng
        REFUNDED // Đã hoàn tiền
    }
}