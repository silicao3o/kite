package com.lite_k8s.incident;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentReportTest {

    @Test
    @DisplayName("IncidentReport를 생성하면 ID와 생성 시각이 자동 설정된다")
    void shouldAutoAssignIdAndTimestampOnCreation() {
        // given
        LocalDateTime before = LocalDateTime.now();

        // when
        IncidentReport report = IncidentReport.builder()
                .containerId("abc123")
                .containerName("web-server")
                .summary("컨테이너가 OOM으로 종료되었습니다")
                .build();

        // then
        assertThat(report.getId()).isNotNull();
        assertThat(report.getCreatedAt()).isNotNull();
        assertThat(report.getCreatedAt()).isAfterOrEqualTo(before);
        assertThat(report.getStatus()).isEqualTo(IncidentReport.Status.OPEN);
    }
}
