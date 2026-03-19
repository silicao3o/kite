package com.lite_k8s.playbook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 대기 액션 핸들러
 */
@Slf4j
@Component
public class DelayHandler implements ActionHandler {

    @Override
    public ActionResult execute(Action action, Map<String, String> context) {
        Map<String, String> params = action.getParams();
        if (params == null || !params.containsKey("seconds")) {
            return ActionResult.failure("Missing required parameter: seconds");
        }

        String secondsStr = params.get("seconds");
        try {
            int seconds = Integer.parseInt(secondsStr);
            log.info("{}초 대기 시작", seconds);
            Thread.sleep(seconds * 1000L);
            log.info("대기 완료");
            return ActionResult.success();
        } catch (NumberFormatException e) {
            return ActionResult.failure("Invalid seconds value: " + secondsStr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.failure("Delay interrupted");
        }
    }
}
