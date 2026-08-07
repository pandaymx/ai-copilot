package xyz.ppmblszdp.ai.dto;

/**
 * 多模态媒体传输对象（用于图片、文件等载荷）。
 *
 * @param mimeType 媒体 MIME 类型，例如 "image/png", "image/jpeg"
 * @param data     Base64 编码的字节数据或 Data URL（例如 "data:image/png;base64,..."）
 */
public record MediaDto(
		String mimeType,
		String data
) {
	public String mimeType() {
		return (mimeType != null && !mimeType.isBlank()) ? mimeType.trim() : "image/png";
	}

	public String data() {
		return data != null ? data.trim() : "";
	}
}
