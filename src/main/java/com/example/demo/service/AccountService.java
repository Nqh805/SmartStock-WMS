package com.example.demo.service;

import com.example.demo.dto.AccountDTO;
import com.example.demo.entity.User.Employee;
import com.example.demo.entity.User.Role;
import com.example.demo.entity.User.User;
import com.example.demo.entity.User.UserStatus;
import com.example.demo.entity.Warehouse.WareHouse;
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

    // --- CONSTANTS ---
    private static final String DEFAULT_ADMIN_NAME = "Quản trị viên Cấp cao";
    private static final String DEFAULT_IMG_EXTENSION = ".jpg";

    private static final String PROJECT_AVATAR_PATH = "src/main/resources/static/uploads/avatars/";
    private static final String BUILD_AVATAR_PATH = "target/classes/static/uploads/avatars/";
    private static final String WEB_AVATAR_PATH = "/uploads/avatars/";

    // --- REPOSITORIES & COMPONENTS ---
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final WareHouseRepository wareHouseRepository;
    private final PasswordEncoder passwordEncoder;

    public Page<Employee> getAccounts(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        return employeeRepository.searchAccounts(keyword, pageable);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public List<WareHouse> getAllWarehouses() {
        return wareHouseRepository.findAll();
    }

    public AccountDTO getAccountDTOById(Long empId) {
        Employee emp = employeeRepository.findById(empId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên"));

        AccountDTO dto = new AccountDTO();
        dto.setEmployeeId(emp.getId());
        dto.setFullName(emp.getFullName());
        dto.setEmail(emp.getEmail());
        dto.setPhone(emp.getPhone());

        if (emp.getWareHouse() != null) {
            dto.setWareHouseId(emp.getWareHouse().getId());
        }

        User user = emp.getUser();
        if (user != null) {
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setStatus(user.getStatus().name());

            if (user.getRoles() != null && !user.getRoles().isEmpty()) {
                dto.setRoleId(user.getRoles().iterator().next().getId());
            }
        }

        return dto;
    }

    // tạo tk mới
    @Transactional
    public void createAccount(AccountDTO dto) {
        validateUniqueFieldsForCreation(dto);

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setStatus(UserStatus.valueOf(dto.getStatus()));

        Role role = roleRepository.findById(dto.getRoleId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chức vụ!"));
        user.getRoles().add(role);

        user = userRepository.save(user);

        Employee emp = new Employee();
        emp.setFullName(dto.getFullName());
        emp.setEmail(dto.getEmail());
        emp.setPhone(dto.getPhone());
        emp.setUser(user);
        emp.setWareHouse(fetchWarehouseOrDefault(dto.getWareHouseId()));

        employeeRepository.save(emp);
    }

    // cập nhật thông tin tài khoản
    @Transactional
    public void updateAccount(AccountDTO dto) {
        Employee emp = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhân viên!"));
        User user = emp.getUser();

        emp.setFullName(dto.getFullName());
        emp.setEmail(dto.getEmail());
        emp.setPhone(dto.getPhone());
        emp.setWareHouse(fetchWarehouseOrDefault(dto.getWareHouseId()));

        user.setStatus(UserStatus.valueOf(dto.getStatus()));

        Role role = roleRepository.findById(dto.getRoleId()).orElseThrow();
        user.getRoles().clear();
        user.getRoles().add(role);

        // Mã hóa và ghi đè mật khẩu nếu người dùng có nhập mới
        if (dto.getPassword() != null && !dto.getPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        userRepository.save(user);
        employeeRepository.save(emp);
    }

    @Transactional
    public void toggleLockAccount(Long employeeId) {
        Employee emp = employeeRepository.findById(employeeId).orElseThrow();
        User user = emp.getUser();

        if (user.getStatus() == UserStatus.ACTIVE) {
            user.setStatus(UserStatus.INACTIVE);
        } else {
            user.setStatus(UserStatus.ACTIVE);
        }

        userRepository.save(user);
    }

    public Employee getEmployeeByUsername(String username) {
        return employeeRepository.findByUserUsername(username).orElseGet(() -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user!"));

            Employee emp = new Employee();
            emp.setFullName(DEFAULT_ADMIN_NAME);
            emp.setUser(user);
            return employeeRepository.save(emp);
        });
    }

    // cập nhật thông tin cá nhân của chính mình
    @Transactional
    public void updatePersonalProfile(String username, String fullName, String phone, String email,
            String newPassword, MultipartFile avatarFile, Long wareHouseId) {

        Employee emp = getEmployeeByUsername(username);
        emp.setFullName(fullName);
        emp.setPhone(phone);
        emp.setEmail(email);
        emp.setWareHouse(fetchWarehouseOrDefault(wareHouseId));
        employeeRepository.save(emp);

        User user = emp.getUser();
        if (avatarFile != null && !avatarFile.isEmpty()) {
            String newAvatarUrl = saveAvatarImage(avatarFile, username);
            user.setAvatarUrl(newAvatarUrl);
        }

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(newPassword));
        }

        userRepository.save(user);
    }

    // ========== PRIVATE HELPER METHODS =========

    // validate username, email, phone
    private void validateUniqueFieldsForCreation(AccountDTO dto) {
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Tên đăng nhập đã tồn tại!");
        }

        if (employeeRepository.findByEmail(dto.getEmail())
                .filter(existing -> !existing.getId().equals(dto.getEmployeeId()))
                .isPresent()) {
            throw new IllegalArgumentException("Email đã tồn tại!");
        }

        if (employeeRepository.findByPhone(dto.getPhone())
                .filter(existing -> !existing.getId().equals(dto.getEmployeeId()))
                .isPresent()) {
            throw new IllegalArgumentException("Số điện thoại đã tồn tại!");
        }
    }

    private WareHouse fetchWarehouseOrDefault(Long wareHouseId) {
        if (wareHouseId == null) {
            return null;
        }
        return wareHouseRepository.findById(wareHouseId).orElse(null);
    }

    // Lưu trữ vật lý file ảnh đại diện vào hệ thống và trả về đường dẫn Web URL
    private String saveAvatarImage(MultipartFile file, String username) {
        try {
            String originalFileName = file.getOriginalFilename();
            String extension = (originalFileName != null && originalFileName.contains("."))
                    ? originalFileName.substring(originalFileName.lastIndexOf("."))
                    : DEFAULT_IMG_EXTENSION;

            String fileName = "avatar_" + username + "_" + System.currentTimeMillis() + extension;

            new java.io.File(PROJECT_AVATAR_PATH).mkdirs();
            new java.io.File(BUILD_AVATAR_PATH).mkdirs();

            java.nio.file.Path pathInProject = java.nio.file.Paths.get(PROJECT_AVATAR_PATH + fileName);
            java.nio.file.Path pathInBuild = java.nio.file.Paths.get(BUILD_AVATAR_PATH + fileName);

            java.nio.file.Files.copy(file.getInputStream(), pathInProject,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Files.copy(file.getInputStream(), pathInBuild,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return WEB_AVATAR_PATH + fileName;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi lưu ảnh đại diện: " + e.getMessage());
        }
    }
}