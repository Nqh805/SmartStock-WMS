package com.example.demo.config;

import com.example.demo.entity.User.Employee;
import com.example.demo.service.AccountService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.security.Principal;

@ControllerAdvice
public class GlobalAppConfig {

    private final AccountService accountService;

    public GlobalAppConfig(AccountService accountService) {
        this.accountService = accountService;
    }

    // 🚀 Hàm này sẽ ngầm tự động chạy trên TẤT CẢ các trang HTML
    // Nó sẽ nhét biến "currentUserAvatar" vào giao diện để ta lấy ảnh ra hiển thị
    @ModelAttribute("currentUserAvatar")
    public String populateAvatar(Principal principal) {
        if (principal != null) {
            Employee emp = accountService.getEmployeeByUsername(principal.getName());
            if (emp != null && emp.getUser() != null && emp.getUser().getAvatarUrl() != null) {
                return emp.getUser().getAvatarUrl(); // Trả về ảnh của cá nhân
            }
        }
        return "/asset/avatar.png"; // Trả về ảnh mặc định nếu chưa upload
    }
}