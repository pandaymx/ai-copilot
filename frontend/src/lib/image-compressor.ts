/**
 * 客户端图片压缩与自适应优化工具。
 *
 * 规则与约束：
 * 1. 约束单张图片体积在 4MB 以内（Spring AI / 多模态 LLM 视觉识别最佳实践）；
 * 2. 避免透明 PNG / WebP 导出为 JPEG 导致透明通道丢失变黑底问题；
 * 3. 对 GIF 动图跳过 Canvas 重绘以保护多帧动画不被破坏；
 * 4. 自动按长边缩放到 maxDimension（默认 2560px），保障清晰度的同时大幅减少 Base64 传输体积。
 */

export interface CompressedImageResult {
  file: File;
  dataUrl: string;
  width: number;
  height: number;
  size: number;
  mimeType: string;
  name: string;
}

const DEFAULT_MAX_SIZE = 4 * 1024 * 1024; // 4MB
const DEFAULT_MAX_DIMENSION = 2560; // 2.5K 分辨率上限

export async function compressImage(
  file: File,
  maxSizeBytes: number = DEFAULT_MAX_SIZE,
  maxDimension: number = DEFAULT_MAX_DIMENSION,
): Promise<CompressedImageResult> {
  const mimeType = file.type || "image/png";

  // 1. GIF 动图：跳过 Canvas 重绘，直接转为 DataURL（防止帧丢失变为静态图）
  if (mimeType === "image/gif") {
    if (file.size > maxSizeBytes) {
      throw new Error(
        `GIF 动图大小 (${(file.size / 1024 / 1024).toFixed(1)}MB) 超过 4MB 限制，请裁剪或使用短动图。`,
      );
    }
    const dataUrl = await fileToDataUrl(file);
    return {
      file,
      dataUrl,
      width: 0,
      height: 0,
      size: file.size,
      mimeType: "image/gif",
      name: file.name,
    };
  }

  // 2. 将文件加载为 HTMLImageElement 获取原始宽高
  const rawDataUrl = await fileToDataUrl(file);
  const img = await loadImage(rawDataUrl);

  const origWidth = img.naturalWidth || img.width;
  const origHeight = img.naturalHeight || img.height;

  // 如果文件体积已经小于等于限制且尺寸未超标，直接返回
  if (
    file.size <= maxSizeBytes &&
    origWidth <= maxDimension &&
    origHeight <= maxDimension
  ) {
    return {
      file,
      dataUrl: rawDataUrl,
      width: origWidth,
      height: origHeight,
      size: file.size,
      mimeType,
      name: file.name,
    };
  }

  // 3. 计算等比例缩放尺寸
  let targetWidth = origWidth;
  let targetHeight = origHeight;

  if (targetWidth > maxDimension || targetHeight > maxDimension) {
    if (targetWidth >= targetHeight) {
      targetHeight = Math.round((targetHeight * maxDimension) / targetWidth);
      targetWidth = maxDimension;
    } else {
      targetWidth = Math.round((targetWidth * maxDimension) / targetHeight);
      targetHeight = maxDimension;
    }
  }

  // 4. 选用目标 MIME 格式：透明 PNG / WebP 优先导出为 image/webp 或 image/png
  const isPng = mimeType === "image/png";
  const isWebp = mimeType === "image/webp";
  const targetMime = isPng || isWebp ? "image/webp" : "image/jpeg";

  // 5. Canvas 渲染与步进质量压缩
  const canvas = document.createElement("canvas");
  canvas.width = targetWidth;
  canvas.height = targetHeight;
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    throw new Error("无法初始化 Canvas 上下文进行图片压缩");
  }

  ctx.imageSmoothingEnabled = true;
  ctx.imageSmoothingQuality = "high";
  ctx.drawImage(img, 0, 0, targetWidth, targetHeight);

  let quality = 0.9;
  let compressedDataUrl = canvas.toDataURL(targetMime, quality);
  let estimatedSize = estimateBase64Size(compressedDataUrl);

  // 循环降质以满足 <= 4MB 上限
  while (estimatedSize > maxSizeBytes && quality > 0.4) {
    quality -= 0.15;
    compressedDataUrl = canvas.toDataURL(targetMime, quality);
    estimatedSize = estimateBase64Size(compressedDataUrl);
  }

  // 若仍超限且尺寸较大，则进行二级尺寸减半
  if (estimatedSize > maxSizeBytes) {
    canvas.width = Math.round(targetWidth * 0.7);
    canvas.height = Math.round(targetHeight * 0.7);
    ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    compressedDataUrl = canvas.toDataURL(targetMime, 0.75);
    estimatedSize = estimateBase64Size(compressedDataUrl);
    targetWidth = canvas.width;
    targetHeight = canvas.height;
  }

  // 转换为 Blob / File 对象
  const blob = await new Promise<Blob>((resolve) => {
    canvas.toBlob(
      (b) => {
        resolve(b || new Blob([], { type: targetMime }));
      },
      targetMime,
      quality,
    );
  });

  const finalFile = new File([blob], file.name, {
    type: targetMime,
    lastModified: Date.now(),
  });

  return {
    file: finalFile,
    dataUrl: compressedDataUrl,
    width: targetWidth,
    height: targetHeight,
    size: estimatedSize,
    mimeType: targetMime,
    name: file.name,
  };
}

function fileToDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = (err) => reject(err);
    reader.readAsDataURL(file);
  });
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => resolve(img);
    img.onerror = (err) => reject(err);
    img.src = src;
  });
}

function estimateBase64Size(dataUrl: string): number {
  const commaIdx = dataUrl.indexOf(",");
  const base64Str = commaIdx !== -1 ? dataUrl.slice(commaIdx + 1) : dataUrl;
  const padding = base64Str.endsWith("==")
    ? 2
    : base64Str.endsWith("=")
      ? 1
      : 0;
  return Math.floor((base64Str.length * 3) / 4) - padding;
}
