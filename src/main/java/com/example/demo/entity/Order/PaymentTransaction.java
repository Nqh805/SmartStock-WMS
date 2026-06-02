package com.example.demo.entity.Order;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.demo.entity.User.Employee;

@Entity
@Table(name = "payment_transaction")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 50)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_code", length = 100)
    private String referenceCode;

    @Column(name = "note")
    private String note;

    @Column(name = "receipt_image_url", length = 500)
    private String receiptImageUrl;

    // Liên kết nhiều đợt thanh toán về một Đơn hàng (Mua hoặc Bán)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_header_id", nullable = false)
    private OrderHeader orderHeader;

    // Liên kết với nhân viên kế toán thực hiện chi/thu tiền
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;
}