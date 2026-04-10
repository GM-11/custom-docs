package com.doceditor.authentication.repositories;

import java.util.Optional;
import java.util.UUID;

import com.doceditor.authentication.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    @Query(value = "SELECT id FROM users WHERE email = :email", nativeQuery = true)
    Optional<String> findUserIdByEmail(@Param("email") String email);

}
