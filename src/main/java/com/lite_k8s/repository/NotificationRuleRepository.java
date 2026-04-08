package com.lite_k8s.repository;

import com.lite_k8s.model.NotificationRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRuleRepository extends JpaRepository<NotificationRuleEntity, String> {
    List<NotificationRuleEntity> findByEnabled(boolean enabled);
}
