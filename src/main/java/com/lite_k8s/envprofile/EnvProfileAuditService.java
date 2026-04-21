package com.lite_k8s.envprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EnvProfileAuditService {

    private final EnvProfileAuditLogRepository repository;

    public void logCreated(String profileId, String profileName, String actor) {
        repository.save(EnvProfileAuditLog.builder()
                .profileId(profileId)
                .profileName(profileName)
                .action(EnvProfileAuditLog.Action.CREATED)
                .actor(actor)
                .build());
    }

    public void logUpdated(String profileId, String profileName, String actor) {
        repository.save(EnvProfileAuditLog.builder()
                .profileId(profileId)
                .profileName(profileName)
                .action(EnvProfileAuditLog.Action.UPDATED)
                .actor(actor)
                .build());
    }

    public void logDeleted(String profileId, String profileName, String actor) {
        repository.save(EnvProfileAuditLog.builder()
                .profileId(profileId)
                .profileName(profileName)
                .action(EnvProfileAuditLog.Action.DELETED)
                .actor(actor)
                .build());
    }

    public void logEntryUpdated(String profileId, String profileName, String changedKeys,
                                 String beforeHash, String afterHash, String actor) {
        repository.save(EnvProfileAuditLog.builder()
                .profileId(profileId)
                .profileName(profileName)
                .action(EnvProfileAuditLog.Action.ENTRY_UPDATED)
                .changedKeys(changedKeys)
                .beforeHash(beforeHash)
                .afterHash(afterHash)
                .actor(actor)
                .build());
    }

    public void logReferenced(String profileId, String profileName, String containerName, String actor) {
        repository.save(EnvProfileAuditLog.builder()
                .profileId(profileId)
                .profileName(profileName)
                .action(EnvProfileAuditLog.Action.REFERENCED)
                .referencedContainerName(containerName)
                .actor(actor)
                .build());
    }
}
