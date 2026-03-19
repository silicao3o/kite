package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Recreate 전략
 * 전체 중지 → 전체 생성 (다운타임 발생, 가장 단순)
 */
@Slf4j
@RequiredArgsConstructor
public class RecreateDeployment implements DeploymentStrategy {

    private final ContainerOperator operator;

    @Override
    public DeployResult deploy(DeploymentSpec spec, DockerClient dockerClient) {
        List<DeploymentSpec.RunningContainer> targets = spec.getTargets();

        log.info("[Recreate] 전체 중지 시작: {}개", targets.size());

        // 1단계: 전체 중지·제거
        for (DeploymentSpec.RunningContainer target : targets) {
            operator.stopAndRemove(target.getId());
        }

        log.info("[Recreate] 새 버전 생성 시작: {} → {}", spec.getServiceName(), spec.getNewImage());

        // 2단계: 전체 새 버전 생성
        List<String> deployedIds = new ArrayList<>();
        int failed = 0;

        for (DeploymentSpec.RunningContainer target : targets) {
            String newId = operator.createAndStart(target.getId(), spec.getNewImage(), target.getName());
            if (newId != null) {
                deployedIds.add(newId);
            } else {
                failed++;
            }
        }

        if (failed > 0) {
            return DeployResult.failure(type(), deployedIds.size(), failed,
                    "Recreate 부분 실패: " + failed + "개 생성 실패");
        }
        return DeployResult.success(type(), deployedIds.size(), deployedIds,
                "Recreate 완료: " + deployedIds.size() + "개");
    }

    @Override
    public DeploymentType type() {
        return DeploymentType.RECREATE;
    }
}
