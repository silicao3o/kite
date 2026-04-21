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
class EnvProfileResolverVariableTest {

    @Mock private EnvProfileService service;
    private EnvProfileResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new EnvProfileResolver(service);
    }

    @Test
    @DisplayName("${KEY} 패턴을 같은 프로파일 내 다른 엔트리 값으로 치환한다")
    void resolve_SubstitutesVariables() {
        when(service.getDecryptedEntries("p1")).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_HOST").value("112.187.198.214").build(),
                EnvProfileEntry.builder().key("DB_PORT").value("5432").build(),
                EnvProfileEntry.builder().key("DB_NAME").value("Operia").build(),
                EnvProfileEntry.builder().key("SPRING_DATASOURCE_URL")
                        .value("jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}").build()
        ));

        Map<String, String> result = resolver.resolve(List.of("p1"));

        assertThat(result.get("SPRING_DATASOURCE_URL"))
                .isEqualTo("jdbc:postgresql://112.187.198.214:5432/Operia");
    }

    @Test
    @DisplayName("존재하지 않는 변수 참조는 그대로 유지된다")
    void resolve_UnknownVariableKeptAsIs() {
        when(service.getDecryptedEntries("p1")).thenReturn(List.of(
                EnvProfileEntry.builder().key("URL").value("${UNKNOWN_VAR}/path").build()
        ));

        Map<String, String> result = resolver.resolve(List.of("p1"));

        assertThat(result.get("URL")).isEqualTo("${UNKNOWN_VAR}/path");
    }

    @Test
    @DisplayName("여러 프로파일 간 변수 참조도 치환된다")
    void resolve_CrossProfileSubstitution() {
        when(service.getDecryptedEntries("p1")).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_HOST").value("10.0.0.1").build(),
                EnvProfileEntry.builder().key("DB_PORT").value("5432").build()
        ));
        when(service.getDecryptedEntries("p2")).thenReturn(List.of(
                EnvProfileEntry.builder().key("DB_NAME").value("MyDB").build(),
                EnvProfileEntry.builder().key("JDBC_URL")
                        .value("jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}").build()
        ));

        Map<String, String> result = resolver.resolve(List.of("p1", "p2"));

        assertThat(result.get("JDBC_URL"))
                .isEqualTo("jdbc:postgresql://10.0.0.1:5432/MyDB");
    }

    @Test
    @DisplayName("변수 참조가 없는 값은 그대로 반환된다")
    void resolve_NoVariables_Unchanged() {
        when(service.getDecryptedEntries("p1")).thenReturn(List.of(
                EnvProfileEntry.builder().key("TZ").value("Asia/Seoul").build()
        ));

        Map<String, String> result = resolver.resolve(List.of("p1"));

        assertThat(result.get("TZ")).isEqualTo("Asia/Seoul");
    }
}
