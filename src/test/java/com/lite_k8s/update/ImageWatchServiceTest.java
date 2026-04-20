package com.lite_k8s.update;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageWatchServiceTest {

    @Mock
    ImageWatchRepository repository;

    @InjectMocks
    ImageWatchService service;

    @Test
    @DisplayName("6. findEnabled()로 활성 와치만 조회할 수 있다")
    void findEnabledReturnsOnlyEnabled() {
        ImageWatchEntity enabled = ImageWatchEntity.builder()
                .image("ghcr.io/org/app").enabled(true).build();
        when(repository.findByEnabled(true)).thenReturn(List.of(enabled));

        List<ImageWatchEntity> result = service.findEnabled();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getImage()).isEqualTo("ghcr.io/org/app");
    }

    @Test
    @DisplayName("7. save()로 와치를 저장할 수 있다")
    void saveWatch() {
        ImageWatchEntity entity = ImageWatchEntity.builder()
                .image("ghcr.io/org/app").build();
        when(repository.save(entity)).thenReturn(entity);

        ImageWatchEntity saved = service.save(entity);

        assertThat(saved.getImage()).isEqualTo("ghcr.io/org/app");
        verify(repository).save(entity);
    }

    @Test
    @DisplayName("8. findAll()로 전체 와치를 조회할 수 있다")
    void findAll() {
        when(repository.findAll()).thenReturn(List.of(
                ImageWatchEntity.builder().image("a").build(),
                ImageWatchEntity.builder().image("b").build()
        ));

        assertThat(service.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("9. delete(id)로 와치를 삭제할 수 있다")
    void deleteWatch() {
        service.delete("some-id");

        verify(repository).deleteById("some-id");
    }
}
