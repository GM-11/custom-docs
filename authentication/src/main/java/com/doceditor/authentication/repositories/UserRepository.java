package com.doceditor.authentication.repositories;

import java.util.Optional;
import java.util.UUID;

import com.doceditor.authentication.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

}
