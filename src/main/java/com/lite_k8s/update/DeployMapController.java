package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.lite_k8s.node.Node;
import com.lite_k8s.node.NodeDockerClientFactory;
import com.lite_k8s.node.NodeRegistry;
import com.lite_k8s.util.ContainerPatternMatcher;
import com.lite_k8s.util.DockerContainerNames;
import com.lite_k8s.util.ImageReferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 디플로이 맵 화면 전용 — 와치별 노드 매칭 결과를 서버에서 직접 계산해 내려준다.
 *
 * UI 가 /api/containers 캐시를 따로 받아 매칭하는 방식은
 *  1) 캐시가 일시적 노드 조회 실패로 비어있으면 영원히 '알 수 없음' 표시
 *  2) UI 매칭 로직이 백엔드 폴러와 어긋날 수 있음
 * 이 두 가지 문제를 만든다. 백엔드에서 폴러와 동일한 매칭 규칙
 * (ContainerPatternMatcher + ImageReferences.sameShortName) 으로 즉시
 * 계산해 내려주면 UI 는 결과만 표시.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DeployMapController {

    private final ImageWatchService watchService;
    private final NodeRegistry nodeRegistry;
    private final NodeDockerClientFactory nodeClientFactory;
    private final DockerClient localDockerClient;

    @GetMapping("/api/deploy-map")
    public List<Map<String, Object>> deployMap() {
        List<ImageWatchEntity> watches = watchService.findAll();
        List<Node> allNodes = nodeRegistry != null ? nodeRegistry.findAll() : List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (ImageWatchEntity w : watches) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", w.getId());
            entry.put("image", w.getEffectiveImage());
            entry.put("tag", w.getTag());
            entry.put("mode", w.getMode() != null ? w.getMode().name() : "POLLING");
            entry.put("containerPattern", w.getContainerPattern());
            entry.put("enabled", w.isEnabled());
            entry.put("nodes", buildNodeStatus(w, allNodes));
            result.add(entry);
        }
        return result;
    }

    private List<Map<String, Object>> buildNodeStatus(ImageWatchEntity watch, List<Node> allNodes) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<String> targetNames = watch.getNodeNames() != null ? watch.getNodeNames() : List.of();
        String watchImage = watch.getEffectiveImage();
        String pattern = watch.getContainerPattern();

        if (targetNames.isEmpty()) {
            // 와치 nodeNames 가 비어있으면 '전체' 한 카드로 표시 (등록된 모든 노드 + 로컬)
            int[] counts = countAcrossAllNodes(allNodes, pattern, watchImage);
            nodes.add(buildNodeEntry("전체", counts[0], counts[1], true));
            return nodes;
        }

        for (String name : targetNames) {
            Node node = allNodes.stream()
                    .filter(n -> name.equals(n.getName()))
                    .findFirst()
                    .orElse(null);
            if (node == null) {
                // 등록된 노드에 없음 — 카드는 표시하되 데이터 없음으로
                nodes.add(buildNodeEntry(name, 0, 0, false));
                continue;
            }
            int[] counts = countOnNode(node, pattern, watchImage);
            nodes.add(buildNodeEntry(name, counts[0], counts[1], counts[2] == 1));
        }
        return nodes;
    }

    private int[] countAcrossAllNodes(List<Node> allNodes, String pattern, String watchImage) {
        int matched = 0, running = 0;
        try {
            for (Container c : localDockerClient.listContainersCmd().withShowAll(true).exec()) {
                if (matchesContainer(c, pattern, watchImage)) {
                    matched++;
                    if ("running".equals(c.getState())) running++;
                }
            }
        } catch (Exception e) {
            log.debug("로컬 컨테이너 조회 실패: {}", e.getMessage());
        }
        for (Node node : allNodes) {
            int[] c = countOnNode(node, pattern, watchImage);
            matched += c[0];
            running += c[1];
        }
        return new int[]{matched, running};
    }

    /** @return [matched, running, reachable(0|1)] */
    private int[] countOnNode(Node node, String pattern, String watchImage) {
        int matched = 0, running = 0;
        try {
            DockerClient client = nodeClientFactory.createClient(node);
            for (Container c : client.listContainersCmd().withShowAll(true).exec()) {
                if (matchesContainer(c, pattern, watchImage)) {
                    matched++;
                    if ("running".equals(c.getState())) running++;
                }
            }
            return new int[]{matched, running, 1};
        } catch (Exception e) {
            log.debug("노드 {} 컨테이너 조회 실패: {}", node.getName(), e.getMessage());
            return new int[]{matched, running, 0};
        }
    }

    private boolean matchesContainer(Container c, String pattern, String watchImage) {
        String name = DockerContainerNames.extractName(c, "");
        if (!ContainerPatternMatcher.matches(name, pattern)) return false;
        // image 가 ref 형식(nginx:alpine, ghcr.io/.../app:tag) 일 때만 short name 가드 적용.
        // image ID (digest pin 후 untag 된 컨테이너) 는 비교 불가 → 패턴 매칭만 신뢰.
        if (!ImageReferences.isImageReference(c.getImage())) return true;
        return ImageReferences.sameShortName(c.getImage(), watchImage);
    }

    private Map<String, Object> buildNodeEntry(String name, int matched, int running, boolean reachable) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("name", name);
        n.put("matched", matched);
        n.put("running", running);
        n.put("reachable", reachable);
        return n;
    }
}
