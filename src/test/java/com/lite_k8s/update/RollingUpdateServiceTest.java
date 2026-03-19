package com.lite_k8s.update;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.exception.DockerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RollingUpdateServiceTest {

    @Mock private DockerClient dockerClient;
    @Mock private ContainerRecreator recreator;

    private RollingUpdateService service;

    @BeforeEach
    void setUp() {
        service = new RollingUpdateService(dockerClient, recreator);
    }

    @Test
    @DisplayName("maxUnavailable=1이면 컨테이너 하나씩 순차 업데이트")
    void executeRollingUpdate_WithMaxUnavailable1_UpdatesSequentially() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setContainerPattern("myapp-.*");
        watch.setMaxUnavailable(1);

        Container c1 = mockContainer("c1", "/myapp-1");
        Container c2 = mockContainer("c2", "/myapp-2");

        when(recreator.recreate(any(), any(), any())).thenReturn(true);

        // when
        List<UpdateResult> results = service.executeRollingUpdate(List.of(c1, c2), watch, "sha256:new");

        // then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(UpdateResult::isSuccess);

        // recreate 순차 호출 확인
        verify(recreator, times(2)).recreate(any(), any(), any());
    }

    @Test
    @DisplayName("maxUnavailable=2이면 2개 동시 업데이트")
    void executeRollingUpdate_WithMaxUnavailable2_UpdatesTwoAtATime() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setMaxUnavailable(2);

        Container c1 = mockContainer("c1", "/myapp-1");
        Container c2 = mockContainer("c2", "/myapp-2");
        Container c3 = mockContainer("c3", "/myapp-3");

        when(recreator.recreate(any(), any(), any())).thenReturn(true);

        // when
        List<UpdateResult> results = service.executeRollingUpdate(List.of(c1, c2, c3), watch, "sha256:new");

        // then
        assertThat(results).hasSize(3);
        verify(recreator, times(3)).recreate(any(), any(), any());
    }

    @Test
    @DisplayName("recreate 실패 시 롤백 수행 후 결과에 실패 표시")
    void executeRollingUpdate_WhenRecreateFails_RecordsFailure() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setImage("ghcr.io/myorg/myapp");
        watch.setTag("latest");
        watch.setMaxUnavailable(1);

        Container c1 = mockContainer("c1", "/myapp-1");

        when(recreator.recreate(any(), any(), any())).thenReturn(false);

        // when
        List<UpdateResult> results = service.executeRollingUpdate(List.of(c1), watch, "sha256:new");

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).isSuccess()).isFalse();
        assertThat(results.get(0).getContainerName()).isEqualTo("myapp-1");
    }

    @Test
    @DisplayName("빈 컨테이너 목록이면 아무것도 하지 않음")
    void executeRollingUpdate_WithEmptyList_DoesNothing() {
        // given
        ImageWatchProperties.ImageWatch watch = new ImageWatchProperties.ImageWatch();
        watch.setMaxUnavailable(1);

        // when
        List<UpdateResult> results = service.executeRollingUpdate(List.of(), watch, "sha256:new");

        // then
        assertThat(results).isEmpty();
        verifyNoInteractions(recreator);
    }

    private Container mockContainer(String id, String name) {
        Container c = mock(Container.class);
        when(c.getId()).thenReturn(id);
        when(c.getNames()).thenReturn(new String[]{name});
        when(c.getImageId()).thenReturn("sha256:old");
        return c;
    }
}
