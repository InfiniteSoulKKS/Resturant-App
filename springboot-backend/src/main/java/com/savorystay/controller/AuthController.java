package com.savorystay.controller;

import com.savorystay.dto.LoginRequest;
import com.savorystay.dto.LoginWithOtpRequest;
import com.savorystay.dto.RegisterRequest;
import com.savorystay.dto.UserResponse;
import com.savorystay.entity.User;
import com.savorystay.repository.UserRepository;
import com.savorystay.security.AuthContext;
import com.savorystay.security.JwtTokenProvider;
import com.savorystay.service.CustomerRestaurantService;
import com.savorystay.service.OtpService;
import com.savorystay.service.AuthRateLimitService;
import com.savorystay.entity.OtpRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final OtpService otpService;
    private final AuthRateLimitService rateLimitService;
    private final AuthContext authContext;
    private final CustomerRestaurantService customerRestaurantService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody RegisterRequest request) {
        String username = request.username();
        String email = request.email();
        String password = request.password();
        String phone = request.phone();

        // First-time registration must be OTP-verified ("registration via OTP" requirement).
        String otpCode = request.otpCode();
        String otpChannelStr = request.otpChannel();
        String verifyId = (otpChannelStr != null && otpChannelStr.equalsIgnoreCase("EMAIL")) ? email : phone;
        if (otpCode == null || otpCode.isBlank() || otpChannelStr == null || otpChannelStr.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "OTP verification is required before registration."));
        }
        OtpRequest.OtpChannel channel;
        try {
            channel = OtpRequest.OtpChannel.valueOf(otpChannelStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid OTP channel"));
        }
        boolean otpOk = otpService.verifyOtp(verifyId, otpCode, channel)          // direct API callers
                || otpService.hasRecentVerifiedOtp(verifyId, channel);            // UI flow (code already verified)
        if (!otpOk) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired OTP. Please verify your contact before registering."));
        }

        if (userRepository.existsByUsername(username)) {
            return ResponseEntity.badRequest().body(Map.of("message", "This username is already taken. Please choose another one or sign in."));
        }

        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Map.of("message", "This email is already registered. Please sign in instead."));
        }

        // Normalize the phone to digits at registration so any formatting the user
        // types later (with/without country code, spaces, dashes) matches the stored
        // value — otherwise a strict-but-correct login gate could reject them.
        String normalizedPhone = phone != null && !phone.isBlank() ? digitsOnly(phone) : null;
        if (normalizedPhone != null && userRepository.findByPhone(normalizedPhone).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "This phone number is already registered. Please sign in instead."));
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .phone(normalizedPhone)
                .passwordHash(passwordEncoder.encode(password))
                .role("ROLE_CUSTOMER")
                .build();

        userRepository.save(user);

        String jwt = tokenProvider.generateToken(user.getUsername(), user.getRole(), user.getId(), user.getRestaurantId());

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("user", UserResponse.from(user));

        return ResponseEntity.ok(response);
    }

    /**
     * Pre-registration availability check — used by the sign-up form to warn the
     * user before they spend an OTP on an email/username/phone that is already
     * taken. Any/all of username, email, phone may be supplied as query params;
     * omitted fields are not checked. This is advisory only — /register remains
     * authoritative and re-checks duplicates atomically at submit time.
     */
    @GetMapping("/check-availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        Map<String, Object> response = new HashMap<>();
        response.put("usernameTaken", username != null && !username.isBlank() && userRepository.existsByUsername(username.trim()));
        response.put("emailTaken", email != null && !email.isBlank() && userRepository.existsByEmail(email.trim()));
        response.put("phoneTaken", phone != null && !phone.isBlank() && userRepository.findByPhone(phone.trim()).isPresent());
        return ResponseEntity.ok(response);
    }

    /** Strip everything that is not a digit so formats like "+91 99990 00002" match "+919999000002". */
    private static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    /**
     * Phone comparison tolerant of formatting/country-code differences. The typed
     * number must be the registered number exactly, or a genuine local form of it
     * (registered WITH country code ending in the typed local number). Comparing
     * only trailing digits would let a different country-code variant with the
     * same last 10 digits pass and then log in as the account.
     */
    private static boolean phonesMatch(String registered, String typed) {
        String r = digitsOnly(registered);
        String t = digitsOnly(typed);
        if (r.isEmpty() || t.isEmpty()) return false;
        return r.equals(t) || (t.length() >= 10 && r.length() > t.length() && r.endsWith(t));
    }

    /**
     * Returns the authenticated user's profile (from the JWT in the request).
     * Lets the frontend restore the full session on page reload so the UI and
     * the checkout gate agree on whether the user is signed in.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        try {
            String userId = authContext.userId(request);
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
            }
            return userRepository.findById(userId)
                    .<ResponseEntity<?>>map(user -> ResponseEntity.ok(Map.of("user", UserResponse.from(user))))
                    .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message", "User not found")));
        } catch (Exception e) {
            // Malformed / unverifiable token — treat as not authenticated.
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        String username = request.username();
        String password = request.password();
        String ip = AuthRateLimitService.clientIp(httpRequest);

        // Account lockout check (by username + IP) before even attempting a match.
        rateLimitService.throwIfLocked("user:" + username, "ip:" + ip);

        // The login form says "Username or Email" — accept both. Email matches are
        // case-insensitive-friendly (lowercased comparison); username stays exact.
        String identifier = username != null ? username.trim() : "";
        Optional<User> userOpt = userRepository.findByUsername(identifier);
        if (userOpt.isEmpty() && identifier.contains("@")) {
            userOpt = userRepository.findByEmail(identifier);
        }
        if (userOpt.isEmpty()) {
            rateLimitService.recordFailure("user:" + identifier, "ip:" + ip);
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        User user = userOpt.get();
        if (Boolean.FALSE.equals(user.getEnabled())) {
            return ResponseEntity.status(403).body(Map.of("message", "Account is disabled. Please contact your administrator."));
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            rateLimitService.recordFailure("user:" + identifier, "ip:" + ip);
            return ResponseEntity.status(401).body(Map.of("message", "Invalid username or password"));
        }

        // Success — clear any accumulated failures/lockout for this account + IP.
        rateLimitService.recordSuccess("user:" + identifier, "ip:" + ip);

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String jwt = tokenProvider.generateToken(user.getUsername(), user.getRole(), user.getId(), user.getRestaurantId());

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("user", UserResponse.from(user));

        return ResponseEntity.ok(response);
    }

    /**
     * Login with OTP verification
     * Step 1: Send OTP via Email/SMS/WhatsApp (use /api/v1/auth/otp/send/* endpoints)
     * Step 2: Call this endpoint with username and verified OTP
     * Request body: { "username": "user", "otpCode": "123456", "channel": "EMAIL|SMS|WHATSAPP" }
     */
    /**
     * After a customer logs in, they may have memberships in multiple restaurants.
     * This endpoint issues a new JWT scoped to the selected restaurant.
     * The customer must be a member of the restaurant (joined via /customer-restaurants/join).
     */
    @PostMapping("/select-restaurant")
    public ResponseEntity<?> selectRestaurant(@RequestBody Map<String, String> body,
                                              HttpServletRequest httpRequest) {
        String userId = authContext.userId(httpRequest);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Not authenticated"));
        }

        String restaurantId = body.get("restaurantId");
        if (restaurantId == null || restaurantId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "restaurantId is required"));
        }

        // Verify the user is a member of this restaurant
        if (!customerRestaurantService.isMember(userId, restaurantId)) {
            return ResponseEntity.status(403).body(Map.of(
                    "success", false,
                    "message", "You are not a member of this restaurant. Please join it first."
            ));
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
        }

        User user = userOpt.get();
        // Issue a new JWT scoped to this restaurant
        String jwt = tokenProvider.generateToken(user.getUsername(), user.getRole(), user.getId(), restaurantId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token", jwt);
        response.put("user", UserResponse.from(user));
        response.put("selectedRestaurantId", restaurantId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login-with-otp")
    public ResponseEntity<?> loginWithOtp(@Valid @RequestBody LoginWithOtpRequest request,
                                          HttpServletRequest httpRequest) {
        String username = request.username();
        String ip = AuthRateLimitService.clientIp(httpRequest);

        // Account lockout check before doing any work (propagates to 429 via GlobalExceptionHandler).
        rateLimitService.throwIfLocked("user:" + username, "ip:" + ip);

        try {
            String otpCode = request.otpCode();
            String channelStr = request.channel();

            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Username is required"));
            }
            if (otpCode == null || otpCode.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "OTP code is required"));
            }
            if (channelStr == null || channelStr.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Channel (EMAIL, SMS, WHATSAPP) is required"));
            }

            OtpRequest.OtpChannel channel;
            try {
                channel = OtpRequest.OtpChannel.valueOf(channelStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid channel. Use EMAIL, SMS, or WHATSAPP"));
            }

            String identifier = username != null ? username.trim() : "";
            Optional<User> userOpt = userRepository.findByUsername(identifier);
            if (userOpt.isEmpty() && identifier.contains("@")) {
                userOpt = userRepository.findByEmail(identifier);
            }
            if (userOpt.isEmpty()) {
                rateLimitService.recordFailure("user:" + identifier, "ip:" + ip);
                return ResponseEntity.status(401).body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();
            if (Boolean.FALSE.equals(user.getEnabled())) {
                return ResponseEntity.status(403).body(Map.of("message", "Account is disabled. Please contact your administrator."));
            }

            // Verify the OTP against the account's REGISTERED contact for the chosen
            // channel (email for EMAIL, phone for SMS/WHATSAPP) AND require the code
            // to have been issued for LOGIN (sent with a validated username). A
            // registration-purpose OTP — e.g. one sent to the same address without
            // a username — can never be used to log in.
            String registeredContact = channel == OtpRequest.OtpChannel.EMAIL
                    ? user.getEmail()
                    : user.getPhone();
            List<String> candidates = new java.util.ArrayList<>();
            if (registeredContact != null && !registeredContact.isBlank()) {
                candidates.add(registeredContact);
            }
            boolean verified = otpService.verifyLoginOtp(candidates, otpCode, channel);

            if (!verified) {
                rateLimitService.recordFailure("user:" + username, "ip:" + ip);
                return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired OTP"));
            }

            // Success — clear lockout state.
            rateLimitService.recordSuccess("user:" + username, "ip:" + ip);

            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            String jwt = tokenProvider.generateToken(user.getUsername(), user.getRole(), user.getId(), user.getRestaurantId());

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwt);
            response.put("user", UserResponse.from(user));
            response.put("message", "Login successful with OTP verification");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in OTP login: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("message", "Login failed: " + e.getMessage()));
        }
    }
}
