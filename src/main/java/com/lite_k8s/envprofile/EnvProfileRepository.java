package com.lite_k8s.envprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnvProfileRepository extends JpaRepository<EnvProfile, String> {
    List<EnvProfile> findByEnabled(boolean enabled);
}
