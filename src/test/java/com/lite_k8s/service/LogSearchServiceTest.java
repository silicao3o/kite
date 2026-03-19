package com.lite_k8s.service;

import com.lite_k8s.model.LogSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogSearchServiceTest {

    @Mock
    private DockerService dockerService;

    private LogSearchService logSearchService;

    private static final String SAMPLE_LOGS = """
            2026-03-13T10:00:00.000Z INFO Starting application
            2026-03-13T10:00:01.000Z DEBUG Initializing database connection
            2026-03-13T10:00:02.000Z INFO Server started on port 8080
            2026-03-13T10:00:03.000Z WARN Memory usage high: 85%
            2026-03-13T10:00:04.000Z ERROR Connection failed: timeout
            2026-03-13T10:00:05.000Z ERROR Database error: connection refused
            2026-03-13T10:00:06.000Z INFO Retrying connection...
            """;

    @BeforeEach
    void setUp() {
        logSearchService = new LogSearchService(dockerService);
    }

    @Test
    @DisplayName("키워드로 로그를 검색할 수 있다")
    void shouldSearchLogsByKeyword() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId)).thenReturn(SAMPLE_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "ERROR", null, null, null);

        // then
        assertThat(result.getEntries()).hasSize(2);
        assertThat(result.getEntries()).allMatch(e -> e.getMessage().contains("ERROR"));
    }

    @Test
    @DisplayName("대소문자 구분 없이 검색할 수 있다")
    void shouldSearchCaseInsensitive() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId)).thenReturn(SAMPLE_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "error", null, null, null);

        // then
        assertThat(result.getEntries()).hasSize(2);
    }

    @Test
    @DisplayName("로그 레벨로 필터링할 수 있다")
    void shouldFilterByLogLevel() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId)).thenReturn(SAMPLE_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, null, null, null, List.of("ERROR", "WARN"));

        // then
        assertThat(result.getEntries()).hasSize(3);
        assertThat(result.getEntries()).allMatch(e ->
            e.getLevel().equals("ERROR") || e.getLevel().equals("WARN"));
    }

    @Test
    @DisplayName("시간 범위로 필터링할 수 있다")
    void shouldFilterByTimeRange() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId)).thenReturn(SAMPLE_LOGS);
        LocalDateTime from = LocalDateTime.of(2026, 3, 13, 10, 0, 2);
        LocalDateTime to = LocalDateTime.of(2026, 3, 13, 10, 0, 4);

        // when
        LogSearchResult result = logSearchService.search(containerId, null, from, to, null);

        // then
        assertThat(result.getEntries()).hasSize(3); // 10:00:02, 10:00:03, 10:00:04
    }

    @Test
    @DisplayName("검색 키워드가 하이라이트 된다")
    void shouldHighlightSearchKeyword() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId)).thenReturn(SAMPLE_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "ERROR", null, null, null);

        // then
        assertThat(result.getEntries()).allMatch(e ->
            e.getHighlightedMessage().contains("<mark>ERROR</mark>"));
    }

    @Test
    @DisplayName("키워드와 레벨을 함께 필터링할 수 있다")
    void shouldCombineKeywordAndLevelFilter() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId)).thenReturn(SAMPLE_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "connection", null, null, List.of("ERROR"));

        // then
        assertThat(result.getEntries()).hasSize(2);
        assertThat(result.getEntries()).allMatch(e ->
            e.getLevel().equals("ERROR") && e.getMessage().toLowerCase().contains("connection"));
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 리스트를 반환한다")
    void shouldReturnEmptyListWhenNoMatch() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId)).thenReturn(SAMPLE_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "nonexistent", null, null, null);

        // then
        assertThat(result.getEntries()).isEmpty();
        assertThat(result.getTotalCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("검색 결과에 총 개수가 포함된다")
    void shouldIncludeTotalCount() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId)).thenReturn(SAMPLE_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "INFO", null, null, null);

        // then
        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getKeyword()).isEqualTo("INFO");
    }
}
