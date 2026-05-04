package com.lite_k8s.controller;

import com.lite_k8s.config.SelfHealingProperties;
import com.lite_k8s.model.ContainerInfo;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.repository.HealingEventRepository;
import com.lite_k8s.service.ContainerLabelReader;
import com.lite_k8s.service.DockerService;
import com.lite_k8s.service.HealingRuleMatcher;
import com.lite_k8s.service.LogSearchService;
import com.lite_k8s.service.MetricsScheduler;
import com.lite_k8s.service.RestartTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerNodeFilterTest {

    @Mock private DockerService dockerService;
    @Mock private SelfHealingProperties selfHealingProperties;
    @Mock private ContainerLabelReader labelReader;
    @Mock private HealingRuleMatcher ruleMatcher;
    @Mock private RestartTracker restartTracker;
    @Mock private HealingEventRepository healingEventRepository;
    @Mock private MetricsScheduler metricsScheduler;
    @Mock private LogSearchService logSearchService;
    @Mock private NodeRegistry nodeRegistry;

    private DashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(
                dockerService, selfHealingProperties, labelReader, ruleMatcher,
                restartTracker, healingEventRepository, metricsScheduler,
                logSearchService, nodeRegistry);
    }

    @Test
    @DisplayName("nodeId 파라미터 없을 때 전체 컨테이너를 반환한다")
    void shouldReturnAllContainersWhenNoNodeFilter() {
        ContainerInfo local = ContainerInfo.builder()
                .id("c1").name("local-app").state("running").nodeId(null).nodeName("local")
                .labels(Map.of()).build();
        ContainerInfo remote = ContainerInfo.builder()
                .id("c2").name("remote-app").state("running").nodeId("node-1").nodeName("vm-b")
                .labels(Map.of()).build();

        when(metricsScheduler.getCachedContainers()).thenReturn(List.of(local, remote));

        Model model = new ConcurrentModel();
        controller.dashboard(model, true, null);

        @SuppressWarnings("unchecked")
        List<ContainerInfo> containers = (List<ContainerInfo>) model.getAttribute("containers");
        assertThat(containers).hasSize(2);
    }

    @Test
    @DisplayName("nodeId=local 전달 시 로컬 컨테이너만 반환한다")
    void shouldFilterContainersByLocalNode() {
        ContainerInfo local = ContainerInfo.builder()
                .id("c1").name("local-app").state("running").nodeId(null).nodeName("local")
                .labels(Map.of()).build();
        ContainerInfo remote = ContainerInfo.builder()
                .id("c2").name("remote-app").state("running").nodeId("node-1").nodeName("vm-b")
                .labels(Map.of()).build();

        when(metricsScheduler.getCachedContainers()).thenReturn(List.of(local, remote));

        Model model = new ConcurrentModel();
        controller.dashboard(model, true, "local");

        @SuppressWarnings("unchecked")
        List<ContainerInfo> containers = (List<ContainerInfo>) model.getAttribute("containers");
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getNodeId()).isNull();
        assertThat(containers.get(0).getName()).isEqualTo("local-app");
    }

    @Test
    @DisplayName("특정 nodeId 전달 시 해당 노드 컨테이너만 반환한다")
    void shouldFilterContainersByRemoteNodeId() {
        ContainerInfo local = ContainerInfo.builder()
                .id("c1").name("local-app").state("running").nodeId(null).nodeName("local")
                .labels(Map.of()).build();
        ContainerInfo remote1 = ContainerInfo.builder()
                .id("c2").name("remote-app-1").state("running").nodeId("node-1").nodeName("vm-b")
                .labels(Map.of()).build();
        ContainerInfo remote2 = ContainerInfo.builder()
                .id("c3").name("remote-app-2").state("running").nodeId("node-2").nodeName("vm-c")
                .labels(Map.of()).build();

        when(metricsScheduler.getCachedContainers()).thenReturn(List.of(local, remote1, remote2));

        Model model = new ConcurrentModel();
        controller.dashboard(model, true, "node-1");

        @SuppressWarnings("unchecked")
        List<ContainerInfo> containers = (List<ContainerInfo>) model.getAttribute("containers");
        assertThat(containers).hasSize(1);
        assertThat(containers.get(0).getNodeId()).isEqualTo("node-1");
        assertThat(containers.get(0).getName()).isEqualTo("remote-app-1");
    }

    @Test
    @DisplayName("모델에 등록된 노드 목록이 전달된다")
    void shouldPassNodeListToModel() {
        Node node1 = Node.builder().id("node-1").name("vm-b").build();
        Node node2 = Node.builder().id("node-2").name("vm-c").build();

        when(metricsScheduler.getCachedContainers()).thenReturn(List.of());
        when(nodeRegistry.findAll()).thenReturn(List.of(node1, node2));

        Model model = new ConcurrentModel();
        controller.dashboard(model, true, null);

        @SuppressWarnings("unchecked")
        List<Node> nodes = (List<Node>) model.getAttribute("nodes");
        assertThat(nodes).hasSize(2);
        assertThat(nodes).extracting(Node::getName).containsExactly("vm-b", "vm-c");
    }
}
