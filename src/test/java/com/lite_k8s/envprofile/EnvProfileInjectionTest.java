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
class EnvProfileInjectionTest {

    @Mock private EnvProfileService service;
    private EnvProfileResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EnvProfileResolver(service);
    }

    @Test
    @DisplayName("라벨에서 profileIds 파싱: 'p1,p2' → ['p1', 'p2']")
    void parseProfileIdsFromLabel() {
        List<String> ids = EnvProfileResolver.parseProfileIdsFromLabel("p1,p2");
        assertThat(ids).containsExactly("p1", "p2");
    }

    @Test
    @DisplayName("라벨이 null이면 빈 리스트 반환")
    void parseProfileIdsFromLabel_Null() {
        List<String> ids = EnvProfileResolver.parseProfileIdsFromLabel(null);
        assertThat(ids).isEmpty();
    }

    @Test
    @DisplayName("mergeEnv: 프로파일 키는 프로파일 값 우선, 프로파일에 없는 키는 기존 유지")
    void mergeEnv() {
        when(service.getDecryptedEntries("p1")).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_HOST").value("10.0.0.1").build(),
                EnvProfileEntry.builder().key("DB_PORT").value("5432").build()
        ));

        String[] existingEnv = {"TZ=Asia/Seoul", "DB_PORT=9999"};

        String[] merged = resolver.mergeWithExistingEnv(List.of("p1"), existingEnv);

        Map<String, String> envMap = envArrayToMap(merged);
        // 프로파일 값이 우선 (DB 정보 갱신)
        assertThat(envMap).containsEntry("DB_HOST", "10.0.0.1");
        assertThat(envMap).containsEntry("DB_PORT", "5432");
        // 프로파일에 없는 키는 기존 유지
        assertThat(envMap).containsEntry("TZ", "Asia/Seoul");
    }

    @Test
    @DisplayName("buildProfileLabel: profileIds를 쉼표로 합쳐서 라벨 값 생성")
    void buildProfileLabel() {
        String label = EnvProfileResolver.buildProfileLabel(List.of("p1", "p2"));
        assertThat(label).isEqualTo("p1,p2");
    }

    @Test
    @DisplayName("mergeEnv: 기존 env의 ${KEY}도 프로파일 값으로 치환된다")
    void mergeEnv_SubstitutesExistingEnvVariables() {
        when(service.getDecryptedEntries("p1")).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_HOST").value("10.0.0.1").build(),
                EnvProfileEntry.builder().key("DB_PORT").value("5432").build(),
                EnvProfileEntry.builder().key("DB_NAME").value("Operia").build()
        ));

        String[] existingEnv = {"SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}", "TZ=Asia/Seoul"};

        String[] merged = resolver.mergeWithExistingEnv(List.of("p1"), existingEnv);

        Map<String, String> envMap = envArrayToMap(merged);
        assertThat(envMap.get("SPRING_DATASOURCE_URL")).isEqualTo("jdbc:postgresql://10.0.0.1:5432/Operia");
        assertThat(envMap.get("TZ")).isEqualTo("Asia/Seoul");
    }

    private Map<String, String> envArrayToMap(String[] env) {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        for (String e : env) {
            int idx = e.indexOf('=');
            if (idx > 0) map.put(e.substring(0, idx), e.substring(idx + 1));
        }
        return map;
    }
}
