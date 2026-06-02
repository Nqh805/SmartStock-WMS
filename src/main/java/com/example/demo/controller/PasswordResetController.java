package com.example.demo.controller;

import com.example.demo.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // Nhận Request gửi Mail
    @PostMapping("/forgot_password/send")
    public String sendResetLink(@RequestParam("email") String email, HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            // Lấy URL gốc của server (VD: http://localhost:8080)
            String appUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            passwordResetService.processForgotPassword(email, appUrl);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Một đường link đặt lại mật khẩu đã được gửi đến email của bạn.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/reset_password";
    }

    // Khách click vào link trong Email
    @GetMapping("/reset_password/confirm")
    public String showChangePasswordPage(@RequestParam("token") String token, Model model) {
        model.addAttribute("token", token);
        return "change_password"; // Gọi giao diện tạo mật khẩu mới
    }

    // Khách Submit mật khẩu mới
    @PostMapping("/reset_password/save")
    public String saveNewPassword(@RequestParam("token") String token,
            @RequestParam("newPassword") String newPassword,
            RedirectAttributes redirectAttributes) {
        try {
            passwordResetService.updatePassword(token, newPassword);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Mật khẩu đã được thay đổi thành công. Vui lòng đăng nhập lại!");
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/reset_password";
        }
    }
}