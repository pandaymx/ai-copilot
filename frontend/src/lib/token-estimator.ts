/**
 * 前端轻量级分词与 Token 估算器。
 *
 * 针对中英混排、代码、标点与空白字符采用多模态启发式加权算法：
 * - CJK 字符（中日韩汉字/假名/全角符号）：通常每个字符约对应 0.7 ~ 1.0 个 Token。
 * - 英文单词与拉丁字母：通常平均 3.5 ~ 4 个字符对应 1 个 Token。
 * - 代码结构与空白符号（缩进、换行、括号）：独立分块估算。
 */

// CJK 汉字、日韩文字与全角标点区间
const CJK_REGEX =
  /[\u4e00-\u9fa5\u3040-\u30ff\u3400-\u4dbf\uf900-\ufaff\uff00-\uffef]/g;

// 英文单词与连续数字
const WORD_OR_NUM_REGEX = /[a-zA-Z0-9_]+/g;

// 常见代码/标点符号（排除空白）
const SYMBOL_REGEX =
  /[^\s\w\u4e00-\u9fa5\u3040-\u30ff\u3400-\u4dbf\uf900-\ufaff\uff00-\uffef]/g;

/**
 * 快速估算输入文本的 Prompt Token 数量。
 *
 * @param text 用户输入的草稿或完整消息
 * @returns 预估 Token 整数
 */
export function estimatePromptTokens(text: string): number {
  if (!text || text.trim().length === 0) {
    return 0;
  }

  // 1. 统计 CJK 字符数量（按 1 char ≈ 0.9 token 加权）
  const cjkMatches = text.match(CJK_REGEX);
  const cjkCount = cjkMatches ? cjkMatches.length : 0;
  const cjkTokens = Math.ceil(cjkCount * 0.9);

  // 2. 统计英文单词/数字（按每个词 ≈ 1.25 token，或按字符长度 4 换算）
  const wordsMatches = text.match(WORD_OR_NUM_REGEX);
  let wordTokens = 0;
  if (wordsMatches) {
    for (const w of wordsMatches) {
      if (w.length <= 4) {
        wordTokens += 1;
      } else {
        wordTokens += Math.ceil(w.length / 3.8);
      }
    }
  }

  // 3. 统计标点与特殊符号（大部分 1 个标点对应 1 个 token）
  const symbolMatches = text.match(SYMBOL_REGEX);
  const symbolTokens = symbolMatches ? symbolMatches.length : 0;

  // 4. 空白与换行（连续多个空格或连续换行压缩计算）
  const newlineCount = (text.match(/\n+/g) || []).length;
  const extraWhitespaceTokens = Math.floor(newlineCount * 0.5);

  const total = Math.max(
    1,
    Math.round(cjkTokens + wordTokens + symbolTokens + extraWhitespaceTokens),
  );
  return total;
}

/**
 * 根据预估 Token 数与模型输入千 Token 单价计算预估费用（元，人民币）。
 *
 * @param promptTokens 预估 Token 数
 * @param inputPricePerK 模型输入千 Token 单价（元/k tokens），默认为 0.001
 * @returns 预估费用（元）
 */
export function estimatePromptCostRmb(
  promptTokens: number,
  inputPricePerK?: number,
): number {
  if (promptTokens <= 0) return 0;
  const price = inputPricePerK ?? 0.001;
  return Number(((promptTokens / 1000) * price).toFixed(6));
}
