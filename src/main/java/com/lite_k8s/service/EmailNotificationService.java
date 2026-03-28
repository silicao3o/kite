package com.lite_k8s.service;

import com.lite_k8s.model.ContainerDeathEvent;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${docker.monitor.notification.email.to}")
    private String recipientEmail;

    @Value("${docker.monitor.notification.email.from:docker-monitor@localhost}")
    private String fromEmail;

    @Value("${docker.monitor.server-name:Unknown Server}")
    private String serverName;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String[] parseRecipients() {
        if (recipientEmail == null || recipientEmail.isBlank()) return new String[0];
        return Arrays.stream(recipientEmail.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private String sanitizeHeader(String value) {
        if (value == null) return "";
        return value.replaceAll("[\r\n]", "");
    }

    @Async
    public void sendAlert(ContainerDeathEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(parseRecipients());
            helper.setSubject(buildSubject(event));
            helper.setText(buildHtmlContent(event), true);

            mailSender.send(message);
            log.info("알림 이메일 전송 완료: {}", event.getContainerName());

        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", event.getContainerName(), e);
        }
    }

    @Async
    public void sendMaxRestartsExceededAlert(String containerName, String containerId, int maxRestarts) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(parseRecipients());
            helper.setSubject(String.format("[MAX RESTARTS] %s - 최대 재시작 횟수 초과 (%s)", sanitizeHeader(containerName), sanitizeHeader(serverName)));
            helper.setText(buildMaxRestartsExceededContent(containerName, containerId, maxRestarts), true);

            mailSender.send(message);
            log.info("최대 재시작 초과 알림 전송 완료: {}", containerName);

        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", containerName, e);
        }
    }

    @Async
    public void sendRestartFailedAlert(String containerName, String containerId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(parseRecipients());
            helper.setSubject(String.format("[RESTART FAILED] %s - 자가치유 실패 (%s)", sanitizeHeader(containerName), sanitizeHeader(serverName)));
            helper.setText(buildRestartFailedContent(containerName, containerId), true);

            mailSender.send(message);
            log.info("재시작 실패 알림 전송 완료: {}", containerName);

        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", containerName, e);
        }
    }

    @Async
    public void sendCpuThresholdAlert(String containerName, String containerId, double currentCpu, double threshold) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(parseRecipients());
            helper.setSubject(String.format("[CPU HIGH] %s - CPU 사용률 %.1f%% (%s)", sanitizeHeader(containerName), currentCpu, sanitizeHeader(serverName)));
            helper.setText(buildThresholdAlertContent("CPU", containerName, containerId, currentCpu, threshold), true);

            mailSender.send(message);
            log.info("CPU 임계치 알림 전송 완료: {} ({}%)", containerName, String.format("%.1f", currentCpu));

        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", containerName, e);
        }
    }

    @Async
    public void sendMemoryThresholdAlert(String containerName, String containerId, double currentMemory, double threshold) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(parseRecipients());
            helper.setSubject(String.format("[MEMORY HIGH] %s - 메모리 사용률 %.1f%% (%s)", sanitizeHeader(containerName), currentMemory, sanitizeHeader(serverName)));
            helper.setText(buildThresholdAlertContent("메모리", containerName, containerId, currentMemory, threshold), true);

            mailSender.send(message);
            log.info("메모리 임계치 알림 전송 완료: {} ({}%)", containerName, String.format("%.1f", currentMemory));

        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", containerName, e);
        }
    }

    @Async
    public void sendRestartLoopAlert(String containerName, String containerId, int restartCount, int windowMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(parseRecipients());
            helper.setSubject(String.format("[RESTART LOOP] %s - %d분 내 %d회 재시작 (%s)",
                    sanitizeHeader(containerName), windowMinutes, restartCount, sanitizeHeader(serverName)));
            helper.setText(buildRestartLoopContent(containerName, containerId, restartCount, windowMinutes), true);

            mailSender.send(message);
            log.info("재시작 반복 알림 전송 완료: {} ({}회)", containerName, restartCount);

        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", containerName, e);
        }
    }

    private String buildSubject(ContainerDeathEvent event) {
        String emoji = event.isOomKilled() ? "[OOM]" : "[DOWN]";
        return String.format("%s 컨테이너 종료 알림: %s (%s)",
                emoji, sanitizeHeader(event.getContainerName()), sanitizeHeader(serverName));
    }

    private String buildHtmlContent(ContainerDeathEvent event) {
        String severityColor = getSeverityColor(event);
        String truncatedLogs = truncateLogs(event.getLastLogs(), 100);
        String shortId = escapeHtml(event.getContainerId() != null ? event.getContainerId().substring(0, Math.min(12, event.getContainerId().length())) : "N/A");
        String exitBadge = event.getExitCode() != null && event.getExitCode() != 0 ? "badge-danger" : "badge-warning";

        String body = String.format("""
                <table class="info-table">
                    <tr><th>서버</th><td>%s</td></tr>
                    <tr><th>컨테이너 이름</th><td><strong>%s</strong></td></tr>
                    <tr><th>컨테이너 ID</th><td><code>%s</code></td></tr>
                    <tr><th>이미지</th><td>%s</td></tr>
                    <tr><th>종료 시간</th><td>%s</td></tr>
                    <tr><th>Exit Code</th><td><span class="badge %s">%s</span></td></tr>
                    <tr><th>이벤트 타입</th><td>%s</td></tr>
                    <tr><th>OOM Killed</th><td>%s</td></tr>
                </table>
                <div class="reason-box">
                    <h3>종료 원인 분석</h3>
                    <pre style="margin: 0; white-space: pre-wrap;">%s</pre>
                </div>
                <h3>마지막 로그 (최근 100줄)</h3>
                <div class="log-box">%s</div>""",
                escapeHtml(serverName),
                escapeHtml(event.getContainerName()),
                shortId,
                escapeHtml(event.getImageName() != null ? event.getImageName() : "N/A"),
                event.getDeathTime() != null ? event.getDeathTime().format(DATE_FORMAT) : "N/A",
                exitBadge,
                event.getExitCode() != null ? event.getExitCode() : "N/A",
                escapeHtml(event.getAction() != null ? event.getAction().toUpperCase() : "UNKNOWN"),
                event.isOomKilled() ? "<span class='badge badge-danger'>YES</span>" : "NO",
                escapeHtml(event.getDeathReason() != null ? event.getDeathReason() : "분석 정보 없음"),
                escapeHtml(truncatedLogs));

        String extraStyles = """
                        .reason-box { background: #fff3cd; border: 1px solid #ffc107; border-radius: 4px; padding: 15px; margin-bottom: 20px; }
                        .reason-box h3 { margin-top: 0; color: #856404; }
                        .log-box { background: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 4px; font-family: 'Consolas', monospace; font-size: 12px; white-space: pre-wrap; word-wrap: break-word; max-height: 400px; overflow-y: auto; }
                        .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
                        .badge-danger { background: #dc3545; color: white; }
                        .badge-warning { background: #ffc107; color: black; }
                """;
        return buildEmailHtml("700px", severityColor, "Docker 컨테이너 종료 알림", extraStyles, body);
    }

    private String getSeverityColor(ContainerDeathEvent event) {
        if (event.isOomKilled()) {
            return "#dc3545"; // 빨간색 - OOM
        }
        Long exitCode = event.getExitCode();
        if (exitCode == null || exitCode == 0 || exitCode == 143) {
            return "#ffc107"; // 노란색 - 정상/예상된 종료
        }
        return "#dc3545"; // 빨간색 - 비정상 종료
    }

    private String truncateLogs(String logs, int maxLines) {
        if (logs == null || logs.isEmpty()) {
            return "로그 없음";
        }
        String[] lines = logs.split("\n");
        if (lines.length <= maxLines) {
            return logs;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("... (").append(lines.length - maxLines).append("줄 생략) ...\n\n");
        for (int i = lines.length - maxLines; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    private String buildEmailHtml(String maxWidth, String headerColor, String title,
                                   String extraStyles, String bodyContent) {
        return "<!DOCTYPE html>\n<html>\n<head>\n    <meta charset=\"UTF-8\">\n    <style>\n"
                + "        body { font-family: 'Segoe UI', Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }\n"
                + "        .container { max-width: " + maxWidth + "; margin: 0 auto; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); overflow: hidden; }\n"
                + "        .header { background: " + headerColor + "; color: white; padding: 20px; text-align: center; }\n"
                + "        .header h1 { margin: 0; font-size: 24px; }\n"
                + "        .content { padding: 20px; }\n"
                + "        .info-table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }\n"
                + "        .info-table th { text-align: left; padding: 10px; background: #f8f9fa; border-bottom: 1px solid #dee2e6; width: 30%; }\n"
                + "        .info-table td { padding: 10px; border-bottom: 1px solid #dee2e6; }\n"
                + "        .footer { background: #f8f9fa; padding: 15px; text-align: center; color: #6c757d; font-size: 12px; }\n"
                + extraStyles
                + "    </style>\n</head>\n<body>\n    <div class=\"container\">\n"
                + "        <div class=\"header\"><h1>" + title + "</h1></div>\n"
                + "        <div class=\"content\">" + bodyContent + "</div>\n"
                + "        <div class=\"footer\">Docker Monitor Service | 자동 생성된 알림입니다</div>\n"
                + "    </div>\n</body>\n</html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String buildMaxRestartsExceededContent(String containerName, String containerId, int maxRestarts) {
        String shortId = escapeHtml(containerId != null ? containerId.substring(0, Math.min(12, containerId.length())) : "N/A");
        String body = String.format("""
                <table class="info-table">
                    <tr><th>서버</th><td>%s</td></tr>
                    <tr><th>컨테이너 이름</th><td><strong>%s</strong></td></tr>
                    <tr><th>컨테이너 ID</th><td><code>%s</code></td></tr>
                    <tr><th>최대 재시작 횟수</th><td>%d회</td></tr>
                </table>
                <div class="warning-box">
                    <strong>주의:</strong> 이 컨테이너는 최대 재시작 횟수에 도달하여 더 이상 자동 재시작되지 않습니다.
                    수동으로 확인이 필요합니다.
                </div>""",
                escapeHtml(serverName), escapeHtml(containerName), shortId, maxRestarts);
        return buildEmailHtml("600px", "#dc3545", "최대 재시작 횟수 초과",
                "        .warning-box { background: #f8d7da; border: 1px solid #f5c6cb; border-radius: 4px; padding: 15px; margin-top: 20px; }\n",
                body);
    }

    private String buildThresholdAlertContent(String type, String containerName, String containerId,
                                                double current, double threshold) {
        String color = current > threshold * 1.2 ? "#dc3545" : "#ffc107";
        String shortId = escapeHtml(containerId != null ? containerId.substring(0, Math.min(12, containerId.length())) : "N/A");
        String body = String.format("""
                <table class="info-table">
                    <tr><th>서버</th><td>%s</td></tr>
                    <tr><th>컨테이너 이름</th><td><strong>%s</strong></td></tr>
                    <tr><th>컨테이너 ID</th><td><code>%s</code></td></tr>
                    <tr><th>임계치</th><td>%.1f%%</td></tr>
                </table>
                <div class="metric-box">
                    <div class="metric-value">%.1f%%</div>
                    <div class="metric-label">현재 %s 사용률</div>
                </div>""",
                escapeHtml(serverName), escapeHtml(containerName), shortId, threshold, current, type);
        String extraStyles = "        .metric-box { background: #f8f9fa; border-radius: 8px; padding: 20px; margin-top: 20px; text-align: center; }\n"
                + "        .metric-value { font-size: 48px; font-weight: bold; color: " + color + "; }\n"
                + "        .metric-label { color: #6c757d; margin-top: 5px; }\n";
        return buildEmailHtml("600px", color, type + " 사용률 경고", extraStyles, body);
    }

    @Async
    public void sendCrashLoopStoppedAlert(String containerName, String containerId, int restartCount, int windowMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(parseRecipients());
            helper.setSubject(String.format("[CRASH LOOP] %s - 강제 정지됨 (%d분 내 %d회 재시작) (%s)",
                    sanitizeHeader(containerName), windowMinutes, restartCount, sanitizeHeader(serverName)));
            helper.setText(buildCrashLoopStoppedContent(containerName, containerId, restartCount, windowMinutes), true);

            mailSender.send(message);
            log.info("Crash Loop 강제 정지 알림 전송 완료: {} ({}회)", containerName, restartCount);

        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", containerName, e);
        }
    }

    private String buildCrashLoopStoppedContent(String containerName, String containerId, int restartCount, int windowMinutes) {
        String shortId = escapeHtml(containerId != null ? containerId.substring(0, Math.min(12, containerId.length())) : "N/A");
        String body = String.format("""
                <table class="info-table">
                    <tr><th>서버</th><td>%s</td></tr>
                    <tr><th>컨테이너 이름</th><td><strong>%s</strong></td></tr>
                    <tr><th>컨테이너 ID</th><td><code>%s</code></td></tr>
                    <tr><th>재시작 횟수</th><td><strong>%d회</strong> (최근 %d분 내)</td></tr>
                    <tr><th>조치</th><td><span style="color:#dc3545;font-weight:bold;">컨테이너 강제 정지됨</span></td></tr>
                </table>
                <div class="warning-box">
                    <strong>Crash Loop 감지:</strong> 컨테이너가 %d분 내 %d회 재시작되어 강제 정지되었습니다.<br>
                    애플리케이션 오류(DB 스키마 불일치, 설정 오류 등)를 확인한 후 수동으로 재시작하세요.
                </div>""",
                escapeHtml(serverName), escapeHtml(containerName), shortId, restartCount, windowMinutes, windowMinutes, restartCount);
        return buildEmailHtml("600px", "#dc3545", "Crash Loop — 컨테이너 강제 정지",
                "        .warning-box { background: #f8d7da; border: 1px solid #f5c6cb; border-radius: 4px; padding: 15px; margin-top: 20px; }\n",
                body);
    }

    private String buildRestartLoopContent(String containerName, String containerId, int restartCount, int windowMinutes) {
        String shortId = escapeHtml(containerId != null ? containerId.substring(0, Math.min(12, containerId.length())) : "N/A");
        String body = String.format("""
                <table class="info-table">
                    <tr><th>서버</th><td>%s</td></tr>
                    <tr><th>컨테이너 이름</th><td><strong>%s</strong></td></tr>
                    <tr><th>컨테이너 ID</th><td><code>%s</code></td></tr>
                    <tr><th>재시작 횟수</th><td><strong>%d회</strong> (최근 %d분 내)</td></tr>
                </table>
                <div class="warning-box">
                    <strong>주의:</strong> 컨테이너가 짧은 시간 내에 여러 번 재시작되고 있습니다.
                    애플리케이션 오류나 리소스 문제를 점검해 주세요.
                </div>""",
                escapeHtml(serverName), escapeHtml(containerName), shortId, restartCount, windowMinutes);
        return buildEmailHtml("600px", "#dc3545", "재시작 반복 감지",
                "        .warning-box { background: #f8d7da; border: 1px solid #f5c6cb; border-radius: 4px; padding: 15px; margin-top: 20px; }\n",
                body);
    }

    private String buildRestartFailedContent(String containerName, String containerId) {
        String shortId = escapeHtml(containerId != null ? containerId.substring(0, Math.min(12, containerId.length())) : "N/A");
        String body = String.format("""
                <table class="info-table">
                    <tr><th>서버</th><td>%s</td></tr>
                    <tr><th>컨테이너 이름</th><td><strong>%s</strong></td></tr>
                    <tr><th>컨테이너 ID</th><td><code>%s</code></td></tr>
                </table>
                <div class="warning-box">
                    <strong>주의:</strong> 컨테이너 재시작이 실패했습니다. 수동으로 확인이 필요합니다.
                </div>""",
                escapeHtml(serverName), escapeHtml(containerName), shortId);
        return buildEmailHtml("600px", "#dc3545", "자가치유 실패",
                "        .warning-box { background: #f8d7da; border: 1px solid #f5c6cb; border-radius: 4px; padding: 15px; margin-top: 20px; }\n",
                body);
    }
}
