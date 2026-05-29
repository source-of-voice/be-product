package org.example.auth.services;

import org.example.auth.DTO.request.*;
import org.example.auth.DTO.response.AuthResponse;
import org.example.auth.entities.RefreshToken;
import org.example.auth.entities.Role;
import org.example.auth.entities.RoleName;
import org.example.auth.entities.User;
import org.example.auth.helpers.BCrypt;
import org.example.auth.helpers.CheckInput;
import org.example.auth.helpers.JwtUtil;
import org.example.auth.helpers.RefreshTokenHelper;
import org.example.auth.repositories.RefreshTokenRepository;
import org.example.auth.repositories.RoleRepository;
import org.example.auth.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.example.auth.helpers.RefreshTokenHelper.newOpaqueToken;
import static org.example.auth.helpers.RefreshTokenHelper.sha256Base64Url;

@Service
public class AuthService {
    private final CheckInput checkInput;
    private final UserRepository userRepository;
    private final BCrypt bCrypt;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenRepository refreshTokenRepository;


    @Value("${refresh.duration}")
    private long refreshDuration;

    public AuthService(CheckInput checkInput, UserRepository userRepository, BCrypt bCrypt, RoleRepository roleRepository, JwtUtil jwtUtil, StringRedisTemplate redisTemplate, RefreshTokenRepository refreshTokenRepository, RefreshTokenHelper refreshTokenHelper) {
        this.checkInput = checkInput;
        this.userRepository = userRepository;
        this.bCrypt = bCrypt;
        this.roleRepository = roleRepository;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.refreshTokenRepository = refreshTokenRepository;

    }

    public String register(RegisterRequest request) {

        String email = request.getEmail();
        String password = request.getPassword();

        if (email.isBlank()) {
            throw new IllegalArgumentException("Invalid input data");
        }

        if (!checkInput.isEmailValid(email)) {
            throw new IllegalArgumentException("Siema");
        }

        if (!checkInput.isPasswordStrong(password)) {
            throw new IllegalArgumentException("Password does not meet requirements");
        }

        if (password.isBlank()) {
            throw new IllegalArgumentException("Invalid input data");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Unable to complete registration");
        }

        String hashedPassword = bCrypt.hashPassword(password);
        String username = email.substring(0, email.indexOf('@'));

        User user = new User();
        user.setEmail(email);
        user.setPassword(hashedPassword);
        user.setUsername(username);

        Role role = roleRepository.findRoleByName(RoleName.USER).orElseThrow(() -> new IllegalArgumentException("Unable to complete registration"));

        user.getRoles().add(role);

        userRepository.save(user);

        return "Registered";
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail();
        String password = request.getPassword();

        if (email.isBlank()) {
            throw new IllegalArgumentException("Invalid input data");
        }

        if (password.isBlank()) {
            throw new IllegalArgumentException("Invalid input data");
        }

        User user = userRepository.findByEmailWithRoles(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));


        if(!bCrypt.checkPassword(password, user.getPassword())){
            throw new IllegalArgumentException("Invalid email or password");
        }

        List<String> roles = user.getRoles().stream().map(role -> role.getName().name()).toList();

        String accessToken = jwtUtil.generateToken(user.getId(),roles,user.getTokenVersion());

