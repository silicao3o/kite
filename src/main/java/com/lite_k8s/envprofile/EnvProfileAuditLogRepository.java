package com.lite_k8s.envprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnvProfileAuditLogRepository extends JpaRepository<EnvProfileAuditLog, String> {
    List<EnvProfileAuditLog> findByProfileIdOrderByTimestampDesc(String profileId);
}
