package com.lite_k8s.update;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImageUpdateHistoryRepository extends JpaRepository<ImageUpdateHistoryEntity, String> {

    List<ImageUpdateHistoryEntity> findByWatchIdOrderByCreatedAtDesc(String watchId);
}
