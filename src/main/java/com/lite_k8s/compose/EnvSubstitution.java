package com.lite_k8s.compose;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compose YAML 의 ${KEY}, ${KEY:-default}, ${KEY:?msg} 변수 치환.
 *
 * ServiceDeployer 와 DeclaredImageResolver 에서 공통으로 사용되도록 분리.
 */
public final class EnvSubstitution {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private EnvSubstitution() {}

    /** 단일 문자열에 ${KEY} 치환 적용. value 가 null 이거나 ${ 가 없으면 원본 반환. */
    public static String substituteVars(String value, Map<String, String> context) {
        if (value == null || !value.contains("${")) return value;
        Matcher m = VAR_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1);
            String varName;
            String defaultValue = null;
            boolean required = false;
            String requiredMsg = null;

            int reqIdx = expr.indexOf(":?");
            int defIdx = expr.indexOf(":-");

            if (reqIdx >= 0) {
                varName = expr.substring(0, reqIdx);
                requiredMsg = expr.substring(reqIdx + 2);
                required = true;
            } else if (defIdx >= 0) {
                varName = expr.substring(0, defIdx);
                defaultValue = expr.substring(defIdx + 2);
            } else {
                varName = expr;
            }

            String replacement = context.get(varName);
            if (replacement == null && required) {
                throw new IllegalStateException("필수 변수 미설정: " + varName + " (" + requiredMsg + ")");
            }
            if (replacement == null && defaultValue != null) {
                replacement = defaultValue;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement != null ? replacement : m.group(0)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** List<String> 의 각 요소에 substituteVars 적용. null 입력은 빈 리스트. */
    public static List<String> substituteList(List<String> list, Map<String, String> context) {
        if (list == null) return List.of();
        return list.stream().map(v -> substituteVars(v, context)).toList();
    }

    /** ParsedService 의 image / containerName / ports / volumes / extraHosts / memoryLimit / cpuLimit 치환. */
    public static ParsedService substituteFields(ParsedService svc, Map<String, String> context) {
        return ParsedService.builder()
                .serviceName(svc.getServiceName())
                .image(substituteVars(svc.getImage(), context))
                .containerName(substituteVars(svc.getContainerName(), context))
                .ports(substituteList(svc.getPorts(), context))
                .volumes(substituteList(svc.getVolumes(), context))
                .environment(svc.getEnvironment())
                .networks(svc.getNetworks())
                .restartPolicy(svc.getRestartPolicy())
                .labels(svc.getLabels())
                .extraHosts(substituteList(svc.getExtraHosts(), context))
                .dependsOn(svc.getDependsOn())
                .profiles(svc.getProfiles())
                .memoryLimit(substituteVars(svc.getMemoryLimit(), context))
                .cpuLimit(substituteVars(svc.getCpuLimit(), context))
                .build();
    }
}
