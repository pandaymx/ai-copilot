package xyz.ppmblszdp.ai.dto;

public record ImageGenerationRequestDto(
		String prompt,
		String provider,
		String model,
		Integer width,
		Integer height,
		String quality,
		String style
) {
	public ImageGenerationRequestDto(String prompt) {
		this(prompt, null, null, null, null, null, null);
	}
}
