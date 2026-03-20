package com.lite_k8s.repository;

import com.lite_k8s.model.HealingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class HealingEventRepository {

    private final HealingEventJpaRepository jpa;

    public void save(HealingEvent event) {
        jpa.save(event);
    }

    public List<HealingEvent> findAll() {
        return jpa.findAllByOrderByTimestampDesc();
    }

    public List<HealingEvent> findByContainerId(String containerId) {
        return jpa.findByContainerIdOrderByTimestampDesc(containerId);
    }

    public List<HealingEvent> findBySuccess(boolean success) {
        return jpa.findBySuccessOrderByTimestampDesc(success);
    }
}
