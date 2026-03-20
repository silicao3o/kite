package com.lite_k8s.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuggestionJpaRepository extends JpaRepository<Suggestion, String> {
    List<Suggestion> findAllByOrderByCreatedAtDesc();
    List<Suggestion> findByStatusOrderByCreatedAtDesc(Suggestion.Status status);
}
