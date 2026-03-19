package com.lite_k8s.repository;

import com.lite_k8s.model.HealingEvent;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Repository
public class HealingEventRepository {

    private static final int MAX_EVENTS = 100;
    private final List<HealingEvent> events = new CopyOnWriteArrayList<>();

    public void save(HealingEvent event) {
        events.add(0, event); // 최신 이벤트를 앞에 추가

        // 최대 개수 초과 시 오래된 이벤트 삭제
        while (events.size() > MAX_EVENTS) {
            events.remove(events.size() - 1);
        }
    }

    public List<HealingEvent> findAll() {
        return new ArrayList<>(events);
    }

    public List<HealingEvent> findByContainerId(String containerId) {
        return events.stream()
                .filter(e -> e.getContainerId().equals(containerId))
                .collect(Collectors.toList());
    }

    public List<HealingEvent> findBySuccess(boolean success) {
        return events.stream()
                .filter(e -> e.isSuccess() == success)
                .collect(Collectors.toList());
    }
}
