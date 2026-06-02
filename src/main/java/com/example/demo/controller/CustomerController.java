package com.example.demo.controller;

import com.example.demo.entity.Partner.Customer;
import com.example.demo.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/view")
    public String viewCustomers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String keyword,
            Model model) {

        int pageSize = 10; // Cố định 10 khách hàng trên 1 trang
        Page<Customer> pageData = customerService.getCustomers(keyword, page, pageSize);

        // Đổ dữ liệu sang Thymeleaf
        model.addAttribute("customers", pageData.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageData.getTotalPages());
        model.addAttribute("totalItems", pageData.getTotalElements()); // Dành cho "Tổng số khách hàng" ở góc phải
        model.addAttribute("keyword", keyword);

        return "customer_list"; // Trả về file customer_list.html
    }

    // xử lý thêm khách hàng
    @GetMapping("/add")
    public String showAddCustomerForm(Model model) {
        model.addAttribute("customer", new Customer());
        return "add_customer";
    }

    @PostMapping("/add")
    public String createCustomer(@ModelAttribute("customer") Customer customer,
            Model model,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            // Tái sử dụng lại hàm addCustomer có sẵn trong CustomerService
            customerService.addCustomer(customer);

            redirectAttributes.addFlashAttribute("successMessage", "Thêm mới đối tác khách hàng thành công!");
            return "redirect:/customers/view";

        } catch (IllegalArgumentException e) {
            // Bắt lỗi trùng lặp số điện thoại, giữ lại thông tin form để người dùng sửa
            model.addAttribute("errorMessage", e.getMessage());
            return "add_customer";

        } catch (Exception e) {
            model.addAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi lưu: " + e.getMessage());
            return "add_customer";
        }
    }

    @PostMapping("/api/add")
    @ResponseBody
    public ResponseEntity<?> addCustomerAjax(@RequestBody Customer customer) {
        try {
            Customer savedCustomer = customerService.addCustomer(customer);
            return ResponseEntity.ok(savedCustomer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Lỗi hệ thống: " + e.getMessage());
        }
    }

    @GetMapping("/edit/{id}")
    public String showEditCustomerForm(@PathVariable("id") Long id, Model model,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            Customer customer = customerService.getCustomerById(id);
            model.addAttribute("customer", customer);
            return "add_customer"; // Dùng chung form Add
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/customers/view";
        }
    }

    @PostMapping("/edit/{id}")
    public String updateCustomer(@PathVariable("id") Long id, @ModelAttribute("customer") Customer customer,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            customer.setId(id); // Giữ nguyên ID cũ để Update thay vì Insert
            customerService.updateCustomer(customer);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin khách hàng thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi cập nhật: " + e.getMessage());
            return "redirect:/customers/edit/" + id;
        }
        return "redirect:/customers/view";
    }

    @PostMapping("/delete/{id}")
    public String deleteCustomer(@PathVariable("id") Long id,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            customerService.deleteCustomer(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa khách hàng thành công!");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Đã xảy ra lỗi hệ thống khi xóa!");
        }

        return "redirect:/customers/view";
    }
}