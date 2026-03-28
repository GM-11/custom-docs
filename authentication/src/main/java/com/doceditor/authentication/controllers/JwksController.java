package com.doceditor.authentication.controllers;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import com.doceditor.authentication.services.EncryptionService;
import com.nimbusds.jose.jwk.RSAKey;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/.well-known")
public class JwksController {

    private final EncryptionService encryptionService;

    public JwksController(EncryptionService encryptionService) {
        this.encryptionService = encryptionService;
    }

    @GetMapping("/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        RSAPublicKey publicKey = (RSAPublicKey) encryptionService.getPublicKey();

        RSAKey jwk = new RSAKey.Builder(publicKey)
                .keyID("auth-key-1")
                .build();

        return ResponseEntity.ok(Map.of("keys", java.util.List.of(jwk.toJSONObject())));
    }
}
