package com.lite_k8s.playbook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Playbook 파일 로더
 *
 * 디렉토리에서 YAML 파일을 로드하여 Playbook으로 파싱
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaybookLoader {

    private final PlaybookParser parser;

    /**
     * 디렉토리에서 모든 Playbook YAML 파일 로드
     */
    public List<Playbook> loadFromDirectory(Path directory) throws IOException {
        List<Playbook> playbooks = new ArrayList<>();

        try (Stream<Path> files = Files.list(directory)) {
            files.filter(this::isYamlFile)
                    .forEach(file -> {
                        try {
                            Playbook playbook = parser.parseFile(file);
                            playbooks.add(playbook);
                            log.info("Playbook 로드: {} ({})", playbook.getName(), file.getFileName());
                        } catch (IOException e) {
                            log.error("Playbook 로드 실패: {}", file, e);
                        }
                    });
        }

        return playbooks;
    }

    /**
     * 디렉토리에서 Playbook을 로드하여 Registry에 등록
     */
    public void loadAndRegister(Path directory, PlaybookRegistry registry) throws IOException {
        List<Playbook> playbooks = loadFromDirectory(directory);
        for (Playbook playbook : playbooks) {
            registry.register(playbook);
        }
        log.info("{}개의 Playbook 등록 완료", playbooks.size());
    }

    private boolean isYamlFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".yml") || fileName.endsWith(".yaml");
    }
}
