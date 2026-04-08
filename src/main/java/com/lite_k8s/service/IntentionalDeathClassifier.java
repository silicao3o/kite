package com.lite_k8s.service;

import com.lite_k8s.model.ContainerDeathEvent;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 컨테이너 죽음 이벤트가 "의도적 종료"인지 판정한다.
 *
 * 판정 규칙 (우선순위):
 * 1. 라벨 self-healing.heal-intentional=true → intentional=false (override)
 * 2. 선행 stop 이벤트 → intentional (stop-event-precedent)
 * 3. 선행 kill 이벤트 + !oomKilled → intentional (kill-event-precedent)
 * 4. exitCode == 0 + 선행 이벤트 없음 → intentional (exit-zero)
 * 5. 그 외 → abnormal
 *
 * 주의: exitCode 단독(예: 143)으로는 판정하지 않는다.
 *      Docker의 STOPSIGNAL 커스텀 이미지 때문에 exit code만으론 신뢰할 수 없다.
 *      판정의 근간은 Docker 이벤트 스트림의 선행 이벤트(stop/kill) 여부다.
 */
@Service
public class IntentionalDeathClassifier {

    private static final String LABEL_HEAL_INTENTIONAL = "self-healing.heal-intentional";

    public void classify(ContainerDeathEvent event) {
        if (hasHealIntentionalLabel(event)) {
            event.setIntentional(false);
            event.setIntentionalReason("label-override");
            return;
        }

        String action = event.getAction();

        if ("stop".equalsIgnoreCase(action)) {
            event.setIntentional(true);
            event.setIntentionalReason("stop-event-precedent");
            return;
        }

        if ("kill".equalsIgnoreCase(action) && !event.isOomKilled()) {
            event.setIntentional(true);
            event.setIntentionalReason("kill-event-precedent");
            return;
        }

        if (isDieWithoutPrecedent(action)
                && event.getExitCode() != null
                && event.getExitCode() == 0L) {
            event.setIntentional(true);
            event.setIntentionalReason("exit-zero");
            return;
        }

        event.setIntentional(false);
    }

    private boolean hasHealIntentionalLabel(ContainerDeathEvent event) {
        Map<String, String> labels = event.getLabels();
        if (labels == null) return false;
        return "true".equalsIgnoreCase(labels.get(LABEL_HEAL_INTENTIONAL));
    }

    private boolean isDieWithoutPrecedent(String action) {
        return action == null || "die".equalsIgnoreCase(action);
    }
}
