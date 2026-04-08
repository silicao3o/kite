package com.lite_k8s.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter: 엔티티 문자열 필드를 AES-256-GCM으로 암호화하여 DB에 저장한다.
 *
 * 저장 형식: "enc:v1:{base64(IV || ciphertext || tag)}"
 *
 * 특징:
 *  - IV는 12바이트(GCM 표준) 랜덤 생성 → 같은 평문이라도 매번 다른 암호문
 *  - 인증 태그 16바이트로 변조 감지
 *  - 레거시 평문 값은 그대로 로드 (마이그레이션 호환성)
 *  - 이미 "enc:v1:" prefix가 있으면 재암호화하지 않음 (멱등성)
 *  - CryptoKeyProvider가 비활성화된 경우 평문으로 pass-through (dev 환경)
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final CryptoKeyProvider keyProvider;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public EncryptedStringConverter(@Lazy CryptoKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        if (attribute.isEmpty()) return attribute;
        if (!keyProvider.isEnabled()) return attribute;           // 키 없음 → 평문
        if (attribute.startsWith(PREFIX)) return attribute;       // 이미 암호화됨 → 멱등

        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keyProvider.getKey(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes());

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("SSH credential 암호화 실패", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        if (dbData.isEmpty()) return dbData;
        if (!dbData.startsWith(PREFIX)) return dbData;            // 레거시 평문 → 그대로
        if (!keyProvider.isEnabled()) {
            // prefix는 있지만 키가 없음 — 복호화 불가. 평문으로 내려보내진 안 됨.
            throw new IllegalStateException(
                    "DB에 암호화된 credential이 있으나 KITE_ENCRYPTION_KEY가 설정되지 않았습니다.");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(decoded);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keyProvider.getKey(),
                    new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain);
        } catch (Exception e) {
            throw new RuntimeException("SSH credential 복호화 실패 — 암호화 키가 변경되었거나 데이터가 변조됨", e);
        }
    }
}
