package com.lite_k8s.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * AES 암호화 키를 환경변수(KITE_ENCRYPTION_KEY)에서 로드한다.
 *
 * 키 생성 예시 (base64):
 *   openssl rand -base64 32
 *
 * 키가 설정되지 않은 경우(null/blank), converter는 평문으로 동작한다.
 * 운영 환경에서는 반드시 설정해야 한다.
 */
@Slf4j
@Component
public class CryptoKeyProvider {

    private static final String ALGORITHM = "AES";
    private static final int EXPECTED_KEY_BYTES = 32; // AES-256

    private final SecretKey key;
    private final boolean enabled;

    public CryptoKeyProvider(@Value("${KITE_ENCRYPTION_KEY:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            log.warn("KITE_ENCRYPTION_KEY 환경변수가 설정되지 않음 — SSH credential이 DB에 평문으로 저장됩니다. " +
                    "운영 환경에서는 반드시 설정하세요. 생성 예시: openssl rand -base64 32");
            this.key = null;
            this.enabled = false;
            return;
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "KITE_ENCRYPTION_KEY가 올바른 base64 형식이 아닙니다. `openssl rand -base64 32`로 생성하세요.", e);
        }

        if (bytes.length != EXPECTED_KEY_BYTES) {
            throw new IllegalStateException(
                    "KITE_ENCRYPTION_KEY는 32바이트(256bit)여야 합니다. 현재: " + bytes.length + " bytes. " +
                    "`openssl rand -base64 32`로 재생성하세요.");
        }

        this.key = new SecretKeySpec(bytes, ALGORITHM);
        this.enabled = true;
        log.info("SSH credential 암호화 활성화 (AES-256-GCM)");
    }

    public SecretKey getKey() {
        return key;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
