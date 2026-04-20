package com.lite_k8s.update;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ImageWatchService {

    private final ImageWatchRepository repository;

    public ImageWatchEntity save(ImageWatchEntity entity) {
        entity.validate();
        return repository.save(entity);
    }

    public List<ImageWatchEntity> findAll() {
        return repository.findAll();
    }

    public List<ImageWatchEntity> findEnabled() {
        return repository.findByEnabled(true);
    }

    public Optional<ImageWatchEntity> findById(String id) {
        return repository.findById(id);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }
}
