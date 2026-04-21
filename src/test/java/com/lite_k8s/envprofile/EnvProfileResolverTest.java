package com.lite_k8s.envprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvProfileResolverTest {

    @Mock private EnvProfileService service;
    private EnvProfileResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EnvProfileResolver(service);
    }

    @Test
    @DisplayName("profileIds로 엔트리를 수집하여 KEY=VALUE 맵 반환")
    void resolve_CollectsEntries() {
        when(service.getDecryptedEntries("p1")).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_HOST").value("10.0.0.1").build(),
                EnvProfileEntry.builder().key("DB_PORT").value("5432").build()
        ));

        Map<String, String> result = resolver.resolve(List.of("p1"));

        assertThat(result).containsEntry("DB_HOST", "10.0.0.1");
        assertThat(result).containsEntry("DB_PORT", "5432");
    }

    @Test
    @DisplayName("여러 프로파일 resolve 시 뒤쪽 프로파일이 앞쪽을 오버라이드")
    void resolve_LaterProfileOverrides() {
        when(service.getDecryptedEntries("p1")).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_HOST").value("host-from-p1").build()
        ));
        when(service.getDecryptedEntries("p2")).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_HOST").value("host-from-p2").build()
        ));

        Map<String, String> result = resolver.resolve(List.of("p1", "p2"));

        assertThat(result).containsEntry("DB_HOST", "host-from-p2");
    }

    @Test
    @DisplayName("빈 profileIds면 빈 맵 반환")
    void resolve_EmptyIds_ReturnsEmptyMap() {
        Map<String, String> result = resolver.resolve(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("resolveAsEnvList는 KEY=VALUE 형태의 String 리스트 반환")
    void resolveAsEnvList() {
        when(service.getDecryptedEntries("p1")).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_HOST").value("10.0.0.1").build()
        ));

        List<String> envList = resolver.resolveAsEnvList(List.of("p1"));

        assertThat(envList).contains("DB_HOST=10.0.0.1");
    }
}
