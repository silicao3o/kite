package com.lite_k8s.approval;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 승인 대기 저장소 (인메모리)
 */
@Repository
public class PendingApprovalRepository {

    private final Map<String, PendingApproval> store = new ConcurrentHashMap<>();

    /**
     * 저장
     */
    public void save(PendingApproval approval) {
        store.put(approval.getId(), approval);
    }

    /**
     * ID로 조회
     */
    public Optional<PendingApproval> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * 상태별 조회
     */
    public List<PendingApproval> findByStatus(ApprovalStatus status) {
        return store.values().stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toList());
    }

    /**
     * 전체 조회
     */
    public List<PendingApproval> findAll() {
        return List.copyOf(store.values());
    }

    /**
     * 만료된 PENDING 상태 조회
     */
    public List<PendingApproval> findExpiredPending() {
        LocalDateTime now = LocalDateTime.now();
        return store.values().stream()
                .filter(a -> a.getStatus() == ApprovalStatus.PENDING)
                .filter(a -> a.getExpiresAt().isBefore(now))
                .collect(Collectors.toList());
    }

    /**
     * 삭제
     */
    public void delete(String id) {
        store.remove(id);
    }

    /**
     * 전체 삭제 (테스트용)
     */
    public void clear() {
        store.clear();
    }
}
