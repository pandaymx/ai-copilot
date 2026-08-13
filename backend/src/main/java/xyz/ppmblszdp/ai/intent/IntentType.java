package xyz.ppmblszdp.ai.intent;

/**
 * 通用意图分类枚举。
 */
public enum IntentType {
    /** 闲聊 / 常规问答 */
    CHAT("闲聊"),
    /** 代码编写 / 调试 / 重构 / 编程 */
    CODE("代码"),
    /** 多语言翻译 / 词汇润色 */
    TRANSLATION("翻译"),
    /** 文本写作 / 文章撰写 / 总结周报 */
    WRITING("写作"),
    /** 深度分析 / 数据推理 / 逻辑分析 */
    ANALYSIS("分析"),
    /** 实时信息搜索 / 资讯检索 */
    SEARCH("搜索"),
    /** 数学计算 / 公式推导 / 逻辑求解 */
    MATH("数学"),
    /** 多模态视觉 / 图片输入处理 */
    MULTIMODAL("多模态"),
    /** 图像创作 / AI 绘图 */
    IMAGE("图片生成");

    private final String label;

    IntentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
