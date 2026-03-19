package com.lite_k8s.incident;

import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class SuggestionRepository {

    private final Map<String, Suggestion> store = new ConcurrentHashMap<>();

    public void save(Suggestion suggestion) {
        store.put(suggestion.getId(), suggestion);
    }

    public Optional<Suggestion> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<Suggestion> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(Suggestion::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public List<Suggestion> findByStatus(Suggestion.Status status) {
        return store.values().stream()
                .filter(s -> s.getStatus() == status)
                .sorted(Comparator.comparing(Suggestion::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    public void clear() {
        store.clear();
    }
}
