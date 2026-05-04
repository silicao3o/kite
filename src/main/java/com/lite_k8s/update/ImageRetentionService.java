package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 같은 image repo 의 옛 dangling 이미지를 정리해 디스크 누적을 막는다.
 *
 * 이미지 watch 가 새 digest 로 컨테이너를 recreate 할 때마다 옛 digest 가 untag 되지만
 * 디스크엔 남는다. 누적되면 daemon 의 `no space left on device` 로 다음 pull 이 실패.
 * 이 서비스가 recreate 직후 옛 것을 정리한다.
 *
 * 정책:
 *  - currently-used (running + stopped 컨테이너가 image-id 로 참조) 는 절대 안 지움
 *  - 나머지는 created 기준 최신 K개 (ImageRetentionProperties.keepRecent) 유지
 *  - K 이외의 옛 것 prune. NotFound 는 무시 (다른 prune 이 이미 지운 케이스)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageRetentionService {

    private final ImageRetentionProperties properties;

    /**
     * 주어진 image repo (예: "ghcr.io/daquv-core/quvi") 에 속한 이미지들을 정리.
     * @return 실제 prune 한 이미지 개수
     */
    public int pruneOldImages(DockerClient client, String imageRepo) {
        if (imageRepo == null || imageRepo.isBlank()) return 0;
        int keepRecent = Math.max(1, properties.getKeepRecent());

        // 1. 사용중 image-id 수집 (running + stopped)
        Set<String> usedImageIds = new HashSet<>();
        try {
            List<Container> containers = client.listContainersCmd().withShowAll(true).exec();
            for (Container c : containers) {
                if (c.getImageId() != null) usedImageIds.add(c.getImageId());
            }
        } catch (Exception e) {
            log.warn("retention: 컨테이너 리스트 조회 실패 — 정리 스킵: {}", imageRepo, e);
            return 0;
        }

        // 2. 같은 repo 의 이미지만 수집, created desc 정렬
        List<Image> matched;
        try {
            matched = client.listImagesCmd().withShowAll(true).exec().stream()
                    .filter(img -> matchesRepo(img, imageRepo))
                    .sorted(Comparator.comparingLong((Image i) -> i.getCreated() != null ? i.getCreated() : 0L).reversed())
                    .toList();
        } catch (Exception e) {
            log.warn("retention: 이미지 리스트 조회 실패 — 정리 스킵: {}", imageRepo, e);
            return 0;
        }

        // 3. 사용중은 keep, 사용중이 아닌 것 중 최신 K개도 keep, 나머지 prune
        int unusedKept = 0;
        int removed = 0;
        for (Image img : matched) {
            if (usedImageIds.contains(img.getId())) continue;  // 사용중 — keep, 카운트 X
            if (unusedKept < keepRecent) {
                unusedKept++;
                continue;
            }
            try {
                client.removeImageCmd(img.getId()).withForce(true).exec();
                removed++;
                log.info("retention: 옛 이미지 prune {} ({})", img.getId(), imageRepo);
            } catch (NotFoundException ignored) {
                // 다른 prune 이 이미 지움
            } catch (Exception e) {
                log.warn("retention: 이미지 prune 실패 (계속 진행): {} ({})", img.getId(), imageRepo, e);
            }
        }
        return removed;
    }

    private boolean matchesRepo(Image img, String repo) {
        String prefix = repo + ":";
        String digestPrefix = repo + "@";
        if (img.getRepoTags() != null) {
            for (String t : img.getRepoTags()) {
                if (t != null && t.startsWith(prefix)) return true;
            }
        }
        if (img.getRepoDigests() != null) {
            for (String d : img.getRepoDigests()) {
                if (d != null && d.startsWith(digestPrefix)) return true;
            }
        }
        return false;
    }
}
