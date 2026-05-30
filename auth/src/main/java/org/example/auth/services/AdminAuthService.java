package org.example.auth.services;

import org.example.auth.DTO.request.AssignRoleRequest;
import org.example.auth.DTO.request.RegisterRequest;
import org.example.auth.DTO.request.RevokeRoleRequest;
import org.example.auth.DTO.response.AuthResponse;
import org.example.auth.DTO.response.ListUserResponse;
import org.example.auth.entities.Role;
import org.example.auth.entities.RoleName;
import org.example.auth.entities.User;
import org.example.auth.repositories.RefreshTokenRepository;
import org.example.auth.repositories.RoleRepository;
import org.example.auth.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminAuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final StringRedisTemplate redisTemplate;

    public AdminAuthService(UserRepository userRepository, RoleRepository roleRepository, RefreshTokenRepository refreshTokenRepository, StringRedisTemplate redisTemplate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.redisTemplate = redisTemplate;
    }

    public String assignRole(AssignRoleRequest request){
        Long userId = request.getUserId();
        RoleName roleName = request.getRoleName();

        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(()->new IllegalArgumentException("Unable to update user role"));

        Role role = roleRepository.findRoleByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unable to update user role: " + roleName));

        boolean added = user.getRoles().add(role);
        if (added) {
            user.incrementTokenVersion();
            userRepository.save(user);

            refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
            redisTemplate.opsForValue().set("usr:ver:" + user.getId(), String.valueOf(user.getTokenVersion()));
        }

        return "Role assigned";
    }

    public String revokeRole(RevokeRoleRequest request){
        Long userId = request.getUserId();
        RoleName roleName = request.getRoleName();

        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(()->new IllegalArgumentException("Unable to update user role"));

        Role role = roleRepository.findRoleByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unable to update user role: " + roleName));

        boolean removed = user.getRoles().remove(role);
        if (removed) {
            user.incrementTokenVersion();
            userRepository.save(user);

            refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
            redisTemplate.opsForValue().set("usr:ver:" + user.getId(), String.valueOf(user.getTokenVersion()));
        }

        return "Role removed";
    }


    public Page<ListUserResponse> getUsersWithRoles(Pageable pageable) {
        Page<Long> userIdsPage = userRepository.findUserIds(pageable);

        if (userIdsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        List<Long> ids = userIdsPage.getContent();

        List<User> users = userRepository.findAllWithRolesByIdIn(ids);

        Map<Long, User> usersById = users.stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        List<ListUserResponse> content = ids.stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .map(this::mapToAdminUserResponse)
                .collect(Collectors.toList());

        return new PageImpl<>(
                content,
                pageable,
                userIdsPage.getTotalElements()
        );
    }

    private ListUserResponse mapToAdminUserResponse(User user) {
        return new ListUserResponse(
                user.getId(),
                user.getEmail(),
                user.getRoles()
                        .stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet())
        );
    }

}
