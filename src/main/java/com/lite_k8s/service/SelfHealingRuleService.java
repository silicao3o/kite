package com.lite_k8s.service;

import com.lite_k8s.config.SelfHealingProperties;
import com.lite_k8s.model.SelfHealingRuleEntity;
import com.lite_k8s.repository.SelfHealingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SelfHealingRuleService {

    private final SelfHealingRuleRepository repository;

    public List<SelfHealingProperties.Rule> findAllActive() {
        return repository.findByEnabled(true).stream()
                .map(SelfHealingRuleEntity::toRule)
                .toList();
    }

    public SelfHealingRuleEntity save(SelfHealingRuleEntity entity) {
        return repository.save(entity);
    }

    public void disable(String id) {
        repository.findById(id).ifPresent(e -> {
            e.setEnabled(false);
            repository.save(e);
        });
    }

    public Optional<SelfHealingRuleEntity> findById(String id) {
        return repository.findById(id);
    }

    public List<SelfHealingRuleEntity> findAll() {
        return repository.findAll();
    }
}
