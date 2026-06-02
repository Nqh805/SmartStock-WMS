package com.example.demo.config;

import com.example.demo.entity.User.Role;
import com.example.demo.entity.User.User;
import com.example.demo.entity.User.UserStatus;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder; // Bộ mã hóa mật khẩu

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        // 1. TẠO 3 ROLE (NẾU CHƯA CÓ)
        Role managerRole = createRoleIfNotFound("MANAGER", "Thủ kho (Toàn quyền)");
        Role inspectorRole = createRoleIfNotFound("INSPECTOR", "Kiểm kho (Quản lý hàng, duyệt nhập)");
        Role staffRole = createRoleIfNotFound("STAFF", "Nhân viên kho (Xem và kiểm kê)");

        // 2. TẠO TÀI KHOẢN ADMIN ĐẦU TIÊN (NẾU CHƯA CÓ)
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            // Mật khẩu là "123456" nhưng sẽ được mã hóa an toàn trước khi lưu vào DB
            admin.setPassword(passwordEncoder.encode("123456"));
            admin.setStatus(UserStatus.ACTIVE);

            // Cấp quyền Thủ kho cho tài khoản này
            admin.getRoles().add(managerRole);

            userRepository.save(admin);
            System.out.println("🚀 ĐÃ KHỞI TẠO TÀI KHOẢN MẶC ĐỊNH: admin / 123456");
        }
    }

    private Role createRoleIfNotFound(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName(name);
            newRole.setDescription(description);
            return roleRepository.save(newRole);
        });
    }
}