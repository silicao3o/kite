package com.lite_k8s.envprofile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvProfileAuditServiceTest {

    @Mock private EnvProfileAuditLogRepository repository;
    @InjectMocks private EnvProfileAuditService auditService;

    @Test
    @DisplayName("프로파일 생성 시 CREATED 감사 로그 기록")
    void logCreated() {
        auditService.logCreated("p1", "db-operia", "admin");

        ArgumentCaptor<EnvProfileAuditLog> captor = ArgumentCaptor.forClass(EnvProfileAuditLog.class);
        verify(repository).save(captor.capture());

        EnvProfileAuditLog log = captor.getValue();
        assertThat(log.getProfileId()).isEqualTo("p1");
        assertThat(log.getProfileName()).isEqualTo("db-operia");
        assertThat(log.getAction()).isEqualTo(EnvProfileAuditLog.Action.CREATED);
        assertThat(log.getActor()).isEqualTo("admin");
    }

    @Test
    @DisplayName("엔트리 수정 시 ENTRY_UPDATED 로그 + 변경 key만 기록 (value 없음)")
    void logEntryUpdated() {
        auditService.logEntryUpdated("p1", "db-operia", "DB_PASSWORD", "hashBefore", "hashAfter", "admin");

        ArgumentCaptor<EnvProfileAuditLog> captor = ArgumentCaptor.forClass(EnvProfileAuditLog.class);
        verify(repository).save(captor.capture());

        EnvProfileAuditLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo(EnvProfileAuditLog.Action.ENTRY_UPDATED);
        assertThat(log.getChangedKeys()).isEqualTo("DB_PASSWORD");
        assertThat(log.getBeforeHash()).isEqualTo("hashBefore");
        assertThat(log.getAfterHash()).isEqualTo("hashAfter");
    }

    @Test
    @DisplayName("컨테이너 참조 시 REFERENCED 로그 기록")
    void logReferenced() {
        auditService.logReferenced("p1", "db-operia", "quvi-operia", "system");

        ArgumentCaptor<EnvProfileAuditLog> captor = ArgumentCaptor.forClass(EnvProfileAuditLog.class);
        verify(repository).save(captor.capture());

        EnvProfileAuditLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo(EnvProfileAuditLog.Action.REFERENCED);
        assertThat(log.getReferencedContainerName()).isEqualTo("quvi-operia");
    }
}
