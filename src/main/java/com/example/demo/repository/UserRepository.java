package com.example.demo.repository;

import com.example.demo.entity.User.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    // Hàm này cực kỳ quan trọng để Spring Security tìm tài khoản khi đăng nhập
    Optional<User> findByUsername(String username);
}