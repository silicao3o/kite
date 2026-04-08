package com.lite_k8s.service;

import com.lite_k8s.model.ContainerDeathEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntentionalDeathClassifierTest {

    private final IntentionalDeathClassifier classifier = new IntentionalDeathClassifier();

    @Test
    void classify_stopEventPrecedent_shouldBeIntentional() {
        ContainerDeathEvent event = baseEvent()
                .action("stop")
                .exitCode(143L)
                .oomKilled(false)
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isTrue();
        assertThat(event.getIntentionalReason()).isEqualTo("stop-event-precedent");
    }

    @Test
    void classify_killEventPrecedentWithoutOom_shouldBeIntentional() {
        ContainerDeathEvent event = baseEvent()
                .action("kill")
                .exitCode(137L)
                .oomKilled(false)
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isTrue();
        assertThat(event.getIntentionalReason()).isEqualTo("kill-event-precedent");
    }

    @Test
    void classify_killEventWithOomKilled_shouldNotBeIntentional() {
        ContainerDeathEvent event = baseEvent()
                .action("kill")
                .exitCode(137L)
                .oomKilled(true)
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isFalse();
    }

    @Test
    void classify_oomEventPrecedent_shouldNotBeIntentional() {
        ContainerDeathEvent event = baseEvent()
                .action("oom")
                .exitCode(137L)
                .oomKilled(true)
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isFalse();
    }

    @Test
    void classify_exitCodeZeroWithoutPrecedent_shouldBeIntentional() {
        ContainerDeathEvent event = baseEvent()
                .action("die")
                .exitCode(0L)
                .oomKilled(false)
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isTrue();
        assertThat(event.getIntentionalReason()).isEqualTo("exit-zero");
    }

    @Test
    void classify_abnormalExitCodeWithoutPrecedent_shouldNotBeIntentional() {
        ContainerDeathEvent event = baseEvent()
                .action("die")
                .exitCode(1L)
                .oomKilled(false)
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isFalse();
    }

    @Test
    void classify_segfaultExitCode_shouldNotBeIntentional() {
        ContainerDeathEvent event = baseEvent()
                .action("die")
                .exitCode(139L)
                .oomKilled(false)
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isFalse();
    }

    @Test
    void classify_exitCode143WithoutPrecedent_shouldNotBeIntentional() {
        // exit 143 단독으로는 근거 부족 — STOPSIGNAL 커스텀 이미지가 있음
        // 선행 kill/stop 이벤트가 없으면 intentional로 보지 않는다
        ContainerDeathEvent event = baseEvent()
                .action("die")
                .exitCode(143L)
                .oomKilled(false)
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isFalse();
    }

    @Test
    void classify_nullExitCode_shouldNotBeIntentional() {
        ContainerDeathEvent event = baseEvent()
                .action("die")
                .exitCode(null)
                .oomKilled(false)
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isFalse();
    }

    @Test
    void classify_labelHealIntentionalTrue_shouldOverrideAndKeepAbnormal() {
        // 라벨로 "intentional이어도 자가치유/알림 그대로" 지정
        ContainerDeathEvent event = baseEvent()
                .action("stop")
                .exitCode(143L)
                .oomKilled(false)
                .labels(Map.of("self-healing.heal-intentional", "true"))
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isFalse();
        assertThat(event.getIntentionalReason()).isEqualTo("label-override");
    }

    @Test
    void classify_labelHealIntentionalFalse_shouldNotOverride() {
        ContainerDeathEvent event = baseEvent()
                .action("stop")
                .exitCode(143L)
                .oomKilled(false)
                .labels(Map.of("self-healing.heal-intentional", "false"))
                .build();

        classifier.classify(event);

        assertThat(event.isIntentional()).isTrue();
    }

    private ContainerDeathEvent.ContainerDeathEventBuilder baseEvent() {
        return ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("web-1");
    }
}
