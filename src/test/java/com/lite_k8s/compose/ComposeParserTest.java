package com.lite_k8s.compose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ComposeParserTest {

    @Test
    @DisplayName("기본 compose YAML을 파싱하면 ParsedService 목록이 반환된다")
    void parse_BasicYaml() {
        String yaml = """
                services:
                  quvi:
                    image: ghcr.io/daquv-qv/quvi:latest
                    container_name: quvi-operia
                    ports:
                      - "8080:8080"
                    volumes:
                      - /data/keys:/app/keys
                    environment:
                      TZ: Asia/Seoul
                    networks:
                      - app-net
                    restart: unless-stopped
                    labels:
                      team: daquv
                """;

        List<ParsedService> services = ComposeParser.parse(yaml);

        assertThat(services).hasSize(1);
        ParsedService svc = services.get(0);
        assertThat(svc.getServiceName()).isEqualTo("quvi");
        assertThat(svc.getImage()).isEqualTo("ghcr.io/daquv-qv/quvi:latest");
        assertThat(svc.getContainerName()).isEqualTo("quvi-operia");
        assertThat(svc.getPorts()).containsExactly("8080:8080");
        assertThat(svc.getVolumes()).containsExactly("/data/keys:/app/keys");
        assertThat(svc.getEnvironment()).containsEntry("TZ", "Asia/Seoul");
        assertThat(svc.getNetworks()).containsExactly("app-net");
        assertThat(svc.getRestartPolicy()).isEqualTo("unless-stopped");
        assertThat(svc.getLabels()).containsEntry("team", "daquv");
    }

    @Test
    @DisplayName("여러 서비스가 있는 compose를 파싱한다")
    void parse_MultipleServices() {
        String yaml = """
                services:
                  app:
                    image: myapp:latest
                  db:
                    image: postgres:15
                    ports:
                      - "5432:5432"
                """;

        List<ParsedService> services = ComposeParser.parse(yaml);
        assertThat(services).hasSize(2);
    }

    @Test
    @DisplayName("environment가 리스트 형태('KEY=VALUE')여도 파싱된다")
    void parse_EnvironmentAsList() {
        String yaml = """
                services:
                  app:
                    image: myapp:latest
                    environment:
                      - DB_HOST=localhost
                      - DB_PORT=5432
                """;

        List<ParsedService> services = ComposeParser.parse(yaml);
        assertThat(services.get(0).getEnvironment())
                .containsEntry("DB_HOST", "localhost")
                .containsEntry("DB_PORT", "5432");
    }

    @Test
    @DisplayName("profiles가 있는 서비스는 profiles 리스트가 파싱된다")
    void parse_ProfilesField() {
        String yaml = """
                services:
                  app:
                    image: myapp:latest
                  nginx:
                    image: nginx:alpine
                    profiles:
                      - with-nginx
                      - production
                """;

        List<ParsedService> services = ComposeParser.parse(yaml);

        ParsedService app = services.stream().filter(s -> s.getServiceName().equals("app")).findFirst().get();
        ParsedService nginx = services.stream().filter(s -> s.getServiceName().equals("nginx")).findFirst().get();

        assertThat(app.getProfiles()).isEmpty();
        assertThat(nginx.getProfiles()).containsExactly("with-nginx", "production");
    }

    @Test
    @DisplayName("activeProfiles를 지정하면 해당 프로필에 속한 서비스만 반환된다")
    void parse_WithActiveProfiles_FiltersServices() {
        String yaml = """
                services:
                  app:
                    image: myapp:latest
                  nginx:
                    image: nginx:alpine
                    profiles:
                      - with-nginx
                  redis:
                    image: redis:7
                    profiles:
                      - cache
                """;

        List<ParsedService> result = ComposeParser.parse(yaml, List.of("with-nginx"));

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(ParsedService::getServiceName).toList())
                .containsExactlyInAnyOrder("app", "nginx");
    }

    @Test
    @DisplayName("activeProfiles가 빈 리스트이면 profiles가 없는 서비스만 반환된다")
    void parse_EmptyActiveProfiles_OnlyDefaultServices() {
        String yaml = """
                services:
                  app:
                    image: myapp:latest
                  nginx:
                    image: nginx:alpine
                    profiles:
                      - with-nginx
                """;

        List<ParsedService> result = ComposeParser.parse(yaml, List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getServiceName()).isEqualTo("app");
    }

    @Test
    @DisplayName("activeProfiles가 null이면 모든 서비스가 반환된다 (기존 동작 호환)")
    void parse_NullActiveProfiles_AllServices() {
        String yaml = """
                services:
                  app:
                    image: myapp:latest
                  nginx:
                    image: nginx:alpine
                    profiles:
                      - with-nginx
                """;

        List<ParsedService> result = ComposeParser.parse(yaml, null);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("container_name이 없으면 serviceName을 사용한다")
    void parse_NoContainerName_UsesServiceName() {
        String yaml = """
                services:
                  myapp:
                    image: myapp:latest
                """;

        List<ParsedService> services = ComposeParser.parse(yaml);
        assertThat(services.get(0).getContainerName()).isEqualTo("myapp");
    }
}
