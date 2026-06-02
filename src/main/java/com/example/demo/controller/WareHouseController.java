package com.example.demo.controller;

import com.example.demo.entity.Warehouse.WareHouse;
import com.example.demo.service.WareHouseService;
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

@Controller
@RequestMapping("/warehouses")
@RequiredArgsConstructor
public class WareHouseController {

    private final WareHouseService wareHouseService;

    @GetMapping("/view")
    public String viewWarehouses(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            Model model) {

        int pageSize = 10; // Hiển thị 10 kho/trang
        Page<WareHouse> pageData = wareHouseService.getWarehousesWithPagination(keyword, page, pageSize);

        model.addAttribute("warehouses", pageData.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageData.getTotalPages());
        model.addAttribute("totalItems", pageData.getTotalElements());
        model.addAttribute("keyword", keyword);

        return "warehouse_list"; // Tên file HTML
    }

    @PostMapping("/delete/{id}")
    public String deleteWarehouse(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        try {
            wareHouseService.deleteWarehouse(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa dữ liệu thành công!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi xóa!");
        }

        return "redirect:/warehouses/view"; // Trở về trang danh sách
    }

    @GetMapping("/add")
    public String showAddWarehouseForm(Model model) {
        model.addAttribute("warehouse", new WareHouse());
        return "add_warehouse";
    }

    // 2. Xử lý lưu Kho mới
    @PostMapping("/add")
    public String createWarehouse(@ModelAttribute("warehouse") WareHouse warehouse,
            RedirectAttributes redirectAttributes) {
        try {
            wareHouseService.saveWarehouse(warehouse);
            redirectAttributes.addFlashAttribute("successMessage", "Khởi tạo nhà kho mới thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi lưu nhà kho: " + e.getMessage());
            return "redirect:/warehouses/add";
        }
        return "redirect:/warehouses/view";
    }

    // 3. Mở trang Form để Chỉnh sửa kho đã có
    @GetMapping("/edit/{id}")
    public String showEditWarehouseForm(@PathVariable("id") Long id, Model model) {
        try {
            WareHouse warehouse = wareHouseService.getWarehouseById(id);
            model.addAttribute("warehouse", warehouse);
            return "add_warehouse";
        } catch (Exception e) {
            return "redirect:/warehouses/view";
        }
    }

    // 4. Xử lý cập nhật thông tin Kho
    @PostMapping("/edit/{id}")
    public String updateWarehouse(@PathVariable("id") Long id,
            @ModelAttribute("warehouse") WareHouse warehouse,
            RedirectAttributes redirectAttributes) {
        try {
            warehouse.setId(id); // Giữ nguyên ID cũ
            wareHouseService.saveWarehouse(warehouse);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin nhà kho thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi cập nhật: " + e.getMessage());
            return "redirect:/warehouses/edit/" + id;
        }
        return "redirect:/warehouses/view";
    }
}