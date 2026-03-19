package com.lite_k8s.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookRegistryTest {

    private PlaybookRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new PlaybookRegistry();
    }

    @Test
    @DisplayName("Playbook을 등록하고 이름으로 조회한다")
    void shouldRegisterAndFindByName() {
        // given
        Playbook playbook = Playbook.builder()
                .name("container-restart")
                .trigger(Trigger.builder().event("container.die").build())
                .build();

        // when
        registry.register(playbook);
        Optional<Playbook> found = registry.findByName("container-restart");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("container-restart");
    }

    @Test
    @DisplayName("이벤트 타입으로 매칭되는 Playbook 목록을 조회한다")
    void shouldFindPlaybooksByEventType() {
        // given
        Playbook diePlaybook1 = Playbook.builder()
                .name("restart-on-die")
                .trigger(Trigger.builder().event("container.die").build())
                .build();
        Playbook diePlaybook2 = Playbook.builder()
                .name("notify-on-die")
                .trigger(Trigger.builder().event("container.die").build())
                .build();
        Playbook oomPlaybook = Playbook.builder()
                .name("oom-recovery")
                .trigger(Trigger.builder().event("container.oom").build())
                .build();

        registry.register(diePlaybook1);
        registry.register(diePlaybook2);
        registry.register(oomPlaybook);

        // when
        List<Playbook> diePlaybooks = registry.findByEvent("container.die");
        List<Playbook> oomPlaybooks = registry.findByEvent("container.oom");

        // then
        assertThat(diePlaybooks).hasSize(2);
        assertThat(oomPlaybooks).hasSize(1);
        assertThat(oomPlaybooks.get(0).getName()).isEqualTo("oom-recovery");
    }

    @Test
    @DisplayName("조건까지 매칭되는 Playbook을 찾는다")
    void shouldFindPlaybooksMatchingConditions() {
        // given
        Playbook exitCode1Playbook = Playbook.builder()
                .name("handle-exit-1")
                .trigger(Trigger.builder()
                        .event("container.die")
                        .conditions(Map.of("exitCode", "1"))
                        .build())
                .build();
        Playbook exitCode137Playbook = Playbook.builder()
                .name("handle-oom-kill")
                .trigger(Trigger.builder()
                        .event("container.die")
                        .conditions(Map.of("exitCode", "137", "oomKilled", "true"))
                        .build())
                .build();
        Playbook anyDiePlaybook = Playbook.builder()
                .name("handle-any-die")
                .trigger(Trigger.builder()
                        .event("container.die")
                        .build())
                .build();

        registry.register(exitCode1Playbook);
        registry.register(exitCode137Playbook);
        registry.register(anyDiePlaybook);

        // when - exitCode 1로 죽은 경우
        Map<String, String> event1 = Map.of("exitCode", "1");
        List<Playbook> matched1 = registry.findMatchingPlaybooks("container.die", event1);

        // then - exitCode 1 플레이북과 any 플레이북 매칭
        assertThat(matched1).hasSize(2);
        assertThat(matched1).extracting(Playbook::getName)
                .containsExactlyInAnyOrder("handle-exit-1", "handle-any-die");

        // when - OOM으로 죽은 경우 (exitCode 137, oomKilled true)
        Map<String, String> eventOom = Map.of("exitCode", "137", "oomKilled", "true");
        List<Playbook> matchedOom = registry.findMatchingPlaybooks("container.die", eventOom);

        // then - OOM 플레이북과 any 플레이북 매칭
        assertThat(matchedOom).hasSize(2);
        assertThat(matchedOom).extracting(Playbook::getName)
                .containsExactlyInAnyOrder("handle-oom-kill", "handle-any-die");
    }

    @Test
    @DisplayName("등록되지 않은 이벤트 타입은 빈 목록을 반환한다")
    void shouldReturnEmptyListForUnknownEvent() {
        // when
        List<Playbook> playbooks = registry.findByEvent("unknown.event");

        // then
        assertThat(playbooks).isEmpty();
    }
}
