package com.lite_k8s.analyzer;

import com.lite_k8s.model.ContainerDeathEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExitCodeAnalyzer {

    private static final Map<Long, String> EXIT_CODE_DESCRIPTIONS = Map.ofEntries(
            Map.entry(0L, "정상 종료 - 프로세스가 성공적으로 완료됨"),
            Map.entry(1L, "일반 에러 - 애플리케이션 에러 또는 잘못된 명령"),
            Map.entry(2L, "쉘 내장 명령 오용 - 잘못된 쉘 명령 사용"),
            Map.entry(126L, "명령 실행 불가 - 권한 문제 또는 실행 파일이 아님"),
            Map.entry(127L, "명령을 찾을 수 없음 - PATH에 명령이 없거나 오타"),
            Map.entry(128L, "잘못된 종료 코드 - exit에 잘못된 인자 전달"),
            Map.entry(130L, "Ctrl+C (SIGINT) - 사용자가 인터럽트 신호로 종료"),
            Map.entry(137L, "SIGKILL (9) - 강제 종료됨 (OOM Killer 또는 docker kill)"),
            Map.entry(139L, "SIGSEGV - 세그멘테이션 폴트 (메모리 접근 위반)"),
            Map.entry(143L, "SIGTERM (15) - 정상 종료 요청 (docker stop)"),
            Map.entry(255L, "Exit 상태 범위 초과 - 비정상적인 종료 코드")
    );

    public String analyze(ContainerDeathEvent event) {
        StringBuilder reason = new StringBuilder();

        // OOM Killed 체크
        if (event.isOomKilled()) {
            reason.append("[OOM Killed] 메모리 부족으로 인한 강제 종료\n");
            reason.append("- 컨테이너 메모리 제한 증가를 고려하세요\n");
            reason.append("- 애플리케이션 메모리 누수 점검 필요");
            return reason.toString();
        }

        // Exit Code 분석
        Long exitCode = event.getExitCode();
        if (exitCode == null) {
            return "종료 코드를 확인할 수 없음";
        }

        String description = EXIT_CODE_DESCRIPTIONS.getOrDefault(exitCode,
                "알 수 없는 종료 코드: " + exitCode);

        reason.append("[Exit Code: ").append(exitCode).append("] ").append(description);

        // 추가 분석 정보
        appendAdditionalAnalysis(reason, exitCode, event);

        return reason.toString();
    }

    private void appendAdditionalAnalysis(StringBuilder reason, Long exitCode, ContainerDeathEvent event) {
        String action = event.getAction();

        if (exitCode == 137) {
            reason.append("\n\n가능한 원인:");
            reason.append("\n- docker kill 명령으로 강제 종료됨");
            reason.append("\n- 시스템 OOM Killer에 의해 종료됨");
            reason.append("\n- 메모리 제한 초과로 인한 종료");
            reason.append("\n- Kubernetes/Orchestrator에 의한 강제 종료");
        } else if (exitCode == 143) {
            reason.append("\n\n가능한 원인:");
            reason.append("\n- docker stop 명령으로 정상 종료 요청됨");
            reason.append("\n- Orchestrator에 의한 정상 종료");
            reason.append("\n- 시스템 종료 신호 수신");
        } else if (exitCode == 1) {
            reason.append("\n\n가능한 원인:");
            reason.append("\n- 애플리케이션 내부 에러 (Exception 발생)");
            reason.append("\n- 설정 파일 오류");
            reason.append("\n- 의존성 연결 실패 (DB, 외부 API 등)");
            reason.append("\n\n로그를 확인하여 정확한 원인을 파악하세요.");
        } else if (exitCode == 139) {
            reason.append("\n\n가능한 원인:");
            reason.append("\n- 잘못된 메모리 접근 (포인터 오류)");
            reason.append("\n- 네이티브 라이브러리 충돌");
            reason.append("\n- 스택 오버플로우");
        }

        // 이벤트 타입에 따른 추가 정보
        if ("oom".equalsIgnoreCase(action)) {
            reason.append("\n\n[주의] OOM 이벤트로 감지됨 - 메모리 관련 문제 확실");
        } else if ("kill".equalsIgnoreCase(action)) {
            reason.append("\n\n[참고] kill 이벤트로 감지됨 - 외부에서 강제 종료됨");
        }
    }
}
