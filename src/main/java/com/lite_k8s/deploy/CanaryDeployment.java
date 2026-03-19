package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Canary 배포 전략
 *
 * weight% 만큼의 컨테이너를 새 버전으로 교체
 * 나머지는 기존 버전 유지 (기존 컨테이너 제거 없음, canary만 추가)
 *
 * 예: 5개 컨테이너, weight=20% → 1개 canary 생성 (이름: {원본}-canary)
 */
@Slf4j
@RequiredArgsConstructor
public class CanaryDeployment implements DeploymentStrategy {

    private final ContainerOperator operator;

    @Override
    public DeployResult deploy(DeploymentSpec spec, DockerClient dockerClient) {
        List<DeploymentSpec.RunningContainer> targets = spec.getTargets();
        int total = targets.size();
        int canaryCount = Math.max(1, (int) Math.ceil(total * spec.getCanaryWeight() / 100.0));
        canaryCount = Math.min(canaryCount, total);

        log.info("[Canary] {}개 중 {}개 canary 배포 ({}%): {}",
                total, canaryCount, spec.getCanaryWeight(), spec.getNewImage());

        List<String> deployedIds = new ArrayList<>();
        int failed = 0;

        for (int i = 0; i < canaryCount; i++) {
            DeploymentSpec.RunningContainer target = targets.get(i);
            String canaryName = target.getName() + "-canary";

            // 기존 컨테이너는 그대로 두고 canary를 새로 생성
            String newId = operator.createAndStart(target.getId(), spec.getNewImage(), canaryName);
            if (newId != null) {
                deployedIds.add(newId);
                log.info("[Canary] {} 배포 완료: {}", canaryName,
                        newId.length() > 12 ? newId.substring(0, 12) : newId);
            } else {
                log.error("[Canary] {} 배포 실패", canaryName);
                failed++;
            }
        }

        if (failed > 0) {
            return DeployResult.failure(type(), deployedIds.size(), failed,
                    "Canary 부분 실패: " + failed + "개");
        }
        return DeployResult.success(type(), deployedIds.size(), deployedIds,
                String.format("Canary 완료: %d/%d개 (%.0f%%)", canaryCount, total, (double) spec.getCanaryWeight()));
    }

    @Override
    public DeploymentType type() {
        return DeploymentType.CANARY;
    }
}
