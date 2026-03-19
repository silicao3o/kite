package com.lite_k8s.playbook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookParserTest {

    @Test
    @DisplayName("YAML 문자열을 Playbook으로 파싱한다")
    void shouldParseYamlStringToPlaybook() {
        // given
        String yaml = """
                name: container-restart
                description: 컨테이너 재시작 플레이북
                riskLevel: LOW
                trigger:
                  event: container.die
                  conditions:
                    exitCode: "1"
                actions:
                  - name: restart
                    type: container.restart
                    params:
                      containerId: "{{containerId}}"
                """;
        PlaybookParser parser = new PlaybookParser();

        // when
        Playbook playbook = parser.parse(yaml);

        // then
        assertThat(playbook.getName()).isEqualTo("container-restart");
        assertThat(playbook.getDescription()).isEqualTo("컨테이너 재시작 플레이북");
        assertThat(playbook.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(playbook.getTrigger().getEvent()).isEqualTo("container.die");
        assertThat(playbook.getTrigger().getConditions()).containsEntry("exitCode", "1");
        assertThat(playbook.getActions()).hasSize(1);
        assertThat(playbook.getActions().get(0).getType()).isEqualTo("container.restart");
    }

    @Test
    @DisplayName("여러 액션을 가진 Playbook을 파싱한다")
    void shouldParsePlaybookWithMultipleActions() {
        // given
        String yaml = """
                name: oom-recovery
                riskLevel: MEDIUM
                trigger:
                  event: container.oom
                actions:
                  - name: wait
                    type: delay
                    params:
                      seconds: "10"
                  - name: restart
                    type: container.restart
                    params:
                      containerId: "{{containerId}}"
                  - name: notify
                    type: email
                    params:
                      subject: "OOM Recovery"
                """;
        PlaybookParser parser = new PlaybookParser();

        // when
        Playbook playbook = parser.parse(yaml);

        // then
        assertThat(playbook.getActions()).hasSize(3);
        assertThat(playbook.getActions().get(0).getType()).isEqualTo("delay");
        assertThat(playbook.getActions().get(1).getType()).isEqualTo("container.restart");
        assertThat(playbook.getActions().get(2).getType()).isEqualTo("email");
    }

    @Test
    @DisplayName("조건부 실행을 가진 액션을 파싱한다")
    void shouldParseActionWithConditionalExecution() {
        // given
        String yaml = """
                name: conditional-restart
                trigger:
                  event: container.die
                actions:
                  - name: restart
                    type: container.restart
                    when: "{{restartCount}} < 3"
                    params:
                      containerId: "{{containerId}}"
                """;
        PlaybookParser parser = new PlaybookParser();

        // when
        Playbook playbook = parser.parse(yaml);

        // then
        assertThat(playbook.getActions().get(0).getWhen()).isEqualTo("{{restartCount}} < 3");
    }

    @Test
    @DisplayName("파일에서 Playbook을 로드한다")
    void shouldLoadPlaybookFromFile(@TempDir Path tempDir) throws IOException {
        // given
        String yaml = """
                name: file-loaded-playbook
                description: 파일에서 로드된 플레이북
                trigger:
                  event: container.die
                actions:
                  - name: restart
                    type: container.restart
                """;
        Path playbookFile = tempDir.resolve("test-playbook.yml");
        Files.writeString(playbookFile, yaml);
        PlaybookParser parser = new PlaybookParser();

        // when
        Playbook playbook = parser.parseFile(playbookFile);

        // then
        assertThat(playbook.getName()).isEqualTo("file-loaded-playbook");
        assertThat(playbook.getDescription()).isEqualTo("파일에서 로드된 플레이북");
    }
}
