package com.lite_k8s.repository;

import com.lite_k8s.model.HealingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HealingEventJpaRepository extends JpaRepository<HealingEvent, String> {
    List<HealingEvent> findAllByOrderByTimestampDesc();
    List<HealingEvent> findByContainerIdOrderByTimestampDesc(String containerId);
    List<HealingEvent> findBySuccessOrderByTimestampDesc(boolean success);
}
