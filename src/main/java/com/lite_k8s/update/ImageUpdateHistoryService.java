package com.lite_k8s.update;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageUpdateHistoryService {

    private final ImageUpdateHistoryRepository repository;

    public ImageUpdateHistoryEntity record(ImageUpdateHistoryEntity entity) {
        return repository.save(entity);
    }

    public List<ImageUpdateHistoryEntity> findByWatchId(String watchId) {
        return repository.findByWatchIdOrderByCreatedAtDesc(watchId);
    }
}
