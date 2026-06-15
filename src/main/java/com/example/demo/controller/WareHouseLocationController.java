package com.example.demo.controller;

import com.example.demo.entity.Product.ImportBatch;
import com.example.demo.entity.Warehouse.WareHouseLocation;
import com.example.demo.repository.ImportBatchRepository;
import com.example.demo.repository.WareHouseRepository;
import com.example.demo.service.WareHouseLocationService;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/locations")
@RequiredArgsConstructor
public class WareHouseLocationController {

    private final WareHouseLocationService locationService;
    private final ImportBatchRepository importBatchRepository;
    private final WareHouseRepository wareHouseRepository;

    @GetMapping("/view")
    public String viewLocations(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Model model) {

        int pageSize = 15; // Hiển thị 15 kệ/trang cho thoải mái
        Page<WareHouseLocation> pageData = locationService.getLocationsWithPagination(keyword, page, pageSize);

        model.addAttribute("locations", pageData.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageData.getTotalPages());
        model.addAttribute("totalItems", pageData.getTotalElements());
        model.addAttribute("keyword", keyword);

        model.addAttribute("warehouses", wareHouseRepository.findAll());

        return "location_list";
    }

    @GetMapping("/api/{id}/batches")
    @ResponseBody
    public ResponseEntity<?> getBatchesInLocation(@PathVariable("id") Long locationId) {
        try {
            List<ImportBatch> batches = importBatchRepository.findByLocationId(locationId);

            // Đóng gói dữ liệu thành JSON an toàn
            List<Map<String, Object>> result = batches.stream().map(b -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", b.getId()); // <-- THÊM DÒNG NÀY ĐỂ JS LẤY ĐƯỢC ID LÔ HÀNG
                map.put("batchCode", b.getBatchCode());
                map.put("productName", b.getProduct() != null ? b.getProduct().getName() : "Sản phẩm ẩn");
                map.put("quantity", b.getQuantity());
                map.put("importDate", b.getImportDate() != null ? b.getImportDate().toString() : "");
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi truy xuất dữ liệu: " + e.getMessage());
        }
    }

    @PostMapping("/api/batches/remove/{batchId}")
    @ResponseBody
    public ResponseEntity<?> removeBatchFromLocation(@PathVariable("batchId") Long batchId) {
        try {
            ImportBatch batch = importBatchRepository.findById(batchId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lô hàng này!"));

            // Giải phóng lô hàng bằng cách set vị trí kệ về null
            batch.setLocation(null);
            importBatchRepository.save(batch);

            return ResponseEntity.ok("Đã giải phóng lô hàng khỏi vị trí kệ thành công!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi hệ thống: " + e.getMessage());
        }
    }

    @PostMapping("/api/add")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> addLocationAjax(
            @RequestBody Map<String, String> payload) {
        try {
            Long warehouseId = Long.parseLong(payload.get("warehouseId"));
            String shelf = payload.get("shelfName");
            String tier = payload.get("tierName");
            String bin = payload.get("binName");
            String code = payload.get("locationCode");

            if (shelf == null || shelf.isEmpty() || tier == null || tier.isEmpty() || bin == null || bin.isEmpty()
                    || code == null || code.isEmpty()) {
                return org.springframework.http.ResponseEntity.badRequest()
                        .body("Vui lòng điền đầy đủ các trường bắt buộc!");
            }

            locationService.createLocation(warehouseId, shelf, tier, bin, code);
            return org.springframework.http.ResponseEntity.ok("Thêm mới vị trí kệ thành công!");

        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage()); // Trả về lỗi trùng mã
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.internalServerError()
                    .body("Lỗi hệ thống: " + e.getMessage());
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteLocation(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            locationService.deleteLocation(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa vị trí kệ thành công!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi xóa!");
        }

        return "redirect:/locations/view";
    }
}