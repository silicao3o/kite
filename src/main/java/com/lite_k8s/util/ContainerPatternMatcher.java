package com.lite_k8s.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.PatternSyntaxException;

/**
 * 컨테이너 이름 패턴 매칭 — glob(*, ?) / regex 호환.
 *
 * Poller(감지)와 RollingUpdateService(실행)가 동일한 매칭 규칙을 쓰도록
 * 한 곳에 모은다. 한쪽만 glob 변환하면 "감지는 됐는데 업데이트는 0건" 이
 * 되는 불일치가 생긴다.
 */
@Slf4j
public final class ContainerPatternMatcher {

    private ContainerPatternMatcher() {}

    public static boolean matches(String name, String pattern) {
        if (pattern == null || pattern.isEmpty()) return true;
        String regex = isGlobPattern(pattern) ? globToRegex(pattern) : pattern;
        try {
            return name.matches(regex);
        } catch (PatternSyntaxException e) {
            log.warn("컨테이너 패턴 매칭 실패 — substring 폴백: pattern={} reason={}",
                    pattern, e.getDescription());
            String core = pattern.replace("*", "").replace("?", "");
            return !core.isEmpty() && name.contains(core);
        }
    }

    private static boolean isGlobPattern(String pattern) {
        // glob 문자(*, ?) 가 있고 정규식 전용 문자(.*, .+, \d 등) 가 없으면 glob
        if (!pattern.contains("*") && !pattern.contains("?")) return false;
        return !pattern.contains(".*") && !pattern.contains(".+") && !pattern.contains("\\");
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.' -> sb.append("\\.");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
