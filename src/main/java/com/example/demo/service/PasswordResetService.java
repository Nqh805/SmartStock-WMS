package com.example.demo.service;

import com.example.demo.entity.User.Employee;
import com.example.demo.entity.User.PasswordResetToken;
import com.example.demo.repository.EmployeeRepository;
import com.example.demo.repository.PasswordResetTokenRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final EmployeeRepository employeeRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void processForgotPassword(String email, String appUrl) {
        // 1. Tìm nhân viên theo email
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản nào đăng ký với email này!"));

        // 2. Xóa Token cũ (nếu có) và tạo Token mới
        tokenRepository.deleteByUser_Id(employee.getUser().getId());

        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(employee.getUser());
        resetToken.setToken(token);
        tokenRepository.save(resetToken);

        // 3. Gửi Email chứa link reset
        String resetUrl = appUrl + "/reset_password/confirm?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("nguyenhuy130805@gmail.com");
        message.setTo(employee.getEmail());
        message.setSubject("[SmartStock] Yêu cầu đặt lại mật khẩu");
        message.setText("Chào " + employee.getFullName() + ",\n\n"
                + "Bạn đã yêu cầu đặt lại mật khẩu. Vui lòng click vào đường link bên dưới để tạo mật khẩu mới:\n"
                + resetUrl + "\n\n"
                + "Đường link này sẽ hết hạn trong vòng 15 phút.\n"
                + "Nếu bạn không yêu cầu, vui lòng bỏ qua email này.");

        mailSender.send(message);
    }

    @Transactional
    public void updatePassword(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Đường link không hợp lệ hoặc đã bị thay đổi!"));

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(resetToken);
            throw new IllegalArgumentException("Đường link đã hết hạn (quá 15 phút). Vui lòng yêu cầu lại!");
        }

        // Cập nhật mật khẩu mới
        com.example.demo.entity.User.User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Xóa token sau khi dùng xong
        tokenRepository.delete(resetToken);
    }
}