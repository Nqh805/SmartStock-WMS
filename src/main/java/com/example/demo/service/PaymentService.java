package com.example.demo.service;

import com.example.demo.dto.PaymentFormDTO;
import com.example.demo.entity.Order.PaymentStatus;
import com.example.demo.entity.Order.PaymentTransaction;
import com.example.demo.entity.Order.PurchaseOrder;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Transactional
    public void processPayment(Long poId, PaymentFormDTO formDTO) {
        // 1. Tìm đơn hàng
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã: " + poId));

        // 2. Tạo giao dịch lịch sử thanh toán mới
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setOrderHeader(po);
        transaction.setAmount(formDTO.getAmount());
        transaction.setPaymentMethod(formDTO.getPaymentMethod());
        transaction.setReferenceCode(formDTO.getReferenceCode());
        transaction.setNote(formDTO.getNote());
        transaction.setPaymentDate(LocalDateTime.now());

        // lưu ảnh biên lai
        MultipartFile imageFile = formDTO.getReceiptImage();
        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = saveReceiptImage(imageFile);
            transaction.setReceiptImageUrl(imageUrl);
        }

        paymentTransactionRepository.save(transaction);

        // Cập nhật số tiền đã trả cho Đơn hàng
        BigDecimal newPaidAmount = (po.getPaidAmount() != null ? po.getPaidAmount() : BigDecimal.ZERO)
                .add(formDTO.getAmount());
        po.setPaidAmount(newPaidAmount);

        // Cập nhật Trạng thái thanh toán (Logic: So sánh Đã trả và Tổng phải trả)
        if (newPaidAmount.compareTo(po.getTotalPurchaseAmount()) >= 0) {
            po.setPaymentStatus(PaymentStatus.PAID);
        } else {
            po.setPaymentStatus(PaymentStatus.PARTIAL);
        }

        purchaseOrderRepository.save(po);
    }

    private String saveReceiptImage(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        try {
            String projectPath = "src/main/resources/static/uploads/receipts/";
            String buildPath = "target/classes/static/uploads/receipts/";
            String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();

            new java.io.File(projectPath).mkdirs();
            new java.io.File(buildPath).mkdirs();

            java.nio.file.Path pathInProject = java.nio.file.Paths.get(projectPath + fileName);
            java.nio.file.Path pathInBuild = java.nio.file.Paths.get(buildPath + fileName);

            java.nio.file.Files.copy(imageFile.getInputStream(), pathInProject,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(imageFile.getInputStream(), pathInBuild,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/receipts/" + fileName;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Lỗi hệ thống khi lưu file ảnh: " + e.getMessage());
        }
    }
}