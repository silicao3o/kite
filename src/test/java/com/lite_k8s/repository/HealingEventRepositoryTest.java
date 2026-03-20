package com.lite_k8s.repository;

import com.lite_k8s.model.HealingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class HealingEventRepositoryTest {

    @Autowired
    private HealingEventJpaRepository repository;

    @Test
    @DisplayName("이벤트를 저장할 수 있다")
    void shouldSaveEvent() {
        repository.save(createEvent("abc123", "web-server", true));

        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("모든 이벤트를 조회할 수 있다")
    void shouldFindAllEvents() {
        repository.save(createEvent("abc123", "web-1", true));
        repository.save(createEvent("def456", "web-2", false));

        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("최신 이벤트가 먼저 조회된다")
    void shouldReturnEventsInDescendingOrder() {
        HealingEvent old = HealingEvent.builder()
                .containerId("abc123").containerName("old").timestamp(LocalDateTime.now().minusMinutes(1))
                .success(true).restartCount(1).message("ok").build();
        HealingEvent recent = HealingEvent.builder()
                .containerId("def456").containerName("recent").timestamp(LocalDateTime.now())
                .success(true).restartCount(1).message("ok").build();

        repository.save(old);
        repository.save(recent);

        List<HealingEvent> events = repository.findAllByOrderByTimestampDesc();
        assertThat(events.get(0).getContainerName()).isEqualTo("recent");
    }

    @Test
    @DisplayName("컨테이너 ID로 이벤트를 조회할 수 있다")
    void shouldFindByContainerId() {
        repository.save(createEvent("abc123", "web-1", true));
        repository.save(createEvent("abc123", "web-1", false));
        repository.save(createEvent("def456", "web-2", true));

        List<HealingEvent> events = repository.findByContainerIdOrderByTimestampDesc("abc123");

        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.getContainerId().equals("abc123"));
    }

    @Test
    @DisplayName("성공한 이벤트만 필터링할 수 있다")
    void shouldFindBySuccessTrue() {
        repository.save(createEvent("abc123", "web-1", true));
        repository.save(createEvent("def456", "web-2", false));
        repository.save(createEvent("ghi789", "web-3", true));

        List<HealingEvent> events = repository.findBySuccessOrderByTimestampDesc(true);

        assertThat(events).hasSize(2);
        assertThat(events).allMatch(HealingEvent::isSuccess);
    }

    @Test
    @DisplayName("실패한 이벤트만 필터링할 수 있다")
    void shouldFindBySuccessFalse() {
        repository.save(createEvent("abc123", "web-1", true));
        repository.save(createEvent("def456", "web-2", false));
        repository.save(createEvent("ghi789", "web-3", false));

        List<HealingEvent> events = repository.findBySuccessOrderByTimestampDesc(false);

        assertThat(events).hasSize(2);
        assertThat(events).noneMatch(HealingEvent::isSuccess);
    }

    private HealingEvent createEvent(String containerId, String containerName, boolean success) {
        return HealingEvent.builder()
                .containerId(containerId)
                .containerName(containerName)
                .timestamp(LocalDateTime.now())
                .success(success)
                .restartCount(1)
                .message(success ? "성공" : "실패")
                .build();
    }
}
