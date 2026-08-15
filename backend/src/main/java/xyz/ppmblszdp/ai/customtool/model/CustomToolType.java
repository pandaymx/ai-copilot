package xyz.ppmblszdp.ai.customtool.model;

/**
 * 用户自定义工具类型枚举。
 */
public enum CustomToolType {
    /** HTTP RESTful API 调用工具 */
    HTTP("HTTP API"),
    /** Python / JavaScript 安全沙箱脚本工具 */
    SCRIPT("脚本沙箱"),
    /** Prompt 模板驱动的领域虚拟工具 */
    PROMPT("Prompt 工具");

    private final String label;

    CustomToolType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
