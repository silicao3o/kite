package com.lite_k8s.incident;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SuggestionRepository {

    private final SuggestionJpaRepository jpa;

    public void save(Suggestion suggestion) {
        jpa.save(suggestion);
    }

    public Optional<Suggestion> findById(String id) {
        return jpa.findById(id);
    }

    public List<Suggestion> findAll() {
        return jpa.findAllByOrderByCreatedAtDesc();
    }

    public List<Suggestion> findByStatus(Suggestion.Status status) {
        return jpa.findByStatusOrderByCreatedAtDesc(status);
    }

    public void clear() {
        jpa.deleteAll();
    }
}
