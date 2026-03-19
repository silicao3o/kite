package com.lite_k8s.playbook;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * YAML 문자열을 Playbook 객체로 파싱하는 파서
 */
@Component
public class PlaybookParser {

    private final Yaml yaml;

    public PlaybookParser() {
        LoaderOptions loaderOptions = new LoaderOptions();
        this.yaml = new Yaml(new Constructor(PlaybookDto.class, loaderOptions));
    }

    /**
     * YAML 문자열을 Playbook으로 파싱
     */
    public Playbook parse(String yamlString) {
        PlaybookDto dto = yaml.load(yamlString);
        return dto.toPlaybook();
    }

    /**
     * 파일에서 Playbook 로드
     */
    public Playbook parseFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        return parse(content);
    }
}
