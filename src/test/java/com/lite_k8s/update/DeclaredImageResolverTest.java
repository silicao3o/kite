package com.lite_k8s.update;

import com.github.dockerjava.api.model.Container;
import com.lite_k8s.compose.ServiceDefinition;
import com.lite_k8s.compose.ServiceDefinitionRepository;
import com.lite_k8s.envprofile.EnvProfileResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 컨테이너의 kite.service-definition-id 라벨을 신뢰해 compose YAML 의 declared image
 * 를 찾는 resolver. Poller / RollingUpdateService 의 가드가 이걸 사용해 nginx 사이드카에
 * 잘못된 GHCR 이미지가 박히는 것을 막는다.
 */
@ExtendWith(MockitoExtension.class)
class DeclaredImageResolverTest {

    @Mock private ServiceDefinitionRepository repo;
    @Mock private EnvProfileResolver envProfileResolver;

    @Test
    @DisplayName("kite.service-definition-id 라벨로 SD 를 찾고, container_name 이 일치하는 service 의 image 를 반환")
    void declaredImage_returnsImageWhenLabelAndYamlMatch() {
        DeclaredImageResolver resolver = new DeclaredImageResolver(repo, envProfileResolver);

        String yaml = """
                services:
                  quvi:
                    image: ghcr.io/daquv-core/quvi:latest
                    container_name: quvi
                  nginx:
                    image: nginx:alpine
                    container_name: quvi-nginx
                """;
        ServiceDefinition sd = ServiceDefinition.builder()
                .id("sd-1").name("q-play").composeYaml(yaml).build();
        when(repo.findById("sd-1")).thenReturn(Optional.of(sd));
        lenient().when(envProfileResolver.resolve(any())).thenReturn(Map.of());

        Container c = mockContainer("quvi-nginx", Map.of("kite.service-definition-id", "sd-1"));

        Optional<String> image = resolver.declaredImage(c);

        assertThat(image).contains("nginx:alpine");
    }

    @Test
    @DisplayName("kite.service-definition-id 라벨이 없으면 empty")
    void declaredImage_emptyWhenNoLabel() {
        DeclaredImageResolver resolver = new DeclaredImageResolver(repo, envProfileResolver);
        Container c = mockContainer("quvi-nginx", Map.of());

        assertThat(resolver.declaredImage(c)).isEmpty();
    }

    @Test
    @DisplayName("라벨은 있지만 SD 가 DB 에 없으면 empty")
    void declaredImage_emptyWhenSdMissing() {
        DeclaredImageResolver resolver = new DeclaredImageResolver(repo, envProfileResolver);
        when(repo.findById("missing")).thenReturn(Optional.empty());

        Container c = mockContainer("quvi-nginx",
                Map.of("kite.service-definition-id", "missing"));

        assertThat(resolver.declaredImage(c)).isEmpty();
    }

    @Test
    @DisplayName("container_name 에 ${VAR} 가 포함되면 EnvProfile 값으로 치환 후 매칭")
    void declaredImage_substitutesContainerName() {
        DeclaredImageResolver resolver = new DeclaredImageResolver(repo, envProfileResolver);

        String yaml = """
                services:
                  chat-quvi:
                    image: ghcr.io/daquv-core/chat-quvi:latest
                    container_name: ${CONTAINER_NAME:-chat-quvi}
                  nginx:
                    image: nginx:alpine
                    container_name: ${CONTAINER_NAME:-chat-quvi}-nginx
                """;
        ServiceDefinition sd = ServiceDefinition.builder()
                .id("sd-2").name("qvc-chat").composeYaml(yaml).build();
        when(repo.findById("sd-2")).thenReturn(Optional.of(sd));
        when(envProfileResolver.resolve(List.of("p-1")))
                .thenReturn(Map.of("CONTAINER_NAME", "qvc-chat"));

        Container c = mockContainer("qvc-chat-nginx", Map.of(
                "kite.service-definition-id", "sd-2",
                EnvProfileResolver.LABEL_KEY, "p-1"));

        assertThat(resolver.declaredImage(c)).contains("nginx:alpine");
    }

    @Test
    @DisplayName("YAML 의 어느 service 도 매칭되지 않으면 empty")
    void declaredImage_emptyWhenNoServiceMatchesContainerName() {
        DeclaredImageResolver resolver = new DeclaredImageResolver(repo, envProfileResolver);

        String yaml = """
                services:
                  quvi:
                    image: ghcr.io/daquv-core/quvi:latest
                    container_name: quvi
                """;
        ServiceDefinition sd = ServiceDefinition.builder()
                .id("sd-3").name("q-play").composeYaml(yaml).build();
        when(repo.findById("sd-3")).thenReturn(Optional.of(sd));
        lenient().when(envProfileResolver.resolve(any())).thenReturn(Map.of());

        Container c = mockContainer("unknown-container",
                Map.of("kite.service-definition-id", "sd-3"));

        assertThat(resolver.declaredImage(c)).isEmpty();
    }

    private Container mockContainer(String name, Map<String, String> labels) {
        Container c = mock(Container.class);
        lenient().when(c.getNames()).thenReturn(new String[]{"/" + name});
        when(c.getLabels()).thenReturn(labels);
        return c;
    }
}
