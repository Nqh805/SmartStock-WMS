package com.example.demo.dto;

import lombok.Data;

@Data
public class AccountDTO {
    private Long employeeId; // ID của Employee (dùng khi cập nhật)
    private Integer userId; // ID của User (dùng khi cập nhật)

    // Thông tin bảo mật (Bảng User)
    private String username;
    private String password; // Khi Edit, nếu để trống tức là không đổi mật khẩu
    private Integer roleId; // Chứa ID của bảng Role
    private String status; // ACTIVE hoặc INACTIVE

    // Thông tin cá nhân (Bảng Employee)
    private String fullName;
    private String email;
    private String phone;

    private Long wareHouseId;
}