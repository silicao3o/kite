package com.lite_k8s.controller;

import com.lite_k8s.config.SecurityProperties;
import com.lite_k8s.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/login")
    public String loginPage() {
        if (!securityProperties.isEnabled()) {
            return "redirect:/";
        }
        return "login";
    }

    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        if (!securityProperties.isEnabled()) {
            return ResponseEntity.ok(Map.of("message", "Security disabled"));
        }

        SecurityProperties.User userConfig = securityProperties.getUser();

        // 사용자 확인
        if (!userConfig.getUsername().equals(request.username())) {
            log.warn("로그인 실패: 잘못된 사용자명 - {}", request.username());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        // 비밀번호 확인
        boolean passwordMatch;
        if (userConfig.getPassword().startsWith("$2a$") || userConfig.getPassword().startsWith("$2b$")) {
            // BCrypt 해시
            passwordMatch = passwordEncoder.matches(request.password(), userConfig.getPassword());
        } else {
            // 평문 비교
            passwordMatch = userConfig.getPassword().equals(request.password());
        }

        if (!passwordMatch) {
            log.warn("로그인 실패: 잘못된 비밀번호 - {}", request.username());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }

        // JWT 생성
        String token = jwtService.generateToken(request.username());

        // 쿠키 설정
        Cookie cookie = new Cookie("jwt", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) securityProperties.getJwt().getExpirationSeconds());
        response.addCookie(cookie);

        log.info("로그인 성공: {}", request.username());
        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "token", token
        ));
    }

    @PostMapping("/api/auth/logout")
    @ResponseBody
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    public record LoginRequest(String username, String password) {}
}
