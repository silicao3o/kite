package com.lite_k8s.service;

import com.lite_k8s.model.ContainerDeathEvent;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    @Captor
    private ArgumentCaptor<MimeMessage> messageCaptor;

    private EmailNotificationService emailNotificationService;

    @BeforeEach
    void setUp() {
        emailNotificationService = new EmailNotificationService(mailSender);
        ReflectionTestUtils.setField(emailNotificationService, "recipientEmail", "admin@example.com");
        ReflectionTestUtils.setField(emailNotificationService, "fromEmail", "docker-monitor@example.com");
        ReflectionTestUtils.setField(emailNotificationService, "serverName", "Test-Server");
    }

    @Test
    @DisplayName("알림 이메일 전송 성공")
    void sendAlert_ShouldSendEmail() {
        // given
        ContainerDeathEvent event = createTestEvent();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        emailNotificationService.sendAlert(event);

        // then
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("7.17: 글로벌 관리자(yml)만 있으면 해당 주소로 1통 발송")
    void sendAlert_WithOnlyGlobalAdmin_ShouldSendOnce() {
        ContainerDeathEvent event = createTestEvent();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailNotificationService.sendAlert(event);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("7.17: 수신자가 없으면(글로벌 관리자 빈칸 + 구독 없음) 발송 안 함")
    void sendAlert_WhenNoRecipients_ShouldSkip() {
        ReflectionTestUtils.setField(emailNotificationService, "recipientEmail", "");
        ContainerDeathEvent event = createTestEvent();

        emailNotificationService.sendAlert(event);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("7.17: 글로벌 관리자 + 구독자 합산 — 중복 제거 후 각각 발송")
    void sendAlert_WithGlobalAdminAndSubscribers_ShouldSendToEach() {
        EmailSubscriptionService subService = mock(EmailSubscriptionService.class);
        when(subService.findRecipientsFor(any(), any(), anyBoolean()))
                .thenReturn(Set.of("alice@example.com", "bob@example.com"));
        ReflectionTestUtils.setField(emailNotificationService, "emailSubscriptionService", subService);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ContainerDeathEvent event = createTestEvent();

        emailNotificationService.sendAlert(event);

        // 글로벌(admin@example.com) + alice + bob = 3개
        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("7.17: 글로벌 관리자와 구독자 이메일이 중복되면 1통만 발송")
    void sendAlert_DuplicateEmail_ShouldDeduplicate() {
        EmailSubscriptionService subService = mock(EmailSubscriptionService.class);
        when(subService.findRecipientsFor(any(), any(), anyBoolean()))
                .thenReturn(Set.of("admin@example.com")); // yml 관리자와 동일
        ReflectionTestUtils.setField(emailNotificationService, "emailSubscriptionService", subService);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ContainerDeathEvent event = createTestEvent();

        emailNotificationService.sendAlert(event);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("7.17: intentional=true는 findRecipientsFor에 intentional 플래그 전달")
    void sendAlert_Intentional_ShouldPassIntentionalFlag() {
        EmailSubscriptionService subService = mock(EmailSubscriptionService.class);
        when(subService.findRecipientsFor(any(), any(), eq(true)))
                .thenReturn(Set.of());
        ReflectionTestUtils.setField(emailNotificationService, "emailSubscriptionService", subService);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ContainerDeathEvent event = createTestEvent();
        event.setIntentional(true);

        emailNotificationService.sendAlert(event);

        verify(subService).findRecipientsFor(any(), any(), eq(true));
        // 글로벌 관리자만 받음 (구독자 빈 Set 반환)
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("OOM 이벤트 알림 전송")
    void sendAlert_WhenOomKilled_ShouldSendOomAlert() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123def456")
                .containerName("memory-hungry-app")
                .imageName("java-app:1.0")
                .deathTime(LocalDateTime.now())
                .exitCode(137L)
                .oomKilled(true)
                .action("oom")
                .deathReason("[OOM Killed] 메모리 부족으로 인한 강제 종료")
                .lastLogs("java.lang.OutOfMemoryError: Java heap space")
                .build();

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        emailNotificationService.sendAlert(event);

        // then
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("여러 수신자에게 알림 전송 (7.17: 개별 발송)")
    void sendAlert_WithMultipleRecipients_ShouldSendToAll() {
        // given
        ReflectionTestUtils.setField(emailNotificationService, "recipientEmail", "admin1@example.com,admin2@example.com");
        ContainerDeathEvent event = createTestEvent();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        emailNotificationService.sendAlert(event);

        // then - 7.17: 수신자 수만큼 개별 발송
        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("이메일 전송 실패 시 예외 처리")
    void sendAlert_WhenMailFails_ShouldHandleException() {
        // given
        ContainerDeathEvent event = createTestEvent();
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(MimeMessage.class));

        // when & then - 예외가 발생해도 서비스가 중단되지 않아야 함
        emailNotificationService.sendAlert(event);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("null 값이 포함된 이벤트 처리")
    void sendAlert_WithNullValues_ShouldHandleGracefully() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("test-container")
                .imageName(null)
                .deathTime(LocalDateTime.now())
                .exitCode(null)
                .oomKilled(false)
                .action(null)
                .deathReason(null)
                .lastLogs(null)
                .build();

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        emailNotificationService.sendAlert(event);

        // then
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("긴 로그 truncate 처리")
    void sendAlert_WithLongLogs_ShouldTruncate() {
        // given
        StringBuilder longLogs = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            longLogs.append("Log line ").append(i).append(": Some log message\n");
        }

        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("verbose-app")
                .imageName("app:latest")
                .deathTime(LocalDateTime.now())
                .exitCode(1L)
                .oomKilled(false)
                .action("die")
                .deathReason("일반 에러")
                .lastLogs(longLogs.toString())
                .build();

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        emailNotificationService.sendAlert(event);

        // then
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("HTML 특수문자 이스케이프 처리")
    void sendAlert_WithHtmlSpecialChars_ShouldEscape() {
        // given
        ContainerDeathEvent event = ContainerDeathEvent.builder()
                .containerId("abc123")
                .containerName("<script>alert('xss')</script>")
                .imageName("app:latest")
                .deathTime(LocalDateTime.now())
                .exitCode(1L)
                .oomKilled(false)
                .action("die")
                .deathReason("Error: <invalid> & 'test'")
                .lastLogs("Log: <error> message & details")
                .build();

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        emailNotificationService.sendAlert(event);

        // then
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("최대 재시작 횟수 초과 시 알림 전송")
    void sendMaxRestartsExceededAlert_ShouldSendEmail() {
        // given
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        emailNotificationService.sendMaxRestartsExceededAlert("web-server", "abc123", 3);

        // then
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("재시작 실패 시 알림 전송")
    void sendRestartFailedAlert_ShouldSendEmail() {
        // given
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // when
        emailNotificationService.sendRestartFailedAlert("web-server", "abc123");

        // then
        verify(mailSender).send(any(MimeMessage.class));
    }

    private ContainerDeathEvent createTestEvent() {
        return ContainerDeathEvent.builder()
                .containerId("abc123def456")
                .containerName("test-container")
                .imageName("nginx:latest")
                .deathTime(LocalDateTime.of(2026, 3, 10, 14, 30, 0))
                .exitCode(137L)
                .oomKilled(false)
                .action("die")
                .deathReason("[Exit Code: 137] SIGKILL - 강제 종료됨")
                .lastLogs("2026-03-10T14:29:55Z ERROR Connection refused\n2026-03-10T14:29:59Z FATAL Shutting down")
                .build();
    }
}
