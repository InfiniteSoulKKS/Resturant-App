package com.savorystay.controller;

import com.savorystay.entity.User;
import com.savorystay.repository.UserRepository;
import com.savorystay.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    // Concurrent in-memory OTP storage for Spring Boot
    private static class OtpRecord {
        String code;
        long expiresAt;
        boolean verified;

        OtpRecord(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
            this.verified = false;
        }
    }

    private final Map<String, OtpRecord> otpStorage = new ConcurrentHashMap<>();

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOtp(@RequestBody Map<String, String> request) {
        String phoneOrEmail = request.get("phoneOrEmail");
        if (phoneOrEmail == null || phoneOrEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mobile number or email address is required."));
        }

        String generatedOtp = String.valueOf((int) (100000 + Math.random() * 900000));
        long expiresAt = System.currentTimeMillis() + (10 * 60 * 1000); // 10 mins

        otpStorage.put(phoneOrEmail, new OtpRecord(generatedOtp, expiresAt));

        System.out.println("[SPRING BOOT OTP SERVICE] 📱 Verification Code dispatched to " + phoneOrEmail + ": " + generatedOtp);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "6-Digit OTP verification code sent to " + phoneOrEmail + " via SMS.");
        response.put("demoOtp", generatedOtp);
        response.put("expiresInSeconds", 600);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verifyOtp(@RequestBody Map<String, String> request) {
        String phoneOrEmail = request.get("phoneOrEmail");
        String otp = request.get("otp");

        if (phoneOrEmail == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone/Email and 6-digit OTP code are required."));
        }

        OtpRecord record = otpStorage.get(phoneOrEmail);
        if (record == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "No OTP requested for " + phoneOrEmail + ". Click 'Send OTP'."));
        }

        if (System.currentTimeMillis() > record.expiresAt) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP expired. Please request a new OTP."));
        }

        if (!record.code.equals(otp)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid 6-digit OTP code."));
        }

        record.verified = true;

        return ResponseEntity.ok(Map.of(
            "success", true,
            "verified", true,
            "message", "OTP verified successfully!"
        ));
    }

    @PostMapping("/login-otp")
    public ResponseEntity<?> loginOtp(@RequestBody Map<String, String> request) {
        String phoneOrEmail = request.get("phoneOrEmail");
        String otp = request.get("otp");

        if (phoneOrEmail == null || otp == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Phone/Email and 6-digit OTP code are required."));
        }

        OtpRecord record = otpStorage.get(phoneOrEmail);
        if (record == null || (!record.verified && !record.code.equals(otp))) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP verification failed. Please enter valid code."));
        }

        if (System.currentTimeMillis() > record.expiresAt) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP expired. Please request a new OTP."));
        }

        // Find or create user
        Optional<User> userOpt = userRepository.findByEmail(phoneOrEmail)
            .or(() -> userRepository.findByPhone(phoneOrEmail))
            .or(() -> userRepository.findByUsername(phoneOrEmail));

        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
        } else {
            String generatedUsername = "guest_" + UUID.randomUUID().toString().substring(0, 8);
            user = User.builder()
                .username(generatedUsername)
                .email(phoneOrEmail.contains("@") ? phoneOrEmail : generatedUsername + "@savorystay.com")
                .phone(phoneOrEmail.contains("@") ? null : phoneOrEmail)
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .role("ROLE_CUSTOMER")
                .enabled(true)
                .build();
            userRepository.save(user);
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String jwt = tokenProvider.generateToken(user.getUsername(), user.getRole(), user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("refreshToken", jwt);
        response.put("user", user);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String email = request.get("email");
        String phone = request.get("phone");
        String password = request.get("password");
        String role = request.get("role");

        if (username == null || email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username, email, and password are required!"));
        }

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username is already taken!"));
        }

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Email address is already in use!"));
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(password))
                .role(role != null ? role : "ROLE_CUSTOMER")
                .enabled(true)
                .build();

        userRepository.save(user);

        String jwt = tokenProvider.generateToken(user.getUsername(), user.getRole(), user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("refreshToken", jwt);
        response.put("user", user);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, String> request) {
        String identifier = request.get("emailOrUsername");
        if (identifier == null) identifier = request.get("username");
        String password = request.get("password");

        if (identifier == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username/email and password are required"));
        }

        Optional<User> userOpt = userRepository.findByUsername(identifier)
            .or(() -> userRepository.findByEmail(identifier));

        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String jwt = tokenProvider.generateToken(user.getUsername(), user.getRole(), user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("refreshToken", jwt);
        response.put("user", user);

        return ResponseEntity.ok(response);
    }
}
