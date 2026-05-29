package org.example.auth.services;

import org.example.auth.DTO.request.LoginRequest;
import org.example.auth.DTO.request.RegisterRequest;
import org.example.auth.DTO.response.AuthResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private CheckInput checkInput;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCrypt bCrypt;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenHelper refreshTokenHelper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AuthService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        service = new AuthService(
                checkInput,
                userRepository,
                bCrypt,
                roleRepository,
                jwtUtil,
                redisTemplate,
                refreshTokenRepository,
                refreshTokenHelper
        );

        ReflectionTestUtils.setField(service, "refreshDuration", 7L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void register_shouldHashPasswordAndAssignUserRole() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("StrongPassword123!");

        Role userRole = new Role();
        userRole.setName(RoleName.USER);

        when(checkInput.isEmailValid("user@example.com")).thenReturn(true);
        when(checkInput.isPasswordStrong("StrongPassword123!")).thenReturn(true);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(bCrypt.hashPassword("StrongPassword123!")).thenReturn("hashed-password");
        when(roleRepository.findRoleByName(RoleName.USER)).thenReturn(Optional.of(userRole));

        String result = service.register(request);

        assertEquals("Registered", result);

        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("user@example.com")
                        && user.getUsername().equals("user")
                        && user.getPassword().equals("hashed-password")
                        && user.getRoles().contains(userRole)
        ));
    }

    @Test
    void register_shouldRejectWeakPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("123");

        when(checkInput.isEmailValid("user@example.com")).thenReturn(true);
        when(checkInput.isPasswordStrong("123")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.register(request)
        );

        assertEquals("Password does not meet requirements", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldRejectExistingEmailWithoutDetailedMessage() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@example.com");
        request.setPassword("StrongPassword123!");

        when(checkInput.isEmailValid("user@example.com")).thenReturn(true);
        when(checkInput.isPasswordStrong("StrongPassword123!")).thenReturn(true);
        when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.register(request)
        );

        assertEquals("Unable to complete registration", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_shouldReturnAccessAndRefreshToken_whenCredentialsAreValid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("StrongPassword123!");

        Role role = new Role();
        role.setName(RoleName.USER);

        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPassword("hashed-password");
        user.setTokenVersion(0);
        user.setRoles(new HashSet<>());
        user.getRoles().add(role);

        when(userRepository.findByEmailWithRoles("user@example.com"))
                .thenReturn(Optional.of(user));

        when(bCrypt.checkPassword("StrongPassword123!", "hashed-password"))
                .thenReturn(true);

        when(jwtUtil.generateToken(eq(1L), anyList(), eq(0)))
                .thenReturn("access-token");

        AuthResponse response = service.login(request);

        assertEquals("access-token", response.getAccessToken());
        assertNotNull(response.getRefreshToken());

        verify(refreshTokenRepository).save(any());
        verify(valueOperations).set(eq("usr:ver:1"), eq("0"), any());
    }

    @Test
    void login_shouldRejectInvalidPasswordWithGenericMessage() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("WrongPassword");

        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPassword("hashed-password");

        when(userRepository.findByEmailWithRoles("user@example.com"))
                .thenReturn(Optional.of(user));

        when(bCrypt.checkPassword("WrongPassword", "hashed-password"))
                .thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());

        verify(refreshTokenRepository, never()).save(any());
        verify(jwtUtil, never()).generateToken(anyLong(), anyList(), anyInt());
    }

    @Test
    void login_shouldRejectUnknownEmailWithGenericMessage() {
        LoginRequest request = new LoginRequest();
        request.setEmail("unknown@example.com");
        request.setPassword("StrongPassword123!");

        when(userRepository.findByEmailWithRoles("unknown@example.com"))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());
    }

    @Test
    void login_shouldRejectSqlInjectionLikeEmail() {
        LoginRequest request = new LoginRequest();
        request.setEmail("' OR '1'='1");
        request.setPassword("anything");

        when(userRepository.findByEmailWithRoles("' OR '1'='1"))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(request)
        );

        assertEquals("Invalid email or password", exception.getMessage());

        verify(bCrypt, never()).checkPassword(anyString(), anyString());
        verify(jwtUtil, never()).generateToken(anyLong(), anyList(), anyInt());
        verify(refreshTokenRepository, never()).save(any());
    }
}