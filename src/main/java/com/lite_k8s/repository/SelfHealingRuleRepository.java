package com.lite_k8s.repository;

import com.lite_k8s.model.SelfHealingRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SelfHealingRuleRepository extends JpaRepository<SelfHealingRuleEntity, String> {
    List<SelfHealingRuleEntity> findByEnabled(boolean enabled);
}
