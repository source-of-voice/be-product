package org.example.auth.controllers;

import org.example.auth.DTO.request.AssignRoleRequest;
import org.example.auth.DTO.request.RevokeRoleRequest;
import org.example.auth.DTO.response.ListUserResponse;
import org.example.auth.services.AdminAuthService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/admin")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService){
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/assign/role")
    public String assignRole(@RequestBody AssignRoleRequest request){
        return adminAuthService.assignRole(request);
    }

    @PostMapping("/revoke/role")
    public String revokeRole(@RequestBody RevokeRoleRequest request){
        return adminAuthService.revokeRole(request);
    }

    @GetMapping("/users")
    public Page<ListUserResponse> getUsersWithRoles(
            @PageableDefault(size = 10, sort = "email") Pageable pageable
    ) {
        return adminAuthService.getUsersWithRoles(pageable);
    }
}
