package com.lite_k8s.update;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 이미지 retention 정책. application.yml: kite.image-retention.keep-recent
 *
 * 같은 image repo 의 dangling 이미지를 정리할 때 최신 K개만 유지한다.
 * 너무 작으면 롤백 불가, 너무 크면 디스크 누적.
 */
@Component
@ConfigurationProperties(prefix = "kite.image-retention")
@Data
public class ImageRetentionProperties {

    /** repo 당 유지할 최신 이미지 개수 (사용중 컨테이너가 참조하는 것 + 추가로 K개). */
    private int keepRecent = 3;
}
