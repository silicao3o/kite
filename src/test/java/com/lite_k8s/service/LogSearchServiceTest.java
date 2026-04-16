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
import static org.mockito.Mockito.verify;
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

    private static final String SPRING_BOOT_LOGS = """
            2026-04-16T04:09:22.682981716Z 2026-04-16T13:09:22.682+09:00  INFO 1 --- [quvi-api] [io-8199-exec-10] c.d.q.w.QuviHandshakeInterceptor         : WebSocket handshake API Key 인증 성공: userId=seah_id, keyType=SERVICE
            2026-04-16T04:09:22.684064328Z 2026-04-16T13:09:22.683+09:00  INFO 1 --- [quvi-api] [io-8199-exec-10] c.d.q.websocket.Nl2sqlWebSocketHandler   : WebSocket 연결 수립: sessionId=a9a34db3-e7ba-4dce-8916-e298e77cd635
            2026-04-16T04:09:22.696566697Z 2026-04-16T13:09:22.696+09:00  INFO 1 --- [quvi-api] [nio-8199-exec-9] c.d.q.agent.nl2sql.agent.ExecuteAgent    : DAG 모드로 Agent 워크플로우 실행 - workflowId: 6f528e1b-3a06-4628-9023-e97b2619fb97
            2026-04-16T04:09:22.696605099Z 2026-04-16T13:09:22.696+09:00  INFO 1 --- [quvi-api] [nio-8199-exec-9] c.d.q.agent.nl2sql.workflow.DagExecutor  : DAG 실행 시작 - 전체 노드: 2개, 시작 노드: [MODEL_SELECTOR]
            2026-04-16T04:09:35.853120667Z 2026-04-16T13:09:35.852+09:00  WARN 1 --- [quvi-api] [nio-8199-exec-6] c.d.q.exception.GlobalExceptionHandler   : BusinessException: [404 NOT_FOUND] 존재하지 않는 입력값입니다.
            2026-04-16T04:10:38.680038067Z 2026-04-16T13:10:38.679+09:00  INFO 1 --- [quvi-api] [      Thread-30] c.d.q.a.common.AbstractSingleLlmNode     : modelSelector 응답: result
            2026-04-16T04:10:38.694434756Z 2026-04-16T13:10:38.694+09:00  INFO 1 --- [quvi-api] [      Thread-30] c.d.q.agent.nl2sql.agent.ExecuteAgent    : ModelSelector 완료 - 선택된 모델: [PocProductWip, PocRawMaterial]
            2026-04-16T04:10:39.308815105Z 2026-04-16T13:10:39.308+09:00 ERROR 1 --- [quvi-api] [      Thread-31] c.d.langqv.llm.gemini.GeminiSdkDelegate  : Gemini SDK structured call failed: 400 - workflowId: 6f528e1b-3a06-4628-9023-e97b2619fb97
            2026-04-16T04:10:04.891350586Z 2026-04-16T13:10:04.891+09:00  INFO 1 --- [quvi-api] [   scheduling-1] c.d.q.s.s.WorkflowDataCleanupScheduler   : 만료된 QuviBot 데이터 정리 완료
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
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SAMPLE_LOGS);

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
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SAMPLE_LOGS);

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
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SAMPLE_LOGS);

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
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SAMPLE_LOGS);
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
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SAMPLE_LOGS);

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
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SAMPLE_LOGS);

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
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SAMPLE_LOGS);

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
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SAMPLE_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "INFO", null, null, null);

        // then
        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getKeyword()).isEqualTo("INFO");
    }

    // === Step 3: Spring Boot 포맷에서 스레드명 파싱 ===

    @Test
    @DisplayName("Spring Boot 로그 포맷에서 스레드명을 파싱할 수 있다")
    void shouldParseThreadNameFromSpringBootFormat() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SPRING_BOOT_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "WebSocket handshake", null, null, null);

        // then
        assertThat(result.getEntries()).hasSize(1);
        assertThat(result.getEntries().get(0).getThreadName()).isEqualTo("io-8199-exec-10");
    }

    // === Step 4: Spring Boot 포맷에서 로거명 파싱 ===

    @Test
    @DisplayName("Spring Boot 로그 포맷에서 로거명을 파싱할 수 있다")
    void shouldParseLoggerFromSpringBootFormat() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SPRING_BOOT_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "WebSocket handshake", null, null, null);

        // then
        assertThat(result.getEntries().get(0).getLogger()).isEqualTo("c.d.q.w.QuviHandshakeInterceptor");
    }

    // === Step 5: Spring Boot 포맷에서 레벨과 타임스탬프 파싱 ===

    @Test
    @DisplayName("Spring Boot 로그 포맷에서 레벨과 타임스탬프를 올바르게 파싱할 수 있다")
    void shouldParseLevelAndTimestampFromSpringBootFormat() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SPRING_BOOT_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, null, null, null, List.of("WARN"));

        // then
        assertThat(result.getEntries()).hasSize(1);
        assertThat(result.getEntries().get(0).getLevel()).isEqualTo("WARN");
        assertThat(result.getEntries().get(0).getTimestamp()).isNotNull();
    }

    // === Step 5 추가: 스레드명에 공백 패딩이 있는 경우 trim 처리 ===

    @Test
    @DisplayName("스레드명의 공백 패딩을 제거한다")
    void shouldTrimThreadNamePadding() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SPRING_BOOT_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "modelSelector 응답", null, null, null);

        // then
        assertThat(result.getEntries()).hasSize(1);
        assertThat(result.getEntries().get(0).getThreadName()).isEqualTo("Thread-30");
    }

    // === Step 7: 스레드명 필터링 ===

    @Test
    @DisplayName("스레드명으로 로그를 필터링할 수 있다")
    void shouldFilterByThreadName() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SPRING_BOOT_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, null, null, null, null, "nio-8199-exec-9", null);

        // then
        assertThat(result.getEntries()).hasSize(2);
        assertThat(result.getEntries()).allMatch(e -> "nio-8199-exec-9".equals(e.getThreadName()));
    }

    // === Step 8: traceId(workflowId) 필터링 ===

    @Test
    @DisplayName("메시지에서 workflowId로 필터링할 수 있다")
    void shouldFilterByTraceId() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SPRING_BOOT_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, null, null, null, null, null, "6f528e1b-3a06-4628-9023-e97b2619fb97");

        // then
        assertThat(result.getEntries()).hasSize(2); // exec-9의 DAG 모드 + Thread-31의 Gemini 실패
        assertThat(result.getEntries()).allMatch(e ->
            e.getMessage().contains("6f528e1b-3a06-4628-9023-e97b2619fb97"));
    }

    // === Step 9: 검색 결과에 사용 가능한 스레드 목록 ===

    @Test
    @DisplayName("검색 결과에 사용 가능한 스레드 목록이 포함된다")
    void shouldIncludeAvailableThreads() {
        // given
        String containerId = "abc123";
        when(dockerService.getContainerLogs(containerId, null, null)).thenReturn(SPRING_BOOT_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, null, null, null, null);

        // then
        assertThat(result.getAvailableThreads()).isNotNull();
        assertThat(result.getAvailableThreads()).contains("io-8199-exec-10", "nio-8199-exec-9", "Thread-30", "Thread-31", "scheduling-1", "nio-8199-exec-6");
    }

    // === Step 10: 멀티노드 검색 지원 ===

    @Test
    @DisplayName("검색 API에 nodeId를 전달하면 해당 노드의 컨테이너 로그에서 검색한다")
    void shouldSearchWithNodeId() {
        // given
        String containerId = "abc123";
        String containerName = "my-app";
        String nodeId = "node-1";
        when(dockerService.getContainerLogs(containerId, containerName, nodeId)).thenReturn(SAMPLE_LOGS);

        // when
        LogSearchResult result = logSearchService.search(containerId, "ERROR", null, null, null, null, null, containerName, nodeId);

        // then
        assertThat(result.getEntries()).hasSize(2);
        verify(dockerService).getContainerLogs(containerId, containerName, nodeId);
    }
}
