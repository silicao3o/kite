package com.lite_k8s.audit;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 조치 감사 로그 저장소 (인메모리)
 *
 * Append-Only: 저장만 가능, 수정/삭제 불가
 */
@Repository
public class AuditLogRepository {

    private final Map<String, AuditLog> store = new ConcurrentHashMap<>();

    /**
     * 저장 (Append-Only)
     */
    public void save(AuditLog log) {
        store.put(log.getId(), log);
    }

    /**
     * ID로 조회
     */
    public Optional<AuditLog> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /**
     * 전체 조회 (최신순 정렬)
     */
    public List<AuditLog> findAll() {
        return store.values().stream()
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 컨테이너별 조회
     */
    public List<AuditLog> findByContainerId(String containerId) {
        return store.values().stream()
                .filter(log -> containerId.equals(log.getContainerId()))
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Playbook별 조회
     */
    public List<AuditLog> findByPlaybookName(String playbookName) {
        return store.values().stream()
                .filter(log -> playbookName.equals(log.getPlaybookName()))
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 실행 결과별 조회
     */
    public List<AuditLog> findByExecutionResult(ExecutionResult result) {
        return store.values().stream()
                .filter(log -> log.getExecutionResult() == result)
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 시간 범위 조회
     */
    public List<AuditLog> findByTimeRange(LocalDateTime from, LocalDateTime to) {
        return store.values().stream()
                .filter(log -> !log.getTimestamp().isBefore(from) && !log.getTimestamp().isAfter(to))
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 최근 N개 조회
     */
    public List<AuditLog> findRecent(int limit) {
        return store.values().stream()
                .sorted(Comparator.comparing(AuditLog::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 전체 개수
     */
    public long count() {
        return store.size();
    }

    /**
     * 지정 시간 이전 로그 조회 (보존 정책용)
     */
    public List<AuditLog> findOlderThan(LocalDateTime cutoff) {
        return store.values().stream()
                .filter(log -> log.getTimestamp().isBefore(cutoff))
                .collect(Collectors.toList());
    }

    /**
     * 지정 시간 이전 로그 삭제 (보존 정책용 - 시스템 전용)
     *
     * 참고: 일반적인 delete는 제공하지 않음 (Append-Only)
     * 이 메서드는 보존 정책 스케줄러에서만 사용
     *
     * @return 삭제된 로그 수
     */
    public int deleteOlderThan(LocalDateTime cutoff) {
        List<String> toDelete = store.values().stream()
                .filter(log -> log.getTimestamp().isBefore(cutoff))
                .map(AuditLog::getId)
                .collect(Collectors.toList());

        toDelete.forEach(store::remove);
        return toDelete.size();
    }

    /**
     * 전체 삭제 (테스트용)
     */
    public void clear() {
        store.clear();
    }
}
