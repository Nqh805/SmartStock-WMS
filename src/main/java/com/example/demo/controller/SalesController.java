package com.example.demo.controller;

import com.example.demo.dto.OrderDetailDTO;
import com.example.demo.entity.Order.SalesOrder; // Import class tương ứng
import com.example.demo.repository.ProductItemRepository;
import com.example.demo.service.SalesOrderService;
import com.example.demo.service.OrderDetailService;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.example.demo.service.ProductService;

import lombok.RequiredArgsConstructor;

import java.util.List;

@Controller
@RequestMapping("/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesOrderService salesOrderService;
    private final OrderDetailService orderDetailService;
    private final ProductService productService;
    private final ProductItemRepository productItemRepository;

    // Xem danh sách đơn bán hàng
    @GetMapping("/view")
    public String viewSalesOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            Model model) {

        // Ép giờ: startDate thành 00:00:00, endDate thành 23:59:59
        java.time.LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
        java.time.LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(23, 59, 59) : null;

        // Truyền xuống Service
        Page<SalesOrder> pageData = salesOrderService.getSalesOrder(keyword, startDateTime, endDateTime, page, 10);

        model.addAttribute("salesOrders", pageData.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageData.getTotalPages());

        // Trả lại các giá trị lọc để giữ nguyên trên ô Input giao diện
        model.addAttribute("keyword", keyword);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "sales_list";
    }

    // API lấy sản phẩm chi tiết nạp lên Modal
    @GetMapping("/get-order-details")
    @ResponseBody
    public ResponseEntity<List<OrderDetailDTO>> getSalesOrderDetails(@RequestParam("soId") Long soId) {
        List<OrderDetailDTO> details = orderDetailService.getOrderDetailsByOrderId(soId);
        return ResponseEntity.ok(details);
    }

    @GetMapping("/add")
    public String showAddSalesForm(Model model) {
        model.addAttribute("salesOrder", new SalesOrder());
        model.addAttribute("customers", salesOrderService.getAllCustomers());
        model.addAttribute("warehouses", salesOrderService.getAllWarehouses());

        // SỬA DÒNG NÀY:
        model.addAttribute("products", productService.getAllProductsWithInventory());

        return "add_sales";
    }

    @PostMapping("/add")
    public String createNewSalesOrder(@ModelAttribute("salesOrder") SalesOrder salesOrder,
            @RequestParam(value = "scannedSerials", required = false) List<String> scannedSerials,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            salesOrderService.createNewSalesOrder(salesOrder, scannedSerials);

            redirectAttributes.addFlashAttribute("successMessage", "Tạo đơn bán hàng và xuất kho thành công!");
            return "redirect:/sales/view";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("customers", salesOrderService.getAllCustomers());
            model.addAttribute("warehouses", salesOrderService.getAllWarehouses());
            model.addAttribute("products", productService.getAllProductsWithInventory());
            return "add_sales";

        } catch (Exception e) {
            model.addAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi chốt đơn: " + e.getMessage());
            model.addAttribute("customers", salesOrderService.getAllCustomers());
            model.addAttribute("warehouses", salesOrderService.getAllWarehouses());
            model.addAttribute("products", productService.getAllProductsWithInventory());
            return "add_sales";
        }
    }

    // 🚀 2. BỔ SUNG API CHO SÚNG QUÉT MÃ GỌI VÀO
    @GetMapping("/api/scan-serial")
    @ResponseBody
    public ResponseEntity<?> scanSerialForSale(@RequestParam("serial") String serial) {
        try {
            com.example.demo.entity.Product.ProductItem item = productItemRepository.findBySerialNumber(serial.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Mã Serial '" + serial + "' không tồn tại!"));

            if (item.getStatus() != com.example.demo.entity.Product.ItemStatus.IN_STOCK) {
                throw new IllegalStateException("Sản phẩm mang mã '" + serial + "' đang không ở trong Kho!");
            }

            // Đóng gói thông tin sản phẩm trả về cho Javascript
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("serial", item.getSerialNumber());
            response.put("productId", item.getProduct().getId());
            response.put("productName", item.getProduct().getName());
            response.put("sku", item.getProduct().getSkuCode());
            response.put("unit", item.getProduct().getUnit() != null ? item.getProduct().getUnit() : "-");
            response.put("price",
                    item.getProduct().getFinalSellingPrice() != null ? item.getProduct().getFinalSellingPrice()
                            : item.getProduct().getBasePrice());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // API Xử lý thu tiền khách hàng bằng AJAX
    @PostMapping("/api/pay")
    @ResponseBody
    public ResponseEntity<?> processOrderPayment(@RequestBody java.util.Map<String, String> payload) {
        try {
            Long orderId = Long.parseLong(payload.get("orderId"));
            java.math.BigDecimal amount = new java.math.BigDecimal(payload.get("amount"));

            if (amount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body("Số tiền thanh toán phải lớn hơn 0!");
            }

            salesOrderService.processPayment(orderId, amount);
            return ResponseEntity.ok("Ghi nhận thanh toán thành công!");

        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi hệ thống khi thanh toán: " + e.getMessage());
        }
    }

    @GetMapping("/print/{id}")
    public String printSalesReceipt(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            // Lấy thông tin đơn hàng từ Service
            SalesOrder order = salesOrderService.getSalesOrderById(id);
            model.addAttribute("order", order);

            return "sales_receipt"; // Render ra file giao diện máy in

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi in hóa đơn: " + e.getMessage());
            return "redirect:/sales/view";
        }
    }
}