package com.lite_k8s.envprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        // 32바이트 키 (Base64: YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=)
        cryptoService = new CryptoService("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
    }

    @Test
    @DisplayName("encrypt 후 decrypt하면 원본 평문이 복원된다")
    void encryptThenDecrypt() {
        String plaintext = "qwer1234!";
        String encrypted = cryptoService.encrypt(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);

        String decrypted = cryptoService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("같은 평문을 암호화해도 매번 다른 암호문이 생성된다 (IV 랜덤)")
    void encryptProducesDifferentCiphertextEachTime() {
        String plaintext = "secret-password";
        String enc1 = cryptoService.encrypt(plaintext);
        String enc2 = cryptoService.encrypt(plaintext);

        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test
    @DisplayName("null 또는 빈 문자열 encrypt 시 null 반환")
    void encryptNullReturnsNull() {
        assertThat(cryptoService.encrypt(null)).isNull();
        assertThat(cryptoService.encrypt("")).isNull();
    }

    @Test
    @DisplayName("null decrypt 시 null 반환")
    void decryptNullReturnsNull() {
        assertThat(cryptoService.decrypt(null)).isNull();
    }
}
