package com.doceditor.authentication.repositories;

import java.util.Optional;
import java.util.UUID;

import com.doceditor.authentication.entity.RefreshToken;
import com.doceditor.authentication.entity.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    Optional<RefreshToken> findByUser(User user);

    @Modifying
    @Transactional
    void deleteByUser(User user);

}
