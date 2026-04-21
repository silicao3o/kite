package com.lite_k8s.envprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnvProfileEntryRepository extends JpaRepository<EnvProfileEntry, String> {
    List<EnvProfileEntry> findByProfileId(String profileId);
    Optional<EnvProfileEntry> findByProfileIdAndKey(String profileId, String key);
    void deleteByProfileIdAndKey(String profileId, String key);
}
