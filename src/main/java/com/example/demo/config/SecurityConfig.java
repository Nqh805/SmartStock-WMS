package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        // Hash mật khẩu 1 chiều bằng BCrypt
        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        // config phân quyền
        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(csrf -> csrf.disable())
                                .authorizeHttpRequests(auth -> auth
                                                // 1. Giao diện tĩnh & Luồng xác thực
                                                .requestMatchers("/css/**", "/js/**", "/asset/**", "/login",
                                                                "/reset_password/**", "/forgot_password/**")
                                                .permitAll()

                                                // 2. LUỒNG CHỈ DÀNH RIÊNG CHO THỦ KHO (MANAGER)
                                                .requestMatchers("/purchases/approve/**").hasRole("MANAGER")
                                                .requestMatchers("/accounts/manage", "/accounts/add",
                                                                "/accounts/edit/**", "/accounts/update",
                                                                "/accounts/lock/**")
                                                .hasRole("MANAGER")

                                                // 3. LUỒNG DÀNH CHO THỦ KHO (MANAGER) VÀ KIỂM KHO (INSPECTOR)
                                                .requestMatchers("/purchases/add", "/purchases/pay/**")
                                                .hasAnyRole("MANAGER", "INSPECTOR")
                                                .requestMatchers("/suppliers/add", "/suppliers/delete/**")
                                                .hasAnyRole("MANAGER", "INSPECTOR")

                                                // 4. LUỒNG BÁN HÀNG VÀ CÁC ĐƯỜNG DẪN CÒN LẠI
                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .loginProcessingUrl("/process_login")
                                                .defaultSuccessUrl("/dashboard", true)
                                                .failureUrl("/login?error=true")
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessUrl("/login?logout=true")
                                                .permitAll());

                return http.build();
        }
}