package com.doceditor.authentication.services;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

import org.springframework.stereotype.Component;

@Component
public class EncryptionService {
    KeyPair kp;

    public EncryptionService() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            kp = kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA algorithm not available", e);
        }
    }

    public PrivateKey getPrivateKey() {
        return kp.getPrivate();
    }

    public PublicKey getPublicKey() {
        return kp.getPublic();
    }
}
