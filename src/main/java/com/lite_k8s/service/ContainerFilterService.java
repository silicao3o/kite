package com.lite_k8s.service;

import com.lite_k8s.config.MonitorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerFilterService {

    private final MonitorProperties monitorProperties;

    /**
     * 컨테이너가 모니터링 대상인지 확인
     *
     * @param containerName 컨테이너 이름
     * @param imageName     이미지 이름
     * @return true면 모니터링 대상, false면 제외
     */
    public boolean shouldMonitor(String containerName, String imageName) {
        MonitorProperties.Filter filter = monitorProperties.getFilter();

        // 제외 목록 체크 (하나라도 매칭되면 제외)
        if (matchesAnyPattern(containerName, filter.getExcludeNames())) {
            log.debug("컨테이너 제외 (이름 매칭): {}", containerName);
            return false;
        }

        if (matchesAnyPattern(imageName, filter.getExcludeImages())) {
            log.debug("컨테이너 제외 (이미지 매칭): {} (image: {})", containerName, imageName);
            return false;
        }

        // 포함 목록이 있으면 체크 (하나라도 매칭되어야 포함)
        if (!filter.getIncludeNames().isEmpty()) {
            if (!matchesAnyPattern(containerName, filter.getIncludeNames())) {
                log.debug("컨테이너 제외 (포함 목록에 없음): {}", containerName);
                return false;
            }
        }

        if (!filter.getIncludeImages().isEmpty()) {
            if (!matchesAnyPattern(imageName, filter.getIncludeImages())) {
                log.debug("컨테이너 제외 (포함 이미지 목록에 없음): {} (image: {})", containerName, imageName);
                return false;
            }
        }

        return true;
    }

    /**
     * 값이 패턴 목록 중 하나라도 매칭되는지 확인
     */
    private boolean matchesAnyPattern(String value, List<String> patterns) {
        if (value == null || patterns == null || patterns.isEmpty()) {
            return false;
        }

        for (String pattern : patterns) {
            try {
                if (Pattern.matches(pattern, value)) {
                    return true;
                }
            } catch (PatternSyntaxException e) {
                log.warn("잘못된 정규식 패턴: {}", pattern, e);
            }
        }
        return false;
    }
}
