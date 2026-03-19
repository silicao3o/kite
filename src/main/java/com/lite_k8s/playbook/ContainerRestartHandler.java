package com.lite_k8s.playbook;

import com.lite_k8s.service.DockerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 컨테이너 재시작 액션 핸들러
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContainerRestartHandler implements ActionHandler {

    private final DockerService dockerService;

    @Override
    public ActionResult execute(Action action, Map<String, String> context) {
        Map<String, String> params = action.getParams();
        if (params == null || !params.containsKey("containerId")) {
            return ActionResult.failure("Missing required parameter: containerId");
        }

        String containerId = params.get("containerId");
        log.info("컨테이너 재시작 실행: {}", containerId);

        boolean success = dockerService.restartContainer(containerId);
        if (success) {
            return ActionResult.success();
        } else {
            return ActionResult.failure("Failed to restart container: " + containerId);
        }
    }
}
