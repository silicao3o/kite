package com.lite_k8s.compose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EnvSubstitution 유틸 테스트.
 *
 * ServiceDeployer 의 substituteVars / substituteAllFields 로직을 추출한 것이므로
 * 기존 동작과 동일해야 한다 (Tidy First — 구조 변경 단독).
 */
class EnvSubstitutionTest {

    @Test
    @DisplayName("${KEY} 가 context 값으로 치환된다")
    void substituteVars_replacesPlainKey() {
        String result = EnvSubstitution.substituteVars(
                "prefix-${NAME}-suffix",
                Map.of("NAME", "quvi"));
        assertThat(result).isEqualTo("prefix-quvi-suffix");
    }

    @Test
    @DisplayName("${KEY:-default} 는 KEY 가 없으면 default 로 치환")
    void substituteVars_usesDefaultWhenMissing() {
        String result = EnvSubstitution.substituteVars(
                "${CONTAINER_NAME:-chat-quvi}-nginx",
                Map.of());
        assertThat(result).isEqualTo("chat-quvi-nginx");
    }

    @Test
    @DisplayName("${KEY:-default} 는 KEY 가 있으면 KEY 값 사용")
    void substituteVars_prefersContextOverDefault() {
        String result = EnvSubstitution.substituteVars(
                "${CONTAINER_NAME:-chat-quvi}-nginx",
                Map.of("CONTAINER_NAME", "myapp"));
        assertThat(result).isEqualTo("myapp-nginx");
    }

    @Test
    @DisplayName("${KEY:?msg} 는 KEY 가 없으면 IllegalStateException")
    void substituteVars_throwsForRequiredMissing() {
        assertThatThrownBy(() -> EnvSubstitution.substituteVars(
                "${KEYS_DIR:?KEYS_DIR required}:/app/keys:ro",
                Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KEYS_DIR");
    }

    @Test
    @DisplayName("${KEY:?msg} 는 KEY 가 있으면 정상 치환")
    void substituteVars_requiredPresentSubstitutes() {
        String result = EnvSubstitution.substituteVars(
                "${KEYS_DIR:?required}:/app/keys:ro",
                Map.of("KEYS_DIR", "/home/u/keys"));
        assertThat(result).isEqualTo("/home/u/keys:/app/keys:ro");
    }

    @Test
    @DisplayName("null 또는 ${ 미포함 입력은 그대로 반환")
    void substituteVars_passthroughForPlain() {
        assertThat(EnvSubstitution.substituteVars(null, Map.of())).isNull();
        assertThat(EnvSubstitution.substituteVars("plain", Map.of())).isEqualTo("plain");
    }

    @Test
    @DisplayName("substituteList 는 각 요소를 치환한다")
    void substituteList_appliesPerElement() {
        List<String> result = EnvSubstitution.substituteList(
                List.of("${PORT:-8080}:80", "443:443"),
                Map.of("PORT", "9000"));
        assertThat(result).containsExactly("9000:80", "443:443");
    }

    @Test
    @DisplayName("substituteList(null) 은 빈 리스트")
    void substituteList_nullReturnsEmpty() {
        assertThat(EnvSubstitution.substituteList(null, Map.of())).isEmpty();
    }

    @Test
    @DisplayName("substituteFields 는 ParsedService 의 image/containerName/ports/volumes/extraHosts/memory/cpu 를 치환한다")
    void substituteFields_replacesAllStringFields() {
        ParsedService svc = ParsedService.builder()
                .serviceName("nginx")
                .image("${IMAGE:-nginx}:alpine")
                .containerName("${CONTAINER_NAME:-chat-quvi}-nginx")
                .ports(List.of("${HTTP:-80}:80"))
                .volumes(List.of("${SSL:-/dev/null}:/etc/ssl:ro"))
                .extraHosts(List.of("host.docker.internal:host-gateway"))
                .memoryLimit("${MEM:-1G}")
                .cpuLimit("${CPU:-1}")
                .environment(Map.of("TZ", "Asia/Seoul"))
                .networks(List.of("quvi-net"))
                .restartPolicy("unless-stopped")
                .build();

        ParsedService result = EnvSubstitution.substituteFields(
                svc,
                Map.of("CONTAINER_NAME", "qvc-chat", "MEM", "2G"));

        assertThat(result.getServiceName()).isEqualTo("nginx");
        assertThat(result.getImage()).isEqualTo("nginx:alpine");
        assertThat(result.getContainerName()).isEqualTo("qvc-chat-nginx");
        assertThat(result.getPorts()).containsExactly("80:80");
        assertThat(result.getVolumes()).containsExactly("/dev/null:/etc/ssl:ro");
        assertThat(result.getExtraHosts()).containsExactly("host.docker.internal:host-gateway");
        assertThat(result.getMemoryLimit()).isEqualTo("2G");
        assertThat(result.getCpuLimit()).isEqualTo("1");
        // 비치환 필드는 원본 유지
        assertThat(result.getEnvironment()).containsEntry("TZ", "Asia/Seoul");
        assertThat(result.getNetworks()).containsExactly("quvi-net");
        assertThat(result.getRestartPolicy()).isEqualTo("unless-stopped");
    }
}
