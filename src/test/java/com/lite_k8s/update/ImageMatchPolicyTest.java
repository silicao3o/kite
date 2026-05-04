package com.lite_k8s.update;

import com.github.dockerjava.api.model.Container;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Watch 가 컨테이너를 업데이트해도 되는지 판단하는 가드.
 *
 * - declared image (compose YAML) 가 있으면 그걸 기준으로 short-name 비교
 * - 없으면 컨테이너의 runtime image 와 watch image short-name 비교 (fallback)
 */
@ExtendWith(MockitoExtension.class)
class ImageMatchPolicyTest {

    @Mock private DeclaredImageResolver resolver;

    @Test
    @DisplayName("declared image 가 watch 와 short-name 일치하면 허용")
    void allowsUpdate_declaredMatches() {
        ImageMatchPolicy policy = new ImageMatchPolicy(resolver);
        Container c = mockContainer("/quvi", "ghcr.io/daquv-core/quvi:latest");
        when(resolver.declaredImage(c)).thenReturn(Optional.of("ghcr.io/daquv-core/quvi:latest"));

        assertThat(policy.allowsUpdate(c, "ghcr.io/daquv-core/quvi")).isTrue();
    }

    @Test
    @DisplayName("declared image 가 watch 와 short-name 불일치면 차단 — runtime image 가 watch 와 같아도")
    void allowsUpdate_declaredMismatch_blocksEvenIfRuntimeMatches() {
        ImageMatchPolicy policy = new ImageMatchPolicy(resolver);
        // 사이드카가 이미 잘못 quvi 이미지로 덮였더라도 declared 가 nginx:alpine 이면 차단해야
        // 추가 오염을 막을 수 있다.
        Container c = mockContainer("/quvi-nginx", "ghcr.io/daquv-core/quvi:latest");
        when(resolver.declaredImage(c)).thenReturn(Optional.of("nginx:alpine"));

        assertThat(policy.allowsUpdate(c, "ghcr.io/daquv-core/quvi")).isFalse();
    }

    @Test
    @DisplayName("declared 가 없으면 runtime image 의 short-name 과 watch 비교 (fallback)")
    void allowsUpdate_fallbackToRuntimeShortName_whenAllowed() {
        ImageMatchPolicy policy = new ImageMatchPolicy(resolver);
        Container c = mockContainer("/quvi", "ghcr.io/daquv-core/quvi:latest");
        when(resolver.declaredImage(c)).thenReturn(Optional.empty());

        assertThat(policy.allowsUpdate(c, "ghcr.io/daquv-core/quvi")).isTrue();
    }

    @Test
    @DisplayName("declared 없고 runtime short-name 도 다르면 차단")
    void allowsUpdate_fallbackBlocksOnDifferentRuntimeShortName() {
        ImageMatchPolicy policy = new ImageMatchPolicy(resolver);
        Container c = mockContainer("/quvi-nginx", "nginx:alpine");
        when(resolver.declaredImage(c)).thenReturn(Optional.empty());

        assertThat(policy.allowsUpdate(c, "ghcr.io/daquv-core/quvi")).isFalse();
    }

    @Test
    @DisplayName("runtime image 가 image-id (sha256/hex) 형태고 declared 도 없으면 허용 (이름 패턴만 신뢰)")
    void allowsUpdate_fallbackBypassedWhenContainerImageIsId() {
        ImageMatchPolicy policy = new ImageMatchPolicy(resolver);
        Container c = mockContainer("/quvi", "sha256:abcdef0123456789");
        when(resolver.declaredImage(c)).thenReturn(Optional.empty());

        assertThat(policy.allowsUpdate(c, "ghcr.io/daquv-core/quvi")).isTrue();
    }

    private Container mockContainer(String name, String image) {
        Container c = mock(Container.class);
        lenient().when(c.getNames()).thenReturn(new String[]{name});
        lenient().when(c.getImage()).thenReturn(image);
        return c;
    }
}
