package xyz.ppmblszdp.ai.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.factory.ObjectProvider;
import xyz.ppmblszdp.ai.config.AiProviderProperties;

class ImageModelRegistryTest {

    private ImageModel mockDefaultImageModel;
    private AiProviderProperties properties;
    private ImageModelRegistry registry;

    @BeforeEach
    void setUp() {
        mockDefaultImageModel = mock(ImageModel.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<ImageModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mockDefaultImageModel);

        properties = new AiProviderProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new AiProviderProperties.ImageConfig("openai", "dall-e-3", 1024, 1024, "standard", "vivid", "b64_json"),
                null,
                null);

        registry = new ImageModelRegistry(provider, properties);
    }

    @Test
    void testResolveDefaultModel() {
        ImageModel resolved = registry.resolve(null);
        assertEquals(mockDefaultImageModel, resolved);
    }

    @Test
    void testBuildOptionsForOpenAi() {
        ImageOptions options = registry.buildOptions("openai", "dall-e-3", 1024, 1024, "hd", "vivid");
        assertNotNull(options);
        assertTrue(options instanceof OpenAiImageOptions);
        OpenAiImageOptions openAiOptions = (OpenAiImageOptions) options;
        assertEquals("dall-e-3", openAiOptions.getModel());
        assertEquals(1024, openAiOptions.getWidth());
        assertEquals(1024, openAiOptions.getHeight());
        assertEquals("hd", openAiOptions.getQuality());
        assertEquals("vivid", openAiOptions.getStyle());
        assertEquals("b64_json", openAiOptions.getResponseFormat());
    }
}
