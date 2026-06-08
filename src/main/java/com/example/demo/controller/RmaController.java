package com.example.demo.controller;

import com.example.demo.entity.Product.ProductItem;
import com.example.demo.entity.Order.RmaTicket;
import com.example.demo.repository.ProductItemRepository;
import com.example.demo.service.RmaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/rma")
@RequiredArgsConstructor
public class RmaController {

    private final ProductItemRepository productItemRepository;
    private final RmaService rmaService;

    @GetMapping("/check-serial")
    public String checkSerialInfo(@RequestParam(value = "serial", required = false) String serial, Model model) {

        if (serial != null && !serial.trim().isEmpty()) {
            String cleanSerial = serial.trim();
            model.addAttribute("searchKeyword", cleanSerial);

            Optional<ProductItem> itemOpt = productItemRepository.findBySerialNumberWithFullHistory(cleanSerial);

            if (itemOpt.isPresent()) {
                model.addAttribute("item", itemOpt.get());
            } else {
                model.addAttribute("notFound", true);
            }
        }

        return "check_serial";
    }

    // danh sách đơn
    @GetMapping("/warranties")
    public String viewRmaList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(name = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Model model) {

        int pageSize = 10;

        // Ép giờ: startDate thành 00:00:00, endDate thành 23:59:59
        java.time.LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
        java.time.LocalDateTime endDateTime = (endDate != null) ? endDate.atTime(23, 59, 59) : null;

        // Truyền xuống Service
        Page<RmaTicket> pageData = rmaService.getRmaTickets(keyword, startDateTime, endDateTime, page, pageSize);

        // Truyền dữ liệu ra View rma_list.html
        model.addAttribute("rmaList", pageData.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageData.getTotalPages());

        // Trả lại các giá trị lọc để giữ nguyên trên ô Input giao diện
        model.addAttribute("keyword", keyword);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "warranty_list";
    }

    // Mở Form tiếp nhận (Lấy Serial từ URL truyền vào)
    @GetMapping("/warranties/add")
    public String showAddRmaForm(@RequestParam(value = "serial", required = false) String serial, Model model,
            RedirectAttributes redirectAttributes) {

        if (serial == null || serial.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vui lòng tra cứu thiết bị trước khi lập phiếu RMA!");
            return "redirect:/rma/check-serial";
        }

        Optional<ProductItem> itemOpt = productItemRepository.findBySerialNumberWithFullHistory(serial.trim());
        if (itemOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy thiết bị với Serial này!");
            return "redirect:/rma/check-serial";
        }

        ProductItem item = itemOpt.get();
        if (item.getStatus() != com.example.demo.entity.Product.ItemStatus.SOLD) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Thiết bị chưa được bán hoặc không đủ điều kiện lập phiếu RMA!");
            return "redirect:/rma/check-serial?serial=" + serial;
        }

        RmaTicket ticket = new RmaTicket();
        ticket.setSerialNumber(serial);

        if (item.getSalesOrder() != null && item.getSalesOrder().getCustomer() != null) {
            ticket.setCustomer(item.getSalesOrder().getCustomer());
        }

        model.addAttribute("rmaTicket", ticket);
        return "add_rma";
    }

    // Xử lý lưu Form
    @PostMapping("/warranties/add")
    public String submitRmaForm(@ModelAttribute("rmaTicket") RmaTicket rmaTicket,
            RedirectAttributes redirectAttributes) {
        try {
            rmaService.createRmaTicket(rmaTicket, rmaTicket.getSerialNumber());
            redirectAttributes.addFlashAttribute("successMessage", "Tiếp nhận yêu cầu RMA thành công!");
            return "redirect:/rma/warranties";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi tạo phiếu: " + e.getMessage());
            return "redirect:/rma/check-serial";
        }
    }

    @GetMapping("/warranties/print/{id}")
    public String printRmaReceipt(@PathVariable("id") Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            // Lấy thông tin phiếu bảo hành từ Service
            RmaTicket rma = rmaService.getRmaTicketById(id);
            model.addAttribute("rma", rma);

            return "rma_receipt"; // Render ra file giao diện máy in

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi in: " + e.getMessage());
            return "redirect:/rma/warranties";
        }
    }

    @PostMapping("/warranties/update-status")
    public String updateStatus(@RequestParam("id") Long id,
            @RequestParam("status") RmaTicket.RmaStatus status,
            @RequestParam(value = "solution", required = false) String solution,
            @RequestParam(value = "newSerial", required = false) String newSerial,
            RedirectAttributes redirectAttributes) {
        try {
            // Truyền thẳng các tham số xuống Service xử lý
            rmaService.updateRmaStatusWithSwap(id, status, solution, newSerial);
            redirectAttributes.addFlashAttribute("successMessage", "Đã chốt kết quả và trả máy bảo hành thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/rma/warranties";
    }
}