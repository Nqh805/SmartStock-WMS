package com.example.demo.controller;

import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.demo.dto.OrderDetailDTO;
import com.example.demo.dto.PaymentFormDTO;
import com.example.demo.dto.PaymentTransactionDTO;
import com.example.demo.dto.ReceiptFormDTO;
import com.example.demo.entity.Order.PurchaseOrder;
import com.example.demo.entity.Order.PaymentTransaction;
import com.example.demo.entity.Partner.Supplier;
import com.example.demo.entity.Warehouse.WareHouse;
import com.example.demo.repository.PaymentTransactionRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.PurchaseOrderRepository;
import com.example.demo.repository.SupplierRepository;
import com.example.demo.repository.WareHouseRepository;
import com.example.demo.service.OrderDetailService;
import com.example.demo.service.PaymentService;
import com.example.demo.service.PurchaseOrderService;

@Controller
@RequestMapping("/purchases")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final OrderDetailService orderDetailService;
    private final PurchaseOrderService purchaseOrderService;
    private final PaymentService paymentService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ProductRepository productRepository;
    private final WareHouseRepository wareHouseRepository;
    private final SupplierRepository supplierRepository;

    @GetMapping("/view")
    public String viewPurchaseOrders(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(name = "page", defaultValue = "1") int page,
            Model model) {

        int pageSize = 10;
        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Ép giờ để tìm kiếm chính xác trọn vẹn trong ngày
        java.time.LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null; // 00:00:00
        java.time.LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(23, 59, 59) : null; // 23:59:59

        // Gọi hàm tìm kiếm tổng hợp vừa viết
        Page<PurchaseOrder> purchaseOrderPage = purchaseOrderRepository.searchWithFilters(keyword, startDateTime,
                endDateTime, pageable);

        // Trả kết quả về View
        model.addAttribute("purchaseOrders", purchaseOrderPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", purchaseOrderPage.getTotalPages());

        // Trả lại các giá trị lọc để hiển thị giữ lại trên ô Input của HTML
        model.addAttribute("keyword", keyword);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        // Lấy danh sách đang chờ duyệt gửi sang view
        List<PurchaseOrder> pendingOrders = purchaseOrderRepository.findByStatus(PurchaseOrder.PurchaseStatus.PENDING);
        model.addAttribute("pendingOrders", pendingOrders);
        model.addAttribute("pendingCount", pendingOrders.size());

        return "purchase_list";
    }

    // API Duyệt đơn hàng (Dùng chung cho cả duyệt đơn và báo cáo giao thất bại)
    @PostMapping("/approve/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> approvePurchaseOrder(@PathVariable("id") Long poId) {
        try {
            purchaseOrderService.approveOrder(poId);
            return org.springframework.http.ResponseEntity.ok().body("Duyệt đơn thành công");
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    // api chi tiết đơn hàng
    // api chi tiết đơn hàng (Đã đổi thành GET chuẩn RESTful)
    @GetMapping("/get-order-details")
    @ResponseBody
    public List<OrderDetailDTO> getOrderDetails(@RequestParam Long poId) {
        return orderDetailService.getOrderDetailsByOrderId(poId);
    }

    // modal nhận hàng: kiểm kê - sinh lô
    @PostMapping("/receive/{id}")
    public String receivePurchaseOrder(@PathVariable("id") Long poId,
            @ModelAttribute ReceiptFormDTO formDTO,
            @RequestParam(value = "deliveryDocumentFile", required = false) MultipartFile deliveryDocumentFile,
            RedirectAttributes redirectAttributes) {
        try {
            // gọi service
            purchaseOrderService.receiveOrderAndGenerateBatches(poId, formDTO, deliveryDocumentFile);

            redirectAttributes.addFlashAttribute("successMessage", "Xác nhận nhận hàng và sinh lô tự động thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi nhận hàng: " + e.getMessage());
        }

        return "redirect:/purchases/view";
    }

    // Modal hóa đơn thanh toán
    @PostMapping("/pay/{id}")
    public String processPayment(@PathVariable("id") Long poId,
            @ModelAttribute PaymentFormDTO paymentFormDTO,
            RedirectAttributes redirectAttributes) {
        try {
            paymentService.processPayment(poId, paymentFormDTO);
            redirectAttributes.addFlashAttribute("successMessage", "Ghi nhận thanh toán thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi thanh toán: " + e.getMessage());
        }
        return "redirect:/purchases/view";
    }

    // API LẤY LỊCH SỬ THANH TOÁN
    @GetMapping("/get-payment-history")
    @ResponseBody
    public List<PaymentTransactionDTO> getPaymentHistory(@RequestParam Long poId) {
        // Đổi tên hàm gọi ở đây:
        return paymentTransactionRepository.findByOrderHeaderIdOrderByPaymentDateDesc(poId)
                .stream()
                .map(PaymentTransactionDTO::new)
                .collect(Collectors.toList());
    }

    // Đổ dữ liệu lên form thêm mới
    @GetMapping("/add")
    public String showAddPurchaseOrderForm(Model model) {
        PurchaseOrder po = new PurchaseOrder();
        po.setSupplier(new Supplier());
        po.setWareHouse(new WareHouse());

        model.addAttribute("purchaseOrder", po);
        model.addAttribute("products", productRepository.findAll());

        model.addAttribute("suppliers", supplierRepository.findAll());
        model.addAttribute("warehouses", wareHouseRepository.findAll());

        return "add_purchase";
    }

    // Thêm mới sản phẩm
    @PostMapping("/add")
    public String createPurchaseOrder(@ModelAttribute("purchaseOrder") PurchaseOrder purchaseOrder,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            purchaseOrderService.createNewPurchaseOrder(purchaseOrder);
            redirectAttributes.addFlashAttribute("successMessage", "Lập đơn mua hàng mới thành công!");

            return "redirect:/purchases/view";

        } catch (IllegalArgumentException e) {
            // trả về view và đổ lại dữ liệu nếu có lỗi
            model.addAttribute("errorMessage", e.getMessage());

            model.addAttribute("products", productRepository.findAll());
            model.addAttribute("suppliers", supplierRepository.findAll());
            model.addAttribute("warehouses", wareHouseRepository.findAll());

            return "add_purchase";

        } catch (Exception e) {
            model.addAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi lập đơn: " + e.getMessage());

            model.addAttribute("products", productRepository.findAll());
            model.addAttribute("suppliers", supplierRepository.findAll());
            model.addAttribute("warehouses", wareHouseRepository.findAll());

            return "add_purchase";
        }
    }

    @PostMapping("/fail/{id}")
    public String failPurchaseOrder(@PathVariable("id") Long poId,
            @RequestParam("failReason") String failReason,
            RedirectAttributes redirectAttributes) {
        try {
            purchaseOrderService.reportFailedDelivery(poId, failReason);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Đã ghi nhận Đơn hàng giao thất bại và cập nhật lại Uy tín Nhà cung cấp!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/purchases/view";
    }

    @GetMapping("/print/{id}")
    public String printPurchaseOrder(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            PurchaseOrder po = purchaseOrderService.getPurchaseOrderById(id);
            model.addAttribute("po", po);

            List<PaymentTransaction> paymentHistory = paymentTransactionRepository
                    .findByOrderHeaderIdOrderByPaymentDateDesc(id);
            model.addAttribute("paymentHistory", paymentHistory);

            return "purchase_receipt"; // Render ra file giao diện máy in

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi in đơn nhập: " + e.getMessage());
            return "redirect:/purchases/view";
        }
    }

    // 1. API XÓA HẲN ĐƠN HÀNG
    @PostMapping("/delete/{id}")
    public String deletePurchaseOrder(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            purchaseOrderService.deletePurchaseOrder(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa vĩnh viễn đơn hàng nhập nhầm!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa đơn hàng: " + e.getMessage());
        }
        return "redirect:/purchases/view";
    }

    // 2. API TỪ CHỐI DUYỆT (Gọi bằng AJAX từ Modal)
    @PostMapping("/reject/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> rejectPurchaseOrder(@PathVariable("id") Long poId) {
        try {
            purchaseOrderService.rejectPurchaseOrder(poId);
            return org.springframework.http.ResponseEntity.ok().body("Từ chối đơn thành công");
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }
}