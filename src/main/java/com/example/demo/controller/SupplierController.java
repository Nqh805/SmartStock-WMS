package com.example.demo.controller;

import com.example.demo.entity.Partner.Supplier;
import com.example.demo.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/suppliers")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    // API danh sách
    @GetMapping("/view")
    public String viewSuppliers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            Model model) {

        // 1. Lấy dữ liệu phân trang và tìm kiếm từ Service
        Page<Supplier> pageData = supplierService.findSuppliers(keyword, page, sortBy, direction);

        // 2. Đổ dữ liệu vào Model cho giao diện
        model.addAttribute("suppliers", pageData.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageData.getTotalPages());
        model.addAttribute("totalItems", pageData.getTotalElements());

        // 3. Giữ trạng thái sắp xếp và tìm kiếm trên thanh điều hướng
        model.addAttribute("keyword", keyword);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("direction", direction);
        model.addAttribute("reverseDirection", direction.equals("asc") ? "desc" : "asc");

        return "supplier_list"; // Trả về file HTML bạn vừa tạo
    }

    @GetMapping("/add")
    public String showAddSupplierForm(Model model) {
        model.addAttribute("supplier", new Supplier());
        return "add_supplier";
    }

    @PostMapping("/add")
    public String createSupplier(@ModelAttribute("supplier") Supplier supplier,
            Model model,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            supplierService.addSupplier(supplier);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm mới nhà cung cấp thành công!");
            return "redirect:/suppliers/view";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi lưu: " + e.getMessage());
            return "add_supplier";
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditSupplierForm(@PathVariable("id") Long id, Model model,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            Supplier supplier = supplierService.getSupplierById(id);
            model.addAttribute("supplier", supplier);
            return "add_supplier"; // Dùng chung form Add
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/suppliers/view";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateSupplier(@PathVariable("id") Long id, @ModelAttribute("supplier") Supplier supplier,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            supplier.setId(id);
            supplierService.updateSupplier(supplier);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin nhà cung cấp thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi cập nhật: " + e.getMessage());
            return "redirect:/suppliers/edit/" + id;
        }
        return "redirect:/suppliers/view";
    }

    @PostMapping("/delete/{id}")
    public String deleteSupplier(@PathVariable("id") Long id,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            supplierService.deleteSupplier(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa nhà cung cấp thành công!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage()); // Bắt lỗi công nợ / khóa ngoại
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi xóa!");
        }

        return "redirect:/suppliers/view";
    }
}