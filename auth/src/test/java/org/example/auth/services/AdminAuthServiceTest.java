package org.example.auth.services;

import org.example.auth.DTO.request.AssignRoleRequest;
import org.example.auth.DTO.request.RevokeRoleRequest;
import org.example.auth.entities.Role;
import org.example.auth.entities.RoleName;
import org.example.auth.entities.User;
import org.example.auth.repositories.RefreshTokenRepository;
import org.example.auth.repositories.RoleRepository;
import org.example.auth.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private AdminAuthService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new AdminAuthService(
                userRepository,
                roleRepository,
                refreshTokenRepository,
                redisTemplate
        );
    }

    @Test
    void assignRole_shouldAddRoleIncrementTokenVersionAndRevokeSessions() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .username("user")
                .password("hashed")
                .tokenVersion(0)
                .roles(new HashSet<>())
                .build();

        Role reviewerRole = Role.builder()
                .id(2L)
                .name(RoleName.REVIEWER)
                .build();

        AssignRoleRequest request = new AssignRoleRequest();
        request.setUserId(1L);
        request.setRoleName(RoleName.REVIEWER);

        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findRoleByName(RoleName.REVIEWER)).thenReturn(Optional.of(reviewerRole));

        String result = service.assignRole(request);

        assertEquals("Role assigned", result);
        assertTrue(user.getRoles().contains(reviewerRole));
        assertEquals(1, user.getTokenVersion());

        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUserId(eq(1L), any(Instant.class));
        verify(valueOperations).set("usr:ver:1", "1");
    }

    @Test
    void assignRole_shouldNotRevokeSessions_whenRoleAlreadyExists() {
        Role reviewerRole = Role.builder()
                .id(2L)
                .name(RoleName.REVIEWER)
                .build();

        HashSet<Role> roles = new HashSet<>();
        roles.add(reviewerRole);

        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .username("user")
                .password("hashed")
                .tokenVersion(0)
                .roles(roles)
                .build();

        AssignRoleRequest request = new AssignRoleRequest();
        request.setUserId(1L);
        request.setRoleName(RoleName.REVIEWER);

        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findRoleByName(RoleName.REVIEWER)).thenReturn(Optional.of(reviewerRole));

        String result = service.assignRole(request);

        assertEquals("Role assigned", result);
        assertEquals(0, user.getTokenVersion());

        verify(userRepository, never()).save(any());
        verify(refreshTokenRepository, never()).revokeAllByUserId(anyLong(), any());
        verify(valueOperations, never()).set(anyString(), anyString());
    }

    @Test
    void revokeRole_shouldRemoveRoleIncrementTokenVersionAndRevokeSessions() {
        Role reviewerRole = Role.builder()
                .id(2L)
                .name(RoleName.REVIEWER)
                .build();

        HashSet<Role> roles = new HashSet<>();
        roles.add(reviewerRole);

        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .username("user")
                .password("hashed")
                .tokenVersion(0)
                .roles(roles)
                .build();

        RevokeRoleRequest request = new RevokeRoleRequest();
        request.setUserId(1L);
        request.setRoleName(RoleName.REVIEWER);

        when(userRepository.findByIdWithRoles(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findRoleByName(RoleName.REVIEWER)).thenReturn(Optional.of(reviewerRole));

        String result = service.revokeRole(request);

        assertEquals("Role removed", result);
        assertFalse(user.getRoles().contains(reviewerRole));
        assertEquals(1, user.getTokenVersion());

        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeAllByUserId(eq(1L), any(Instant.class));
        verify(valueOperations).set("usr:ver:1", "1");
    }

    @Test
    void assignRole_shouldRejectUnknownUser() {
        AssignRoleRequest request = new AssignRoleRequest();
        request.setUserId(999L);
        request.setRoleName(RoleName.ADMIN);

        when(userRepository.findByIdWithRoles(999L)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.assignRole(request)
        );

        assertEquals("Unable to update user role", exception.getMessage());
        verify(userRepository, never()).save(any());
    }
}
