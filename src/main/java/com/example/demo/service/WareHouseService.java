package com.example.demo.service;

import com.example.demo.entity.Warehouse.WareHouse;
import com.example.demo.repository.WareHouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WareHouseService {

    private final WareHouseRepository wareHouseRepository;

    public Page<WareHouse> getWarehousesWithPagination(String keyword, int pageNo, int pageSize) {
        // Sắp xếp theo ID giảm dần (Kho mới tạo lên đầu)
        Sort sort = Sort.by("id").descending();
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);

        // Xử lý keyword rỗng
        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        return wareHouseRepository.searchWarehouses(searchKeyword, pageable);
    }

    @Transactional
    public void deleteWarehouse(Long id) {
        try {
            wareHouseRepository.deleteById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new RuntimeException(
                    "Không thể xóa nhà kho này vì đang có dữ liệu (Vị trí kệ, Lô hàng...) liên kết tới nó!");
        }
    }

    public WareHouse getWarehouseById(Long id) {
        return wareHouseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin nhà kho có ID: " + id));
    }

    // Lưu thông tin Thêm mới hoặc Cập nhật
    @Transactional
    public void saveWarehouse(WareHouse wareHouse) {
        // Tự động sinh mã kho (Code) nếu người dùng để trống
        if (wareHouse.getCode() == null || wareHouse.getCode().trim().isEmpty()) {
            String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss")
                    .format(java.time.LocalDateTime.now());
            wareHouse.setCode("WH-" + timestamp);
        } else {
            wareHouse.setCode(wareHouse.getCode().trim().toUpperCase());
        }

        wareHouseRepository.save(wareHouse);
    }
}