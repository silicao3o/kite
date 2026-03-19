package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import com.lite_k8s.metrics.MetricsHistoryService;
import com.lite_k8s.metrics.MetricsHistoryRepository;
import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.model.ContainerMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsSchedulerTest {

    @Mock
    private DockerService dockerService;

    @Mock
    private MetricsCollector metricsCollector;

    private MonitorProperties monitorProperties;
    private MetricsScheduler metricsScheduler;

    @BeforeEach
    void setUp() {
        monitorProperties = new MonitorProperties();
        MetricsHistoryService historyService = new MetricsHistoryService(new MetricsHistoryRepository());
        metricsScheduler = new MetricsScheduler(dockerService, metricsCollector, monitorProperties, historyService);
    }

    @Test
    @DisplayName("스케줄러가 실행되면 모든 running 컨테이너의 메트릭을 수집한다")
    void shouldCollectMetricsForAllRunningContainers() {
        // given
        ContainerInfo container1 = createContainer("c1", "container-1", "running");
        ContainerInfo container2 = createContainer("c2", "container-2", "running");
        ContainerInfo container3 = createContainer("c3", "container-3", "exited");

        when(dockerService.listContainers(true)).thenReturn(List.of(container1, container2, container3));
        when(metricsCollector.collectMetrics(anyString(), anyString()))
                .thenReturn(Optional.of(createMockMetrics()));

        // when
        metricsScheduler.collectMetrics();

        // then
        verify(metricsCollector, times(2)).collectMetrics(anyString(), anyString());
        verify(metricsCollector).collectMetrics("c1", "container-1");
        verify(metricsCollector).collectMetrics("c2", "container-2");
        verify(metricsCollector, never()).collectMetrics("c3", "container-3");
    }

    @Test
    @DisplayName("수집 비활성화 시 메트릭을 수집하지 않는다")
    void shouldNotCollectMetricsWhenDisabled() {
        // given
        monitorProperties.getMetrics().setEnabled(false);

        // when
        metricsScheduler.collectMetrics();

        // then
        verify(dockerService, never()).listContainers(anyBoolean());
        verify(metricsCollector, never()).collectMetrics(anyString(), anyString());
    }

    @Test
    @DisplayName("수집된 메트릭을 캐시에 저장한다")
    void shouldCacheCollectedMetrics() {
        // given
        ContainerInfo container = createContainer("c1", "container-1", "running");
        ContainerMetrics metrics = createMockMetrics();

        when(dockerService.listContainers(true)).thenReturn(List.of(container));
        when(metricsCollector.collectMetrics("c1", "container-1")).thenReturn(Optional.of(metrics));

        // when
        metricsScheduler.collectMetrics();

        // then
        Optional<ContainerMetrics> cached = metricsScheduler.getLatestMetrics("c1");
        assertThat(cached).isPresent();
        assertThat(cached.get()).isEqualTo(metrics);
    }

    @Test
    @DisplayName("캐시에 없는 컨테이너 메트릭 요청 시 empty 반환")
    void shouldReturnEmptyForUncachedContainer() {
        // when
        Optional<ContainerMetrics> result = metricsScheduler.getLatestMetrics("nonexistent");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("수집 주기 설정값을 반환한다")
    void shouldReturnConfiguredCollectionInterval() {
        // given
        monitorProperties.getMetrics().setCollectionIntervalSeconds(30);

        // when
        int interval = metricsScheduler.getCollectionIntervalSeconds();

        // then
        assertThat(interval).isEqualTo(30);
    }

    private ContainerInfo createContainer(String id, String name, String state) {
        return ContainerInfo.builder()
                .id(id)
                .name(name)
                .state(state)
                .build();
    }

    private ContainerMetrics createMockMetrics() {
        return ContainerMetrics.builder()
                .containerId("c1")
                .containerName("container-1")
                .cpuPercent(25.5)
                .memoryUsage(100 * 1024 * 1024L)
                .memoryLimit(512 * 1024 * 1024L)
                .memoryPercent(19.53)
                .networkRxBytes(1000L)
                .networkTxBytes(2000L)
                .build();
    }
}
