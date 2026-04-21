package com.lite_k8s.envprofile;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRegistryRepository extends JpaRepository<ImageRegistry, String> {
}
