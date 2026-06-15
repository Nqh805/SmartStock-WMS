package com.example.demo.entity.Order;

import com.example.demo.entity.Partner.Customer;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

@Entity
@Table(name = "rma_ticket")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RmaTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", unique = true, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private RmaType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "serial_number")
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RmaStatus status = RmaStatus.PROCESSING;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "estimated_completion_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate estimatedCompletionDate;

    @Column(name = "warranty_solution", length = 1000)
    private String warrantySolution;

    @Column(name = "replacement_serial_number")
    private String replacementSerialNumber;

    @Column(name = "actual_return_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate actualReturnDate;

    public enum RmaType {
        WARRANTY,
    }

    public enum RmaStatus {
        PROCESSING, // Đang xử lý (Gửi đi hãng)
        COMPLETED, // Đã trả khách hàng
        CANCELLED // Từ chối bảo hành / Hủy phiếu
    }
}