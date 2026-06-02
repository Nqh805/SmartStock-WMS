package com.example.demo.service;

import com.example.demo.entity.Partner.Customer;
import com.example.demo.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;

    // Lấy danh sách phân trang và tìm kiếm
    public Page<Customer> getCustomers(String keyword, int page, int size) {
        // Sắp xếp khách hàng mới nhất lên đầu
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("id").descending());

        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        return customerRepository.searchCustomers(searchKeyword, pageable);
    }

    @Transactional
    public Customer addCustomer(Customer customer) {
        // Kiểm tra trùng lặp số điện thoại
        if (customer.getPhone() != null && customerRepository.findByPhone(customer.getPhone()).isPresent()) {
            throw new IllegalArgumentException("Số điện thoại này đã tồn tại trong hệ thống!");
        }

        // Mặc định công nợ ban đầu bằng 0
        customer.setCustomerReceivable(java.math.BigDecimal.ZERO);

        return customerRepository.save(customer);
    }

    // 1. Lấy thông tin khách hàng theo ID (Phục vụ Sửa)
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng có ID: " + id));
    }

    // 2. Cập nhật khách hàng (Tương tự lưu mới, nhưng giữ ID)
    @Transactional
    public void updateCustomer(Customer customer) {
        customerRepository.save(customer);
    }

    // 3. Xóa khách hàng
    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khách hàng!"));
        if (customer.getCustomerReceivable() != null
                && customer.getCustomerReceivable().compareTo(java.math.BigDecimal.ZERO) > 0) {
            throw new RuntimeException("Không thể xóa! Khách hàng này đang còn nợ "
                    + java.text.NumberFormat.getInstance(new Locale("vi", "VN"))
                            .format(customer.getCustomerReceivable())
                    + " đ. Vui lòng thu hồi công nợ trước!");
        }

        try {
            customerRepository.delete(customer);
            customerRepository.flush();

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new RuntimeException(
                    "Không thể xóa! Khách hàng này đã phát sinh giao dịch (có lịch sử Đơn hàng / Hóa đơn) trong hệ thống.");
        }
    }
}