        String rawRefresh = newOpaqueToken(64);
        String hashedRefresh = sha256Base64Url(rawRefresh);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hashedRefresh);
        refreshToken.setUser(user);
        refreshToken.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshDuration)));

        refreshTokenRepository.save(refreshToken);

        redisTemplate.opsForValue().set(
                "usr:ver:" + user.getId(),
                String.valueOf(user.getTokenVersion()),
                Duration.ofDays(1)
        );

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(rawRefresh);

        return response;
    }

    public AuthResponse refresh(RefreshRequest request){
        String rawRefresh = request.getRefreshToken();

        if(rawRefresh.isEmpty()){
            throw new IllegalArgumentException("Invalid or expired refresh token");
        }

        String hashToken = sha256Base64Url(rawRefresh);

        RefreshToken currentToken = refreshTokenRepository.findActiveByHash(hashToken,Instant.now())
                .orElseThrow(
                        () -> new IllegalArgumentException("Invalid or expired refresh token"));

        Long userId = currentToken.getUser().getId();
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(
                        () -> new IllegalArgumentException("Invalid or expired refresh token"));


        currentToken.setRevoked(true);
        currentToken.setLastUsedAt(Instant.now());
        refreshTokenRepository.save(currentToken);

        String newRaw = newOpaqueToken(64);
        String newHash = sha256Base64Url(newRaw);

        RefreshToken next = new RefreshToken();
        next.setUser(user);
        next.setTokenHash(newHash);
        next.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshDuration)));
        refreshTokenRepository.save(next);

        List<String> roles = user.getRoles().stream().map(r -> r.getName().name()).toList();
        String newAccess = jwtUtil.generateToken(user.getId(), roles, user.getTokenVersion());

        redisTemplate.opsForValue().set("usr:ver:" + user.getId(), String.valueOf(user.getTokenVersion()));

        AuthResponse response = new AuthResponse();
        response.setAccessToken(newAccess);
        response.setRefreshToken(newRaw);

        return response;
    }

    public String logout(Long userId){
        User user = userRepository.findByIdWithRoles(userId).orElseThrow(() -> new IllegalArgumentException("Operation could not be completed"));

        user.incrementTokenVersion();

        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        redisTemplate.opsForValue().set("usr:ver:" + user.getId(), String.valueOf(user.getTokenVersion()), Duration.ofDays(1));

        return "Log out";
    }

    public String changeEmail(EmailChangeRequest request){
        Long userId = request.getUserId();
        String email = request.getEmail();

        User user = userRepository.findUsersById(userId).orElseThrow(() -> new IllegalArgumentException("Operation could not be completed"));

        if(!checkInput.isEmailValid(email)){
            throw new IllegalArgumentException("Invalid email data");
        }

        if(userRepository.existsByEmail(email)){
            throw new IllegalArgumentException("Unable to change email");
        }


        user.incrementTokenVersion();

        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        redisTemplate.opsForValue().set("usr:ver:" + user.getId(), String.valueOf(user.getTokenVersion()), Duration.ofDays(1));


        user.setEmail(email);
        userRepository.save(user);

        return "Email changed";
    }

    public String changePassword(ChangePasswordRequest request){
        Long userId = request.getUserId();
        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();

        User user = userRepository.findUsersById(userId).orElseThrow(() -> new IllegalArgumentException("Operation could not be completed"));

        if(!bCrypt.checkPassword(oldPassword ,user.getPassword())){
           throw  new IllegalArgumentException("Unable to change password");
        }

        if(!checkInput.isPasswordStrong(newPassword)){
            throw  new IllegalArgumentException("Password does not meet requirements");

        }

        if(newPassword.equals(oldPassword)){
            throw  new IllegalArgumentException("Password does not meet requirements");
        }

        user.incrementTokenVersion();

        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        redisTemplate.opsForValue().set("usr:ver:" + user.getId(), String.valueOf(user.getTokenVersion()), Duration.ofDays(1));

        user.setPassword(bCrypt.hashPassword(newPassword));
        userRepository.save(user);

        return "Changing password...";
    }

    public String changeUsername(ChangeUsernameRequest request){
        Long userId = request.getUserId();
        String username = request.getUsername();

        User user = userRepository.findUsersById(userId).orElseThrow(() -> new IllegalArgumentException("Operation could not be completed"));
        if(userRepository.existsByUsername(username)){
            throw new IllegalArgumentException("Unable to change username");
        }
        if(username.isBlank()){
            throw new IllegalArgumentException("Invalid username data");
        }

        user.setUsername(username);
        userRepository.save(user);

        return "Changed username";
    }

    public long getCurrentTokenVersion(Long userId) {
        User user = userRepository.findUsersById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        redisTemplate.opsForValue().set(
                "usr:ver:" + userId,
                String.valueOf(user.getTokenVersion()),
                Duration.ofDays(1)
        );

        return user.getTokenVersion();
    }
}
