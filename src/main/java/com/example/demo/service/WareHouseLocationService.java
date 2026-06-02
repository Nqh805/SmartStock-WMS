package com.example.demo.service;

import com.example.demo.entity.Warehouse.WareHouseLocation;
import com.example.demo.repository.WareHouseLocationRepository;
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
public class WareHouseLocationService {

    private final WareHouseLocationRepository locationRepository;

    public Page<WareHouseLocation> getLocationsWithPagination(String keyword, int pageNo, int pageSize) {
        // Sắp xếp theo Tên Kho -> Dãy Kệ -> Tầng -> Ô cho dễ nhìn
        Sort sort = Sort.by("wareHouse.name").ascending()
                .and(Sort.by("shelfName").ascending())
                .and(Sort.by("tierName").ascending())
                .and(Sort.by("binName").ascending());

        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);

        String searchKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        return locationRepository.searchLocations(searchKeyword, pageable);
    }

    // Nhớ inject thêm WareHouseRepository vào đầu class bằng
    // @RequiredArgsConstructor hoặc khai báo biến
    private final com.example.demo.repository.WareHouseRepository wareHouseRepository;

    @Transactional
    public void createLocation(Long warehouseId, String shelf, String tier, String bin, String code) {
        String cleanCode = code.trim().toUpperCase();

        // 1. Kiểm tra trùng lặp mã vạch vị trí
        if (locationRepository.findByLocationCode(cleanCode).isPresent()) {
            throw new IllegalArgumentException(
                    "Mã vị trí (Barcode) '" + cleanCode + "' này đã tồn tại trong hệ thống!");
        }

        // 2. Tìm Kho tương ứng
        com.example.demo.entity.Warehouse.WareHouse wh = wareHouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho lưu trữ đã chọn!"));

        // 3. Khởi tạo và lưu Entity
        com.example.demo.entity.Warehouse.WareHouseLocation loc = new com.example.demo.entity.Warehouse.WareHouseLocation();
        loc.setWareHouse(wh);
        loc.setShelfName(shelf.trim());
        loc.setTierName(tier.trim());
        loc.setBinName(bin.trim());
        loc.setLocationCode(cleanCode);

        locationRepository.save(loc);
    }

    @Transactional
    public void deleteLocation(Long id) {
        try {
            locationRepository.deleteById(id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new RuntimeException("Lỗi: Không thể xóa vị trí kệ này vì đang có Lô hàng lưu trữ trên kệ!");
        }
    }
}