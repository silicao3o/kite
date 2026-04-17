package com.lite_k8s.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageUpdateHistoryServiceTest {

    @Mock
    ImageUpdateHistoryRepository repository;

    @InjectMocks
    ImageUpdateHistoryService service;

    @Test
    @DisplayName("13. findByWatchId()로 특정 와치의 이력을 조회할 수 있다")
    void findByWatchId() {
        ImageUpdateHistoryEntity h1 = ImageUpdateHistoryEntity.builder()
                .watchId("w1").image("app").status(ImageUpdateHistoryEntity.Status.SUCCESS).build();
        ImageUpdateHistoryEntity h2 = ImageUpdateHistoryEntity.builder()
                .watchId("w1").image("app").status(ImageUpdateHistoryEntity.Status.DETECTED).build();

        when(repository.findByWatchIdOrderByCreatedAtDesc("w1")).thenReturn(List.of(h1, h2));

        List<ImageUpdateHistoryEntity> result = service.findByWatchId("w1");

        assertThat(result).hasSize(2);
        verify(repository).findByWatchIdOrderByCreatedAtDesc("w1");
    }

    @Test
    @DisplayName("14. record()로 이력을 저장할 수 있다")
    void record() {
        ImageUpdateHistoryEntity entity = ImageUpdateHistoryEntity.builder()
                .watchId("w1").image("app").status(ImageUpdateHistoryEntity.Status.DETECTED)
                .newDigest("sha256:abc").build();

        when(repository.save(entity)).thenReturn(entity);

        ImageUpdateHistoryEntity saved = service.record(entity);

        assertThat(saved.getStatus()).isEqualTo(ImageUpdateHistoryEntity.Status.DETECTED);
        verify(repository).save(entity);
    }
}
