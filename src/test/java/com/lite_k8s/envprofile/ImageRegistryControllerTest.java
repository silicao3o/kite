package com.lite_k8s.envprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ImageRegistryControllerTest {

    private ImageRegistryRepository repository;
    private ImageRegistryController controller;

    @BeforeEach
    void setUp() {
        repository = mock(ImageRegistryRepository.class);
        controller = new ImageRegistryController(repository);
    }

    @Test
    void update_shouldModifyAllFields() {
        ImageRegistry existing = ImageRegistry.builder()
                .id("reg-1")
                .image("ghcr.io/old-org/app")
                .alias("old-alias")
                .description("old desc")
                .ghcrToken("old-token")
                .build();
        when(repository.findById("reg-1")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.update("reg-1", Map.of(
                "image", "ghcr.io/new-org/app",
                "alias", "new-alias",
                "description", "new desc",
                "ghcrToken", "new-token"
        ));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(existing.getImage()).isEqualTo("ghcr.io/new-org/app");
        assertThat(existing.getAlias()).isEqualTo("new-alias");
        assertThat(existing.getDescription()).isEqualTo("new desc");
        assertThat(existing.getGhcrToken()).isEqualTo("new-token");
        verify(repository).save(existing);
    }

    @Test
    void update_shouldReturn404WhenNotFound() {
        when(repository.findById("not-exist")).thenReturn(Optional.empty());

        var response = controller.update("not-exist", Map.of("image", "ghcr.io/org/app"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void update_partialFields_shouldOnlyUpdateProvided() {
        ImageRegistry existing = ImageRegistry.builder()
                .id("reg-2")
                .image("ghcr.io/org/app")
                .alias("keep-this")
                .description("keep-desc")
                .ghcrToken("keep-token")
                .build();
        when(repository.findById("reg-2")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.update("reg-2", Map.of("alias", "updated-alias"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(existing.getImage()).isEqualTo("ghcr.io/org/app");
        assertThat(existing.getAlias()).isEqualTo("updated-alias");
        assertThat(existing.getDescription()).isEqualTo("keep-desc");
        assertThat(existing.getGhcrToken()).isEqualTo("keep-token");
    }
}
