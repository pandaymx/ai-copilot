package xyz.ppmblszdp.ai.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import xyz.ppmblszdp.ai.dto.ImageGenerationRequestDto;
import xyz.ppmblszdp.ai.registry.ImageModelRegistry;

class ImageGenerationServiceTest {

    private ImageModelRegistry mockRegistry;
    private ImageModel mockImageModel;
    private ImageGenerationService service;

    @BeforeEach
    void setUp() {
        mockRegistry = mock(ImageModelRegistry.class);
        mockImageModel = mock(ImageModel.class);

        when(mockRegistry.resolve(any())).thenReturn(mockImageModel);
        when(mockRegistry.buildOptions(any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(ImageOptions.class));

        WebClient.Builder builder = WebClient.builder();
        service = new ImageGenerationService(mockRegistry, builder);
    }

    @Test
    void testGenerateImageReturnsBase64Payload() {
        Image mockOutput = new Image(
                null,
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        ImageGeneration generation = new ImageGeneration(mockOutput);
        ImageResponse response = new ImageResponse(List.of(generation));

        when(mockImageModel.call(any(ImagePrompt.class))).thenReturn(response);

        ImageGenerationRequestDto request = new ImageGenerationRequestDto("画一只猫");

        StepVerifier.create(service.generateImage(request))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals("画一只猫", result.prompt());
                    assertTrue(result.payload().startsWith("data:image/png;base64,"));
                })
                .verifyComplete();
    }

    private void assertEquals(String expected, String actual) {
        Assertions.assertEquals(expected, actual);
    }
}
