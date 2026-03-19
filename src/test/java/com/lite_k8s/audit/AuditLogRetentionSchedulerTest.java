package com.lite_k8s.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogRetentionSchedulerTest {

    @Mock
    private AuditLogRepository repository;

    private AuditLogRetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AuditLogRetentionScheduler(repository, 180);
    }

    @Test
    @DisplayName("보존 기간이 지난 로그를 삭제한다")
    void shouldDeleteExpiredLogs() {
        // given
        when(repository.deleteOlderThan(any())).thenReturn(5);

        // when
        scheduler.cleanupExpiredLogs();

        // then
        verify(repository, times(1)).deleteOlderThan(any());
    }

    @Test
    @DisplayName("180일 이전 로그를 삭제 대상으로 한다")
    void shouldUse180DaysRetentionPeriod() {
        // given
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(repository.deleteOlderThan(any())).thenReturn(0);

        // when
        scheduler.cleanupExpiredLogs();

        // then
        verify(repository).deleteOlderThan(captor.capture());
        LocalDateTime cutoff = captor.getValue();

        // cutoff는 현재 시간 - 180일 근처여야 함
        LocalDateTime expected = LocalDateTime.now().minusDays(180);
        assertThat(cutoff).isBetween(expected.minusMinutes(1), expected.plusMinutes(1));
    }

    @Test
    @DisplayName("삭제된 로그가 없으면 로그를 남기지 않는다")
    void shouldNotLogWhenNoLogsDeleted() {
        // given
        when(repository.deleteOlderThan(any())).thenReturn(0);

        // when
        scheduler.cleanupExpiredLogs();

        // then
        verify(repository, times(1)).deleteOlderThan(any());
    }

    @Test
    @DisplayName("보존 기간을 설정할 수 있다")
    void shouldAllowCustomRetentionDays() {
        // given
        AuditLogRetentionScheduler customScheduler = new AuditLogRetentionScheduler(repository, 90);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(repository.deleteOlderThan(any())).thenReturn(0);

        // when
        customScheduler.cleanupExpiredLogs();

        // then
        verify(repository).deleteOlderThan(captor.capture());
        LocalDateTime cutoff = captor.getValue();

        LocalDateTime expected = LocalDateTime.now().minusDays(90);
        assertThat(cutoff).isBetween(expected.minusMinutes(1), expected.plusMinutes(1));
    }
}
