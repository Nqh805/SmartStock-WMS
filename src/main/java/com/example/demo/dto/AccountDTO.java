package com.example.demo.dto;

import lombok.Data;

@Data
public class AccountDTO {
    // ID dùng khi update
    private Long employeeId;
    private Integer userId;

    // Thông tin bảo mật ( User )
    private String username;
    private String password;
    private Integer roleId;
    private String status; // ACTIVE hoặc INACTIVE

    // Thông tin cá nhân ( Employee )
    private String fullName;
    private String email;
    private String phone;

    private Long wareHouseId;
}