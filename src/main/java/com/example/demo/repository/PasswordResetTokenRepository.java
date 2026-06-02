package com.example.demo.repository;

import com.example.demo.entity.User.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUser_Id(Integer userId); // Xóa token cũ nếu tạo token mới
}