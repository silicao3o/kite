package com.lite_k8s.health;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.TimeUnit;

/**
 * Probe 실행기
 * HTTP / TCP / EXEC 세 가지 probe 타입 지원
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProbeRunner {

    private final DockerClient dockerClient;

    // ── HTTP Probe ──────────────────────────────────────────────────

    public ProbeResult runHttpProbe(String host, int port, String path) {
        String url = buildHttpUrl(host, port, path);
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");
            int status = conn.getResponseCode();
            long elapsed = System.currentTimeMillis() - start;

            if (status >= 200 && status < 400) {
                log.debug("HTTP probe 성공: {} → {} ({}ms)", url, status, elapsed);
                return ProbeResult.success(elapsed);
            } else {
                log.debug("HTTP probe 실패: {} → HTTP {}", url, status);
                return ProbeResult.failure("HTTP " + status);
            }
        } catch (Exception e) {
            log.debug("HTTP probe 오류: {}: {}", url, e.getMessage());
            return ProbeResult.failure(e.getMessage());
        }
    }

    // ── TCP Probe ───────────────────────────────────────────────────

    public ProbeResult runTcpProbe(String host, ProbeConfig config) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(
                    new java.net.InetSocketAddress(host, config.getPort()),
                    config.getTimeoutSeconds() * 1000
            );
            long elapsed = System.currentTimeMillis() - start;
            log.debug("TCP probe 성공: {}:{} ({}ms)", host, config.getPort(), elapsed);
            return ProbeResult.success(elapsed);
        } catch (Exception e) {
            log.debug("TCP probe 실패: {}:{}: {}", host, config.getPort(), e.getMessage());
            return ProbeResult.failure(e.getMessage());
        }
    }

    // ── Exec Probe ──────────────────────────────────────────────────

    public ProbeResult runExecProbe(String containerId, ProbeConfig config) {
        if (config.getCommand() == null || config.getCommand().length == 0) {
            return ProbeResult.failure("exec probe: 명령어 없음");
        }

        long start = System.currentTimeMillis();
        try {
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(containerId)
                    .withCmd(config.getCommand())
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            dockerClient.execStartCmd(execCreate.getId())
                    .exec(new ExecStartResultCallback(stdout, stderr))
                    .awaitCompletion(config.getTimeoutSeconds(), TimeUnit.SECONDS);

            Long exitCode = dockerClient.inspectExecCmd(execCreate.getId())
                    .exec()
                    .getExitCodeLong();

            long elapsed = System.currentTimeMillis() - start;

            if (exitCode != null && exitCode == 0) {
                log.debug("Exec probe 성공: {} ({}ms)", containerId, elapsed);
                return ProbeResult.success(elapsed);
            } else {
                String errMsg = stderr.toString().trim();
                log.debug("Exec probe 실패: {} exitCode={}", containerId, exitCode);
                return ProbeResult.failure("exitCode=" + exitCode + (errMsg.isEmpty() ? "" : " " + errMsg));
            }
        } catch (Exception e) {
            log.debug("Exec probe 오류: {}: {}", containerId, e.getMessage());
            return ProbeResult.failure(e.getMessage());
        }
    }

    // ── 유틸 ────────────────────────────────────────────────────────

    String buildHttpUrl(String host, int port, String path) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return "http://" + host + ":" + port + path;
    }

    public ProbeResult run(String host, String containerId, ProbeConfig config) {
        return switch (config.getType()) {
            case HTTP -> runHttpProbe(host, config.getPort(), config.getPath());
            case TCP -> runTcpProbe(host, config);
            case EXEC -> runExecProbe(containerId, config);
        };
    }
}
