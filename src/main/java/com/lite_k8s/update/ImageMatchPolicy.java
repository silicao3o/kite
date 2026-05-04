package com.lite_k8s.update;

import com.github.dockerjava.api.model.Container;
import com.lite_k8s.util.ImageReferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Watch 가 컨테이너를 업데이트해도 되는지 결정하는 가드.
 *
 * 이름 패턴은 워낙 광범위(`quvi*`, `*quvi*`)할 수 있어 nginx 사이드카 같은 false positive
 * 가 잡힌다. 이 가드가 1) compose YAML 에 선언된 image 또는 2) 컨테이너의 runtime image
 * short-name 으로 한 번 더 거르면, 잘못된 이미지가 박히는 것을 막을 수 있다.
 */
@Component
@RequiredArgsConstructor
public class ImageMatchPolicy {

    private final DeclaredImageResolver resolver;

    public boolean allowsUpdate(Container container, String watchImage) {
        Optional<String> declared = resolver.declaredImage(container);
        if (declared.isPresent()) {
            return ImageReferences.sameShortName(declared.get(), watchImage);
        }
        // fallback: declared 를 모르면 컨테이너의 runtime image 와 비교.
        // image-id (sha256/hex) 처럼 비교 불가능한 형태면 가드를 우회 — 이름 패턴만 신뢰.
        String runtime = container.getImage();
        if (!ImageReferences.isImageReference(runtime)) {
            return true;
        }
        return ImageReferences.sameShortName(runtime, watchImage);
    }
}
