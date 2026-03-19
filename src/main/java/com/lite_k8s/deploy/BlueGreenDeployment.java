package com.lite_k8s.deploy;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Blue-Green 배포 전략
 *
 * 1. Green 컨테이너 생성 (이름: {원본}-green)
 * 2. Green 모두 정상 실행 확인
 * 3. 성공: Blue 제거 → 완료
 * 4. 실패: Green 제거 (롤백) → Blue 유지
 */
@Slf4j
public class BlueGreenDeployment implements DeploymentStrategy {

    private final ContainerOperator operator;
    private final long waitMs;

    public BlueGreenDeployment(ContainerOperator operator) {
        this(operator, 3000);
    }

    public BlueGreenDeployment(ContainerOperator operator, long waitMs) {
        this.operator = operator;
        this.waitMs = waitMs;
    }

    @Override
    public DeployResult deploy(DeploymentSpec spec, DockerClient dockerClient) {
        List<DeploymentSpec.RunningContainer> blues = spec.getTargets();
        List<String> greenIds = new ArrayList<>();

        log.info("[BlueGreen] Green 배포 시작: {}개 → {}", blues.size(), spec.getNewImage());

        // 1. Green 컨테이너 생성
        for (DeploymentSpec.RunningContainer blue : blues) {
            String greenName = blue.getName() + "-green";
            String greenId = operator.createAndStart(blue.getId(), spec.getNewImage(), greenName);
            if (greenId == null) {
                log.error("[BlueGreen] Green 생성 실패: {} → 롤백", greenName);
                rollbackGreen(greenIds);
                return DeployResult.failure(type(), greenIds.size(), 1, "Green 생성 실패 → 롤백");
            }
            greenIds.add(greenId);
        }

        // 2. 대기 후 Green 상태 확인
        waitForGreen();

        boolean allGreenRunning = greenIds.stream().allMatch(operator::isRunning);
        if (!allGreenRunning) {
            log.error("[BlueGreen] Green 일부 비정상 → 롤백");
            rollbackGreen(greenIds);
            return DeployResult.failure(type(), 0, greenIds.size(), "Green 비정상 → 롤백");
        }

        // 3. Blue 제거
        log.info("[BlueGreen] Green 정상 확인 → Blue 제거");
        for (DeploymentSpec.RunningContainer blue : blues) {
            operator.stopAndRemove(blue.getId());
        }

        log.info("[BlueGreen] 완료: {}개", greenIds.size());
        return DeployResult.success(type(), greenIds.size(), greenIds,
                "Blue-Green 완료: " + greenIds.size() + "개 Green 활성화");
    }

    private void rollbackGreen(List<String> greenIds) {
        greenIds.forEach(id -> {
            log.info("[BlueGreen] 롤백: Green {} 제거", id);
            operator.stopAndRemove(id);
        });
    }

    private void waitForGreen() {
        if (waitMs <= 0) return;
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public DeploymentType type() {
        return DeploymentType.BLUE_GREEN;
    }
}
