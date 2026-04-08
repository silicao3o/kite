package com.lite_k8s.repository;

import com.lite_k8s.model.EmailSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailSubscriptionRepository extends JpaRepository<EmailSubscriptionEntity, String> {
    List<EmailSubscriptionEntity> findByEnabled(boolean enabled);
}
