package com.lite_k8s.desired;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DesiredStateService {

    private final DesiredServiceSpecRepository repository;

    /** DB에서 enabled=true인 스펙을 ServiceSpec으로 변환하여 반환 */
    public List<DesiredStateProperties.ServiceSpec> findAllActive() {
        return repository.findByEnabled(true).stream()
                .map(DesiredServiceSpecEntity::toServiceSpec)
                .collect(Collectors.toList());
    }

    /** 새 스펙 저장 */
    public DesiredServiceSpecEntity save(DesiredServiceSpecEntity entity) {
        return repository.save(entity);
    }

    /** soft delete: enabled=false */
    public void disable(String id) {
        repository.findById(id).ifPresent(entity -> {
            entity.setEnabled(false);
            repository.save(entity);
        });
    }

    /** 전체 엔티티 목록 (DB) */
    public List<DesiredServiceSpecEntity> findAll() {
        return repository.findAll();
    }

    /** ID로 조회 */
    public java.util.Optional<DesiredServiceSpecEntity> findById(String id) {
        return repository.findById(id);
    }
}
