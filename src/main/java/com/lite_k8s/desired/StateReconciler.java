package com.lite_k8s.desired;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.util.DockerContainerNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Desired State Reconciler
 *
 * 선언된 replicas와 실제 running 컨테이너 수를 비교하여 자동으로 맞춤:
 * - 부족하면 컨테이너 생성
 * - 초과하면 컨테이너 제거
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StateReconciler {

    private final DesiredStateProperties properties;
    private final DockerClient dockerClient;
    private final ContainerFactory containerFactory;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;

    @Scheduled(fixedDelayString =
            "#{${docker.monitor.desired-state.reconcile-interval-seconds:30} * 1000}")
    public void reconcile() {
        if (!properties.isEnabled()) {
            return;
        }

        log.debug("Desired State Reconcile 시작: {}개 서비스", properties.getServices().size());

        for (DesiredStateProperties.ServiceSpec spec : properties.getServices()) {
            try {
                reconcileService(spec);
            } catch (Exception e) {
                log.error("Reconcile 실패: {}", spec.getName(), e);
            }
        }
    }

    private DockerClient resolveClient(String nodeId) {
        if (nodeId == null) return dockerClient;
        return nodeRegistry.findById(nodeId)
                .map(nodeClientFactory::createClient)
                .orElse(dockerClient);
    }

    private void reconcileService(DesiredStateProperties.ServiceSpec spec) {
        DockerClient client = resolveClient(spec.getNodeId());
        List<Container> all = client.listContainersCmd()
                .withShowAll(true)
                .exec();

        // 이름 prefix로 매칭되는 컨테이너 분류
        List<Container> running = all.stream()
                .filter(c -> matchesService(c, spec) && "running".equals(c.getState()))
                .toList();

        List<Container> dead = all.stream()
                .filter(c -> matchesService(c, spec) && !"running".equals(c.getState()))
                .toList();

        int actual = running.size();
        int desired = spec.getReplicas();

        log.debug("[{}] desired={}, actual={}", spec.getName(), desired, actual);

        if (actual < desired) {
            // 죽은 컨테이너 먼저 정리
            cleanupDeadContainers(client, dead);
            // 부족한 수만큼 생성
            int toCreate = desired - actual;
            log.info("[{}] 컨테이너 {}개 생성 필요", spec.getName(), toCreate);
            createContainers(spec, running, toCreate);

        } else if (actual > desired) {
            // 초과분 제거 (가장 나중에 생성된 것부터)
            int toRemove = actual - desired;
            log.info("[{}] 컨테이너 {}개 제거 필요", spec.getName(), toRemove);
            removeExcess(client, running, toRemove);
        }
    }

    private void createContainers(DesiredStateProperties.ServiceSpec spec,
                                   List<Container> existing, int count) {
        int nextIndex = nextAvailableIndex(spec, existing);
        for (int i = 0; i < count; i++) {
            containerFactory.create(spec, nextIndex + i);
        }
    }

    private int nextAvailableIndex(DesiredStateProperties.ServiceSpec spec,
                                    List<Container> existing) {
        return existing.stream()
                .mapToInt(c -> parseIndex(c, spec.getContainerNamePrefix()))
                .max()
                .orElse(-1) + 1;
    }

    private int parseIndex(Container container, String prefix) {
        String name = extractName(container);
        if (name.startsWith(prefix + "-")) {
            try {
                return Integer.parseInt(name.substring(prefix.length() + 1));
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    private void removeExcess(DockerClient client, List<Container> running, int count) {
        List<Container> sorted = running.stream()
                .sorted(Comparator.comparing(c -> extractName(c), Comparator.reverseOrder()))
                .toList();
        sorted.stream().limit(count).forEach(c -> stopAndRemove(client, c));
    }

    private void cleanupDeadContainers(DockerClient client, List<Container> dead) {
        dead.forEach(c -> {
            try {
                client.removeContainerCmd(c.getId()).exec();
                log.debug("죽은 컨테이너 제거: {}", extractName(c));
            } catch (Exception e) {
                log.warn("컨테이너 제거 실패: {}", extractName(c), e);
            }
        });
    }

    private void stopAndRemove(DockerClient client, Container container) {
        String name = extractName(container);
        try {
            client.stopContainerCmd(container.getId()).exec();
            client.removeContainerCmd(container.getId()).exec();
            log.info("초과 컨테이너 제거 완료: {}", name);
        } catch (Exception e) {
            log.error("컨테이너 제거 실패: {}", name, e);
        }
    }

    private boolean matchesService(Container container, DesiredStateProperties.ServiceSpec spec) {
        String name = extractName(container);
        return name.startsWith(spec.getContainerNamePrefix() + "-") ||
               name.equals(spec.getContainerNamePrefix());
    }

    private String extractName(Container container) {
        return DockerContainerNames.extractName(container);
    }
}
