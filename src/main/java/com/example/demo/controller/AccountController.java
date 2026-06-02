package com.example.demo.controller;

import com.example.demo.dto.AccountDTO;
import com.example.demo.entity.User.Employee;
import com.example.demo.service.AccountService;
import lombok.RequiredArgsConstructor;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/manage")
    public String manageAccounts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String keyword,
            Model model) {

        Page<Employee> pageData = accountService.getAccounts(keyword, page, 10);

        model.addAttribute("employees", pageData.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageData.getTotalPages());
        model.addAttribute("keyword", keyword);
        model.addAttribute("roles", accountService.getAllRoles()); // Truyền Roles ra form Select

        return "account_list";
    }

    @PostMapping("/add")
    public String addAccount(@ModelAttribute AccountDTO dto, RedirectAttributes redirectAttributes) {
        try {
            accountService.createAccount(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Tạo tài khoản thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/accounts/manage";
    }

    @PostMapping("/update")
    public String updateAccount(@ModelAttribute AccountDTO dto, RedirectAttributes redirectAttributes) {
        try {
            accountService.updateAccount(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật tài khoản thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/accounts/manage";
    }

    @PostMapping("/lock/{id}")
    public String toggleLock(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        accountService.toggleLockAccount(id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã thay đổi trạng thái tài khoản!");
        return "redirect:/accounts/manage";
    }

    // XEM HỒ SƠ CÁ NHÂN
    @GetMapping("/profile")
    public String viewProfile(java.security.Principal principal, Model model) {
        String username = principal.getName();
        Employee employee = accountService.getEmployeeByUsername(username);
        model.addAttribute("employee", employee);
        model.addAttribute("warehouses", accountService.getAllWarehouses()); // Truyền danh sách kho ra
        return "account_profile";
    }

    // Sửa hàm updateProfile (Nhận thêm wareHouseId)
    @PostMapping("/profile/update")
    public String updateProfile(java.security.Principal principal,
            @RequestParam("fullName") String fullName,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "newPassword", required = false) String newPassword,
            @RequestParam(value = "avatarFile", required = false) org.springframework.web.multipart.MultipartFile avatarFile,
            @RequestParam(value = "wareHouseId", required = false) Long wareHouseId, // 👈 Bổ sung
            RedirectAttributes redirectAttributes) {
        try {
            accountService.updatePersonalProfile(principal.getName(), fullName, phone, email, newPassword, avatarFile,
                    wareHouseId);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật hồ sơ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/accounts/profile";
    }

    @GetMapping("/add")
    public String showAddAccountForm(Model model) {
        model.addAttribute("accountDTO", new AccountDTO());
        model.addAttribute("roles", accountService.getAllRoles());
        model.addAttribute("warehouses", accountService.getAllWarehouses());
        return "add_account";
    }

    // 🚀 BỔ SUNG: Mở trang Chỉnh sửa
    @GetMapping("/edit/{id}")
    public String showEditAccountForm(@PathVariable("id") Long id, Model model) {
        AccountDTO dto = accountService.getAccountDTOById(id);
        model.addAttribute("accountDTO", dto);
        model.addAttribute("roles", accountService.getAllRoles());
        model.addAttribute("warehouses", accountService.getAllWarehouses());
        return "add_account";
    }

}