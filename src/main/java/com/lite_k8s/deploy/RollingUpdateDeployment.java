package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolling Update 전략
 * maxUnavailable 단위로 순차 교체 (무중단)
 */
@Slf4j
@RequiredArgsConstructor
public class RollingUpdateDeployment implements DeploymentStrategy {

    private final ContainerOperator operator;

    @Override
    public DeployResult deploy(DeploymentSpec spec, DockerClient dockerClient) {
        List<DeploymentSpec.RunningContainer> targets = spec.getTargets();
        if (targets.isEmpty()) {
            return DeployResult.success(type(), 0, List.of(), "대상 없음");
        }

        int maxUnavailable = Math.max(1, spec.getMaxUnavailable());
        List<String> deployedIds = new ArrayList<>();
        int failed = 0;

        for (int i = 0; i < targets.size(); i += maxUnavailable) {
            int end = Math.min(i + maxUnavailable, targets.size());
            List<DeploymentSpec.RunningContainer> batch = targets.subList(i, end);

            for (DeploymentSpec.RunningContainer target : batch) {
                log.info("[RollingUpdate] 교체 중: {} → {}", target.getName(), spec.getNewImage());

                // 기존 컨테이너 중지·제거
                operator.stopAndRemove(target.getId());

                // 새 컨테이너 생성·시작
                String newId = operator.createAndStart(target.getId(), spec.getNewImage(), target.getName());
                if (newId != null) {
                    deployedIds.add(newId);
                } else {
                    log.error("[RollingUpdate] 생성 실패: {}", target.getName());
                    failed++;
                }
            }
        }

        if (failed > 0) {
            return DeployResult.failure(type(), deployedIds.size(), failed,
                    failed + "개 교체 실패");
        }
        return DeployResult.success(type(), deployedIds.size(), deployedIds,
                "Rolling Update 완료: " + deployedIds.size() + "개");
    }

    @Override
    public DeploymentType type() {
        return DeploymentType.ROLLING_UPDATE;
    }
}
