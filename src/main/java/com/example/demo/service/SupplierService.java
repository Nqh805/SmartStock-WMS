package com.example.demo.service;

import com.example.demo.entity.Partner.Supplier;
import com.example.demo.repository.SupplierRepository;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

@Service
public class SupplierService {

    @Autowired
    private SupplierRepository supplierRepository;

    public Page<Supplier> findSuppliers(String keyword, int page, String sortBy, String direction) {
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page - 1, 10, sort);

        return supplierRepository.searchSuppliers(keyword, pageable);
    }

    @Transactional
    public Supplier addSupplier(Supplier supplier) {
        supplier.setSupplierPayable(java.math.BigDecimal.ZERO);

        return supplierRepository.save(supplier);
    }

    public Supplier getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà cung cấp có ID: " + id));
    }

    @Transactional
    public void updateSupplier(Supplier supplier) {
        supplierRepository.save(supplier);
    }

    @Transactional
    public void deleteSupplier(Long id) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà cung cấp!"));

        // Check công nợ trước khi xóa
        if (supplier.getSupplierPayable() != null
                && supplier.getSupplierPayable().compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Không thể xóa! Bạn đang còn nợ nhà cung cấp này "
                    + java.text.NumberFormat.getInstance(new java.util.Locale("vi", "VN"))
                            .format(supplier.getSupplierPayable())
                    + " đ. Vui lòng thanh toán công nợ trước!");
        }

        try {
            supplierRepository.delete(supplier);

            supplierRepository.flush();

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new RuntimeException(
                    "Không thể xóa! Nhà cung cấp này đã phát sinh giao dịch (có lịch sử Phiếu nhập / Lô hàng) trong hệ thống.");
        }
    }
}