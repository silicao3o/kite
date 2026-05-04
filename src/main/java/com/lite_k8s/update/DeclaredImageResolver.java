package com.lite_k8s.update;

import com.github.dockerjava.api.model.Container;
import com.lite_k8s.compose.ComposeParser;
import com.lite_k8s.compose.EnvSubstitution;
import com.lite_k8s.compose.ParsedService;
import com.lite_k8s.compose.ServiceDefinition;
import com.lite_k8s.compose.ServiceDefinitionRepository;
import com.lite_k8s.envprofile.EnvProfileResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 컨테이너의 kite.service-definition-id 라벨을 따라 ServiceDefinition 의 compose YAML
 * 에 선언된 image 를 찾아준다.
 *
 * Poller / RollingUpdateService 가드의 1순위 진실의 소스. 라벨이 없거나 매칭 실패시
 * empty 를 반환해 호출부의 fallback (short-name 가드) 으로 떨어진다.
 */
@Component
@RequiredArgsConstructor
public class DeclaredImageResolver {

    public static final String SERVICE_DEFINITION_LABEL = "kite.service-definition-id";

    private final ServiceDefinitionRepository repository;
    private final EnvProfileResolver envProfileResolver;

    public Optional<String> declaredImage(Container container) {
        Map<String, String> labels = container.getLabels();
        if (labels == null || labels.isEmpty()) return Optional.empty();

        String sdId = labels.get(SERVICE_DEFINITION_LABEL);
        if (sdId == null || sdId.isBlank()) return Optional.empty();

        Optional<ServiceDefinition> sdOpt = repository.findById(sdId);
        if (sdOpt.isEmpty()) return Optional.empty();
        ServiceDefinition sd = sdOpt.get();

        List<String> profileIds = EnvProfileResolver.parseProfileIdsFromLabel(
                labels.get(EnvProfileResolver.LABEL_KEY));
        Map<String, String> envContext = profileIds.isEmpty()
                ? Map.of()
                : envProfileResolver.resolve(profileIds);

        String containerName = extractName(container);
        if (containerName == null) return Optional.empty();

        for (ParsedService svc : ComposeParser.parse(sd.getComposeYaml())) {
            ParsedService resolved = EnvSubstitution.substituteFields(svc, envContext);
            if (containerName.equals(resolved.getContainerName())) {
                return Optional.ofNullable(resolved.getImage());
            }
        }
        return Optional.empty();
    }

    private String extractName(Container container) {
        if (container.getNames() == null || container.getNames().length == 0) return null;
        String name = container.getNames()[0];
        return name.startsWith("/") ? name.substring(1) : name;
    }
}
