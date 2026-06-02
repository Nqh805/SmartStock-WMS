package com.example.demo.service;

import com.example.demo.dto.AccountDTO;
import com.example.demo.entity.User.Employee;
import com.example.demo.entity.User.Role;
import com.example.demo.entity.User.User;
import com.example.demo.entity.User.UserStatus;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WareHouseRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private final WareHouseRepository wareHouseRepository;

    // 1. LẤY DANH SÁCH & TÌM KIẾM
    public Page<Employee> getAccounts(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return employeeRepository.searchAccounts(keyword, pageable);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public List<com.example.demo.entity.Warehouse.WareHouse> getAllWarehouses() {
        return wareHouseRepository.findAll();
    }

    public AccountDTO getAccountDTOById(Long empId) {
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên"));
        AccountDTO dto = new AccountDTO();
        dto.setEmployeeId(emp.getId());
        dto.setUserId(emp.getUser().getId());
        dto.setUsername(emp.getUser().getUsername());
        dto.setRoleId(emp.getUser().getRoles().iterator().next().getId());
        dto.setStatus(emp.getUser().getStatus().name());
        dto.setFullName(emp.getFullName());
        dto.setEmail(emp.getEmail());
        dto.setPhone(emp.getPhone());
        if (emp.getWareHouse() != null)
            dto.setWareHouseId(emp.getWareHouse().getId());
        return dto;
    }

    // 2. THÊM TÀI KHOẢN MỚI
    @Transactional
    public void createAccount(AccountDTO dto) {
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại!");
        }

        // Bước 1: Tạo User
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword())); // Mã hóa MK
        user.setStatus(UserStatus.valueOf(dto.getStatus()));

        Role role = roleRepository.findById(dto.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chức vụ!"));
        user.getRoles().add(role);
        user = userRepository.save(user); // Lưu User trước để lấy ID

        // Bước 2: Tạo Employee map với User vừa tạo
        Employee emp = new Employee();
        emp.setFullName(dto.getFullName());
        emp.setEmail(dto.getEmail());
        emp.setPhone(dto.getPhone());
        emp.setUser(user); // Gắn khóa ngoại
        if (dto.getWareHouseId() != null) {
            emp.setWareHouse(wareHouseRepository.findById(dto.getWareHouseId()).orElse(null));
        }
        employeeRepository.save(emp);
    }

    // 3. SỬA TÀI KHOẢN
    @Transactional
    public void updateAccount(AccountDTO dto) {
        Employee emp = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên!"));
        User user = emp.getUser();

        // Cập nhật Employee
        emp.setFullName(dto.getFullName());
        emp.setEmail(dto.getEmail());
        emp.setPhone(dto.getPhone());
        if (dto.getWareHouseId() != null) {
            emp.setWareHouse(wareHouseRepository.findById(dto.getWareHouseId()).orElse(null));
        } else {
            emp.setWareHouse(null);
        }

        // Cập nhật User
        user.setStatus(UserStatus.valueOf(dto.getStatus()));
        Role role = roleRepository.findById(dto.getRoleId()).orElseThrow();
        user.getRoles().clear();
        user.getRoles().add(role);

        // Chỉ đổi mật khẩu nếu user có nhập mật khẩu mới
        if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        userRepository.save(user);
        employeeRepository.save(emp);
    }

    // 4. KHÓA TÀI KHOẢN (Thay vì xóa vật lý)
    @Transactional
    public void toggleLockAccount(Long employeeId) {
        Employee emp = employeeRepository.findById(employeeId).orElseThrow();
        User user = emp.getUser();

        if (user.getStatus() == UserStatus.ACTIVE) {
            user.setStatus(UserStatus.INACTIVE); // Khóa
        } else {
            user.setStatus(UserStatus.ACTIVE); // Mở khóa
        }
        userRepository.save(user);
    }

    // 5. LẤY THÔNG TIN HỒ SƠ CÁ NHÂN (VÀ TỰ ĐỘNG CỨU CÁNH TÀI KHOẢN ADMIN)
    public Employee getEmployeeByUsername(String username) {
        return employeeRepository.findByUserUsername(username).orElseGet(() -> {
            // Khi tài khoản admin tạo từ file DataSeeder đăng nhập, nó chưa có bảng
            // Employee cá nhân.
            // Đoạn code này sẽ tự động phát hiện và bù đắp thông tin cho admin.
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user!"));

            Employee emp = new Employee();
            emp.setFullName("Quản trị viên Cấp cao"); // Tên mặc định
            emp.setUser(user);
            return employeeRepository.save(emp);
        });
    }

    // 6. CẬP NHẬT HỒ SƠ CÁ NHÂN
    @Transactional
    public void updatePersonalProfile(String username, String fullName, String phone, String email,
            String newPassword, org.springframework.web.multipart.MultipartFile avatarFile,
            Long wareHouseId) {
        Employee emp = getEmployeeByUsername(username);
        emp.setFullName(fullName);
        emp.setPhone(phone);
        emp.setEmail(email);
        employeeRepository.save(emp);

        User user = emp.getUser();

        // 1. XỬ LÝ UPLOAD ẢNH ĐẠI DIỆN
        if (avatarFile != null && !avatarFile.isEmpty()) {
            String newAvatarUrl = saveAvatarImage(avatarFile, username);
            user.setAvatarUrl(newAvatarUrl); // Cập nhật link ảnh mới vào DB
        }

        // 2. XỬ LÝ ĐỔI MẬT KHẨU
        if (newPassword != null && !newPassword.trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        if (wareHouseId != null) {
            emp.setWareHouse(wareHouseRepository.findById(wareHouseId).orElse(null));
        }

        userRepository.save(user);
    }

    // 🚀 BỔ SUNG HÀM NÀY: Lưu ảnh vật lý xuống ổ cứng Server
    private String saveAvatarImage(MultipartFile file, String username) {
        try {
            String projectPath = "src/main/resources/static/uploads/avatars/";
            String buildPath = "target/classes/static/uploads/avatars/";

            // Lấy đuôi file (vd: .jpg, .png)
            String originalFileName = file.getOriginalFilename();
            String extension = originalFileName != null ? originalFileName.substring(originalFileName.lastIndexOf("."))
                    : ".jpg";
            String fileName = "avatar_" + username + "_" + System.currentTimeMillis() + extension;

            // Tạo thư mục nếu chưa có
            new java.io.File(projectPath).mkdirs();
            new java.io.File(buildPath).mkdirs();

            java.nio.file.Path pathInProject = java.nio.file.Paths.get(projectPath + fileName);
            java.nio.file.Path pathInBuild = java.nio.file.Paths.get(buildPath + fileName);

            // Copy file vào project
            java.nio.file.Files.copy(file.getInputStream(), pathInProject,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(file.getInputStream(), pathInBuild,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Trả về đường dẫn để lưu vào DB
            return "/uploads/avatars/" + fileName;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi lưu ảnh đại diện: " + e.getMessage());
        }
    }
}