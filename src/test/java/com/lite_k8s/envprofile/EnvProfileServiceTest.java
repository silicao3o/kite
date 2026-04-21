package com.lite_k8s.envprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvProfileServiceTest {

    @Mock private EnvProfileRepository profileRepository;
    @Mock private EnvProfileEntryRepository entryRepository;

    private CryptoService cryptoService;
    private EnvProfileService service;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService("YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=");
        service = new EnvProfileService(profileRepository, entryRepository, cryptoService);
    }

    @Test
    @DisplayName("secret=true 엔트리 저장 시 value가 암호화된다")
    void saveEntry_WhenSecret_EncryptsValue() {
        EnvProfileEntry entry = EnvProfileEntry.builder()
                .profileId("p1")
                .key("DB_PASSWORD")
                .value("qwer1234!")
                .secret(true)
                .build();

        when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveEntry(entry);

        ArgumentCaptor<EnvProfileEntry> captor = ArgumentCaptor.forClass(EnvProfileEntry.class);
        verify(entryRepository).save(captor.capture());

        EnvProfileEntry saved = captor.getValue();
        assertThat(saved.getValue()).isNotEqualTo("qwer1234!");
        // 복호화하면 원본이 나와야 함
        assertThat(cryptoService.decrypt(saved.getValue())).isEqualTo("qwer1234!");
    }

    @Test
    @DisplayName("secret=false 엔트리 저장 시 value가 평문 그대로 저장된다")
    void saveEntry_WhenNotSecret_PlaintextValue() {
        EnvProfileEntry entry = EnvProfileEntry.builder()
                .profileId("p1")
                .key("DB_HOST")
                .value("112.187.198.214")
                .secret(false)
                .build();

        when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.saveEntry(entry);

        ArgumentCaptor<EnvProfileEntry> captor = ArgumentCaptor.forClass(EnvProfileEntry.class);
        verify(entryRepository).save(captor.capture());

        assertThat(captor.getValue().getValue()).isEqualTo("112.187.198.214");
    }

    @Test
    @DisplayName("getEntries 시 secret=true 엔트리의 value는 '***'로 마스킹된다")
    void getEntries_MasksSecretValues() {
        EnvProfileEntry secretEntry = EnvProfileEntry.builder()
                .profileId("p1").key("DB_PASSWORD")
                .value(cryptoService.encrypt("qwer1234!"))
                .secret(true).build();
        EnvProfileEntry plainEntry = EnvProfileEntry.builder()
                .profileId("p1").key("DB_HOST")
                .value("112.187.198.214")
                .secret(false).build();

        when(entryRepository.findByProfileId("p1")).thenReturn(List.of(secretEntry, plainEntry));

        List<EnvProfileEntry> entries = service.getEntries("p1");

        assertThat(entries).hasSize(2);
        EnvProfileEntry masked = entries.stream().filter(e -> e.getKey().equals("DB_PASSWORD")).findFirst().get();
        assertThat(masked.getValue()).isEqualTo("***");

        EnvProfileEntry plain = entries.stream().filter(e -> e.getKey().equals("DB_HOST")).findFirst().get();
        assertThat(plain.getValue()).isEqualTo("112.187.198.214");
    }
}
