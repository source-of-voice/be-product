package org.example.auth.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.auth.entities.RoleName;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ListUserResponse {
    private Long id;
    private String email;
    private Set<RoleName> roles;
}
