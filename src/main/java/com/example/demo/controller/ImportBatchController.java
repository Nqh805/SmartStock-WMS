package com.example.demo.controller;

import com.example.demo.dto.ScanRequestDTO;
import com.example.demo.entity.Product.ImportBatch;
import com.example.demo.repository.ProductItemRepository;
import com.example.demo.service.ImportBatchService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/batches")
@RequiredArgsConstructor
public class ImportBatchController {

    private final ImportBatchService importBatchService;
    private final ProductItemRepository productItemRepository;

    @GetMapping("/view")
    public String viewBatches(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Model model) {

        int pageSize = 10;

        // 1. Lấy danh sách lô hàng từ Service
        Page<ImportBatch> batchPage = importBatchService.getBatchesWithPagination(keyword, page, pageSize);

        // 2. Nhờ Service đếm hộ số lượng dựa trên danh sách vừa lấy
        Map<Long, Long> scannedCounts = importBatchService.getScannedCountsForBatches(batchPage.getContent());

        // 3. Truyền ra View
        model.addAttribute("batches", batchPage.getContent());
        model.addAttribute("scannedCounts", scannedCounts);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", batchPage.getTotalPages());
        model.addAttribute("keyword", keyword);

        return "batch_list";
    }

    // API nhận mảng Serial và nạp vào Database
    @PostMapping("/scan-serials")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<?> scanSerials(
            @org.springframework.web.bind.annotation.RequestBody ScanRequestDTO request) {
        try {
            importBatchService.saveScannedSerials(request.batchId, request.serials);
            return org.springframework.http.ResponseEntity.ok().body(Map.of("message", "Thành công"));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // API lấy danh sách mã Serial/IMEI đã nạp của một Lô hàng (Trả về dạng JSON)
    @GetMapping("/get-serials")
    @ResponseBody
    public java.util.List<java.util.Map<String, Object>> getSerialsByBatch(@RequestParam("batchId") Long batchId) {
        return productItemRepository.findByImportBatchId(batchId)
                .stream()
                .map(item -> {
                    java.util.Map<String, Object> map = new java.util.HashMap<>();
                    map.put("serialNumber", item.getSerialNumber());
                    map.put("status", item.getStatus() != null ? item.getStatus().name() : "IN_STOCK");
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @PostMapping("/api/putaway")
    @ResponseBody
    public ResponseEntity<?> putawayBatchAjax(@RequestBody Map<String, String> payload) {
        try {
            Long batchId = Long.parseLong(payload.get("batchId"));
            String locationCode = payload.get("locationCode");

            importBatchService.putawayBatch(batchId, locationCode);
            return ResponseEntity.ok("Xếp kệ vị trí lô hàng thành công!");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi hệ thống: " + e.getMessage());
        }
    }
}