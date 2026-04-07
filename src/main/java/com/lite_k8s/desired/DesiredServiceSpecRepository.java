package com.lite_k8s.desired;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DesiredServiceSpecRepository extends JpaRepository<DesiredServiceSpecEntity, String> {
    List<DesiredServiceSpecEntity> findByEnabled(boolean enabled);
    Optional<DesiredServiceSpecEntity> findByName(String name);
}
