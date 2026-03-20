package com.lite_k8s.node;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NodeJpaRepository extends JpaRepository<Node, String> {
    List<Node> findByStatus(NodeStatus status);
    boolean existsByName(String name);
    java.util.Optional<Node> findByName(String name);
}
