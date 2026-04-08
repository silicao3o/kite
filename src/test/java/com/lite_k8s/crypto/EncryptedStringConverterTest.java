package com.lite_k8s.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedStringConverterTest {

    // 32바이트(256bit) 테스트 키. base64 encoding of "01234567890123456789012345678901"
    private static final String TEST_KEY_BASE64 = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    private EncryptedStringConverter converter;

    @BeforeEach
    void setUp() {
        converter = new EncryptedStringConverter(new CryptoKeyProvider(TEST_KEY_BASE64));
    }

    @Test
    void convertToDatabase_nullInput_shouldReturnNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabase_emptyInput_shouldReturnEmpty() {
        assertThat(converter.convertToDatabaseColumn("")).isEqualTo("");
    }

    @Test
    void convertToDatabase_shouldReturnEncryptedValue() {
        String plain = "my-secret-password";
        String encrypted = converter.convertToDatabaseColumn(plain);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(encrypted).startsWith("enc:v1:"); // 버전 prefix로 마킹
    }

    @Test
    void roundTrip_shouldReturnOriginalValue() {
        String plain = "super-secret-passphrase-!@#$%^&*()";

        String encrypted = converter.convertToDatabaseColumn(plain);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    void convertToDatabase_sameValueTwice_shouldProduceDifferentCiphertexts() {
        // IV가 랜덤하게 생성되어야 함 (AES-GCM nonce)
        String plain = "secret";

        String enc1 = converter.convertToDatabaseColumn(plain);
        String enc2 = converter.convertToDatabaseColumn(plain);

        assertThat(enc1).isNotEqualTo(enc2);
        assertThat(converter.convertToEntityAttribute(enc1)).isEqualTo(plain);
        assertThat(converter.convertToEntityAttribute(enc2)).isEqualTo(plain);
    }

    @Test
    void convertToEntity_nullInput_shouldReturnNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntity_unencryptedLegacyValue_shouldReturnAsIs() {
        // 기존에 평문으로 저장된 레거시 데이터는 그대로 반환 (마이그레이션 호환)
        String legacy = "legacy-plaintext";

        assertThat(converter.convertToEntityAttribute(legacy)).isEqualTo(legacy);
    }

    @Test
    void convertToDatabase_alreadyEncrypted_shouldNotReencrypt() {
        String plain = "secret";
        String encrypted = converter.convertToDatabaseColumn(plain);
        String reencrypted = converter.convertToDatabaseColumn(encrypted);

        // 이미 암호화된 값은 다시 암호화하지 않음 (멱등성)
        assertThat(reencrypted).isEqualTo(encrypted);
    }

    @Test
    void convertToEntity_tamperedCiphertext_shouldThrow() {
        String plain = "secret";
        String encrypted = converter.convertToDatabaseColumn(plain);
        // 본문 한 글자 변조
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "XX";

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void keyProvider_withoutKey_shouldNotEncryptButPreservePlaintext() {
        // 암호화 키가 설정되지 않은 환경 (개발/테스트) — 평문 그대로 저장
        EncryptedStringConverter noKey = new EncryptedStringConverter(new CryptoKeyProvider(null));

        String plain = "dev-password";
        String stored = noKey.convertToDatabaseColumn(plain);
        assertThat(stored).isEqualTo(plain); // 평문 그대로

        String loaded = noKey.convertToEntityAttribute(stored);
        assertThat(loaded).isEqualTo(plain);
    }
}
