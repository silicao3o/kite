package com.lite_k8s.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookLoaderTest {

    private PlaybookLoader loader;

    @BeforeEach
    void setUp() {
        loader = new PlaybookLoader(new PlaybookParser());
    }

    @Test
    @DisplayName("디렉토리에서 모든 Playbook YAML 파일을 로드한다")
    void shouldLoadAllPlaybooksFromDirectory(@TempDir Path tempDir) throws IOException {
        // given
        String playbook1 = """
                name: playbook-1
                trigger:
                  event: container.die
                actions:
                  - name: restart
                    type: container.restart
                """;
        String playbook2 = """
                name: playbook-2
                trigger:
                  event: container.oom
                actions:
                  - name: notify
                    type: email
                """;

        Files.writeString(tempDir.resolve("playbook1.yml"), playbook1);
        Files.writeString(tempDir.resolve("playbook2.yaml"), playbook2);
        Files.writeString(tempDir.resolve("notaplaybook.txt"), "ignored");

        // when
        List<Playbook> playbooks = loader.loadFromDirectory(tempDir);

        // then
        assertThat(playbooks).hasSize(2);
        assertThat(playbooks).extracting(Playbook::getName)
                .containsExactlyInAnyOrder("playbook-1", "playbook-2");
    }

    @Test
    @DisplayName("Playbook을 로드하여 Registry에 등록한다")
    void shouldLoadAndRegisterPlaybooks(@TempDir Path tempDir) throws IOException {
        // given
        String playbook = """
                name: test-playbook
                trigger:
                  event: container.die
                actions:
                  - name: restart
                    type: container.restart
                """;
        Files.writeString(tempDir.resolve("test.yml"), playbook);

        PlaybookRegistry registry = new PlaybookRegistry();

        // when
        loader.loadAndRegister(tempDir, registry);

        // then
        assertThat(registry.findByName("test-playbook")).isPresent();
        assertThat(registry.findByEvent("container.die")).hasSize(1);
    }
}
