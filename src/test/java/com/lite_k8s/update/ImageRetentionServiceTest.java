package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 같은 image repo 의 옛 dangling 이미지를 정리해 디스크 누적을 막는다.
 *
 * 정책:
 *  - currently-used (running + stopped 컨테이너가 참조) 이미지는 절대 안 지움
 *  - 나머지는 created 기준 최신 K개만 유지, 옛 것 prune
 *  - K = ImageRetentionProperties.keepRecent (default 3)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageRetentionServiceTest {

    @Mock private DockerClient client;
    @Mock private ListImagesCmd listImagesCmd;
    @Mock private ListContainersCmd listContainersCmd;

    private ImageRetentionService service;

    @BeforeEach
    void setUp() {
        ImageRetentionProperties props = new ImageRetentionProperties();
        props.setKeepRecent(3);
        service = new ImageRetentionService(props);

        when(client.listImagesCmd()).thenReturn(listImagesCmd);
        when(listImagesCmd.withShowAll(true)).thenReturn(listImagesCmd);
        when(client.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
    }

    @Test
    @DisplayName("같은 repo 의 5개 dangling 이미지 중 최신 3개 유지, 옛 2개 prune")
    void prune_KeepsRecentK_RemovesOlder() {
        Image img1 = mockImage("sha256:1", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d1"}, 1000L);
        Image img2 = mockImage("sha256:2", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d2"}, 2000L);
        Image img3 = mockImage("sha256:3", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d3"}, 3000L);
        Image img4 = mockImage("sha256:4", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d4"}, 4000L);
        Image img5 = mockImage("sha256:5", new String[]{"ghcr.io/org/quvi:latest"},
                new String[]{"ghcr.io/org/quvi@sha256:d5"}, 5000L);
        when(listImagesCmd.exec()).thenReturn(List.of(img1, img2, img3, img4, img5));
        when(listContainersCmd.exec()).thenReturn(List.of());  // 사용중 컨테이너 없음

        stubRemove("sha256:1");
        stubRemove("sha256:2");

        int removed = service.pruneOldImages(client, "ghcr.io/org/quvi");

        assertThat(removed).isEqualTo(2);
        verify(client).removeImageCmd("sha256:1");
        verify(client).removeImageCmd("sha256:2");
        verify(client, never()).removeImageCmd("sha256:3");
        verify(client, never()).removeImageCmd("sha256:4");
        verify(client, never()).removeImageCmd("sha256:5");
    }

    @Test
    @DisplayName("currently-used 이미지는 옛 것이라도 절대 prune 안 함")
    void prune_NeverRemovesUsedImage() {
        // 5개 이미지 중 가장 옛 것 (sha256:1) 이 stopped 컨테이너에 의해 사용중
        Image img1 = mockImage("sha256:1", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d1"}, 1000L);
        Image img2 = mockImage("sha256:2", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d2"}, 2000L);
        Image img3 = mockImage("sha256:3", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d3"}, 3000L);
        Image img4 = mockImage("sha256:4", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d4"}, 4000L);
        Image img5 = mockImage("sha256:5", new String[]{"ghcr.io/org/quvi:latest"},
                new String[]{"ghcr.io/org/quvi@sha256:d5"}, 5000L);
        when(listImagesCmd.exec()).thenReturn(List.of(img1, img2, img3, img4, img5));

        Container stopped = mock(Container.class);
        when(stopped.getImageId()).thenReturn("sha256:1");
        when(listContainersCmd.exec()).thenReturn(List.of(stopped));

        // sha256:1 이 사용중이니 keepRecent=3 + 사용중 1 = 후보 1개 (sha256:2) 만 prune
        stubRemove("sha256:2");

        int removed = service.pruneOldImages(client, "ghcr.io/org/quvi");

        assertThat(removed).isEqualTo(1);
        verify(client, never()).removeImageCmd("sha256:1");  // 사용중
        verify(client).removeImageCmd("sha256:2");
        verify(client, never()).removeImageCmd("sha256:3");
        verify(client, never()).removeImageCmd("sha256:4");
        verify(client, never()).removeImageCmd("sha256:5");
    }

    @Test
    @DisplayName("다른 repo 의 이미지는 건드리지 않는다")
    void prune_OnlyTouchesMatchingRepo() {
        Image quvi = mockImage("sha256:q", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:dq"}, 1000L);
        Image admin = mockImage("sha256:a", new String[]{}, new String[]{"ghcr.io/org/admin-quvi@sha256:da"}, 1000L);
        Image nginx = mockImage("sha256:n", new String[]{"nginx:alpine"}, new String[]{}, 1000L);
        when(listImagesCmd.exec()).thenReturn(List.of(quvi, admin, nginx));
        when(listContainersCmd.exec()).thenReturn(List.of());

        int removed = service.pruneOldImages(client, "ghcr.io/org/quvi");

        assertThat(removed).isEqualTo(0);  // quvi 1개 < keepRecent=3
        verify(client, never()).removeImageCmd(anyString());
    }

    @Test
    @DisplayName("repo 매칭은 RepoTags 와 RepoDigests 양쪽 다 본다")
    void prune_MatchesByEitherTagsOrDigests() {
        // repo 가 RepoTags 로만 매칭되는 케이스
        Image taggedOnly = mockImage("sha256:t", new String[]{"ghcr.io/org/quvi:v1"}, new String[]{}, 1000L);
        // repo 가 RepoDigests 로만 매칭되는 케이스
        Image digestOnly = mockImage("sha256:d", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:abc"}, 2000L);
        // current
        Image current = mockImage("sha256:c", new String[]{"ghcr.io/org/quvi:latest"}, new String[]{}, 3000L);
        when(listImagesCmd.exec()).thenReturn(List.of(taggedOnly, digestOnly, current));
        when(listContainersCmd.exec()).thenReturn(List.of());

        // 3개 < keepRecent=3 이라 0 remove
        int removed = service.pruneOldImages(client, "ghcr.io/org/quvi");
        assertThat(removed).isEqualTo(0);

        // 4번째 추가 → 1 remove
        Image extra = mockImage("sha256:e", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:e"}, 500L);
        when(listImagesCmd.exec()).thenReturn(List.of(taggedOnly, digestOnly, current, extra));
        stubRemove("sha256:e");
        int removed2 = service.pruneOldImages(client, "ghcr.io/org/quvi");
        assertThat(removed2).isEqualTo(1);
        verify(client).removeImageCmd("sha256:e");
    }

    @Test
    @DisplayName("remove 시 NotFoundException 은 무시 (이미 다른 prune 이 지움)")
    void prune_IgnoresNotFoundOnRemove() {
        Image img1 = mockImage("sha256:1", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d1"}, 1000L);
        Image img2 = mockImage("sha256:2", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d2"}, 2000L);
        Image img3 = mockImage("sha256:3", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d3"}, 3000L);
        Image img4 = mockImage("sha256:4", new String[]{}, new String[]{"ghcr.io/org/quvi@sha256:d4"}, 4000L);
        Image img5 = mockImage("sha256:5", new String[]{"ghcr.io/org/quvi:latest"},
                new String[]{"ghcr.io/org/quvi@sha256:d5"}, 5000L);
        when(listImagesCmd.exec()).thenReturn(List.of(img1, img2, img3, img4, img5));
        when(listContainersCmd.exec()).thenReturn(List.of());

        // sha256:1 은 NotFound (이미 지워짐), sha256:2 는 정상 remove
        RemoveImageCmd notFoundCmd = mock(RemoveImageCmd.class);
        when(client.removeImageCmd("sha256:1")).thenReturn(notFoundCmd);
        when(notFoundCmd.withForce(true)).thenReturn(notFoundCmd);
        when(notFoundCmd.exec()).thenThrow(new NotFoundException("Status 404"));
        stubRemove("sha256:2");

        int removed = service.pruneOldImages(client, "ghcr.io/org/quvi");

        assertThat(removed).isEqualTo(1);  // sha256:2 만 카운트, sha256:1 NotFound 는 무시
    }

    private Image mockImage(String id, String[] repoTags, String[] repoDigests, long created) {
        Image img = mock(Image.class);
        when(img.getId()).thenReturn(id);
        when(img.getRepoTags()).thenReturn(repoTags);
        when(img.getRepoDigests()).thenReturn(repoDigests);
        when(img.getCreated()).thenReturn(created);
        return img;
    }

    private void stubRemove(String id) {
        RemoveImageCmd cmd = mock(RemoveImageCmd.class);
        when(client.removeImageCmd(id)).thenReturn(cmd);
        when(cmd.withForce(true)).thenReturn(cmd);
    }
}
