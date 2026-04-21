package com.lite_k8s.envprofile;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EnvProfileResolver {

    private final EnvProfileService service;

    /** profileIds 순서대로 엔트리를 수집, 뒤쪽이 앞쪽을 오버라이드 */
    public Map<String, String> resolve(List<String> profileIds) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String profileId : profileIds) {
            for (EnvProfileEntry entry : service.getDecryptedEntries(profileId)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** Docker create API의 Env 필드에 전달할 KEY=VALUE 리스트 */
    public List<String> resolveAsEnvList(List<String> profileIds) {
        return resolve(profileIds).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toList();
    }

    /**
     * profile env와 기존 컨테이너 env를 merge.
     * 프로파일에 있는 키 → 프로파일 값 사용 (DB 정보 갱신)
     * 프로파일에 없는 키 → 기존 값 유지 (TZ 등)
     */
    public String[] mergeWithExistingEnv(List<String> profileIds, String[] existingEnv) {
        // 기존 env를 먼저 깔고
        Map<String, String> merged = new LinkedHashMap<>();
        if (existingEnv != null) {
            for (String e : existingEnv) {
                int idx = e.indexOf('=');
                if (idx > 0) {
                    merged.put(e.substring(0, idx), e.substring(idx + 1));
                }
            }
        }

        // 프로파일 env가 오버라이드 (프로파일이 우선)
        merged.putAll(resolve(profileIds));

        return merged.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
    }

    /** 라벨 값("p1,p2")을 profileId 리스트로 파싱 */
    public static List<String> parseProfileIdsFromLabel(String label) {
        if (label == null || label.isBlank()) return List.of();
        return Arrays.stream(label.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** profileId 리스트를 라벨 값("p1,p2")으로 직렬화 */
    public static String buildProfileLabel(List<String> profileIds) {
        return String.join(",", profileIds);
    }

    public static final String LABEL_KEY = "kite.env-profile-ids";
}
