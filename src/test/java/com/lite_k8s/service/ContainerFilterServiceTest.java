package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerFilterServiceTest {

    private MonitorProperties monitorProperties;
    private ContainerFilterService filterService;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();
        filterService = new ContainerFilterService(monitorProperties);
    }

    @Test
    @DisplayName("필터 없으면 모든 컨테이너 모니터링")
    void shouldMonitor_WithNoFilters_ShouldReturnTrue() {
        // when
        boolean result = filterService.shouldMonitor("my-app", "nginx:latest");

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("제외 이름 패턴에 매칭되면 제외")
    void shouldMonitor_WhenMatchesExcludeName_ShouldReturnFalse() {
        // given
        monitorProperties.getFilter().setExcludeNames(List.of(".*-temp", "test-.*"));

        // when & then
        assertThat(filterService.shouldMonitor("app-temp", "nginx:latest")).isFalse();
        assertThat(filterService.shouldMonitor("test-container", "nginx:latest")).isFalse();
        assertThat(filterService.shouldMonitor("my-app", "nginx:latest")).isTrue();
    }

    @Test
    @DisplayName("제외 이미지 패턴에 매칭되면 제외")
    void shouldMonitor_WhenMatchesExcludeImage_ShouldReturnFalse() {
        // given
        monitorProperties.getFilter().setExcludeImages(List.of("busybox.*", "alpine.*"));

        // when & then
        assertThat(filterService.shouldMonitor("my-app", "busybox:latest")).isFalse();
        assertThat(filterService.shouldMonitor("my-app", "alpine:3.18")).isFalse();
        assertThat(filterService.shouldMonitor("my-app", "nginx:latest")).isTrue();
    }

    @Test
    @DisplayName("포함 이름 패턴이 있으면 매칭되는 것만 포함")
    void shouldMonitor_WhenIncludeNamesSet_ShouldOnlyIncludeMatching() {
        // given
        monitorProperties.getFilter().setIncludeNames(List.of("prod-.*", "important-.*"));

        // when & then
        assertThat(filterService.shouldMonitor("prod-api", "nginx:latest")).isTrue();
        assertThat(filterService.shouldMonitor("important-db", "postgres:latest")).isTrue();
        assertThat(filterService.shouldMonitor("dev-api", "nginx:latest")).isFalse();
    }

    @Test
    @DisplayName("포함 이미지 패턴이 있으면 매칭되는 것만 포함")
    void shouldMonitor_WhenIncludeImagesSet_ShouldOnlyIncludeMatching() {
        // given
        monitorProperties.getFilter().setIncludeImages(List.of("nginx.*", "postgres.*"));

        // when & then
        assertThat(filterService.shouldMonitor("my-app", "nginx:latest")).isTrue();
        assertThat(filterService.shouldMonitor("my-db", "postgres:15")).isTrue();
        assertThat(filterService.shouldMonitor("my-cache", "redis:latest")).isFalse();
    }

    @Test
    @DisplayName("제외가 포함보다 우선")
    void shouldMonitor_ExcludeTakesPrecedenceOverInclude() {
        // given
        monitorProperties.getFilter().setIncludeNames(List.of("prod-.*"));
        monitorProperties.getFilter().setExcludeNames(List.of("prod-temp-.*"));

        // when & then
        assertThat(filterService.shouldMonitor("prod-api", "nginx:latest")).isTrue();
        assertThat(filterService.shouldMonitor("prod-temp-worker", "nginx:latest")).isFalse();
    }

    @Test
    @DisplayName("null 값 처리")
    void shouldMonitor_WithNullValues_ShouldHandleGracefully() {
        // given
        monitorProperties.getFilter().setExcludeNames(List.of("test-.*"));

        // when & then
        assertThat(filterService.shouldMonitor(null, "nginx:latest")).isTrue();
        assertThat(filterService.shouldMonitor("my-app", null)).isTrue();
    }

    @Test
    @DisplayName("잘못된 정규식 패턴 무시")
    void shouldMonitor_WithInvalidRegex_ShouldIgnorePattern() {
        // given
        monitorProperties.getFilter().setExcludeNames(List.of("[invalid", "test-.*"));

        // when & then
        // [invalid 패턴은 무시되고, test-.* 패턴만 적용
        assertThat(filterService.shouldMonitor("test-app", "nginx:latest")).isFalse();
        assertThat(filterService.shouldMonitor("my-app", "nginx:latest")).isTrue();
    }
}
