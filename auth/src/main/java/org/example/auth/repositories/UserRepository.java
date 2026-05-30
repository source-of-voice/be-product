package org.example.auth.repositories;

import org.example.auth.entities.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);
    @Query("""
        select u from User u
        left join fetch u.roles
        where u.email = :email
    """)
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    @Query("select u from User u left join fetch u.roles where u.id = :id")
    Optional<User> findByIdWithRoles(@Param("id") Long id);
    Optional<User> findUsersById(Long id);

    @Query(
            value = "select u.id from User u",
            countQuery = "select count(u.id) from User u"
    )
    Page<Long> findUserIds(Pageable pageable);

    @Query("select distinct u from User u left join fetch u.roles where u.id in :ids")
    List<User> findAllWithRolesByIdIn(@Param("ids") Collection<Long> ids);



}
