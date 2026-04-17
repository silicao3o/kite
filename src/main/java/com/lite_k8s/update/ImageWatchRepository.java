package com.lite_k8s.update;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageWatchRepository extends JpaRepository<ImageWatchEntity, String> {

    List<ImageWatchEntity> findByEnabled(boolean enabled);
}
