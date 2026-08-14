package xyz.ppmblszdp.ai.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 变量与表达式解析器：
 * 安全解析 `${input.xxx}` 与 `${nodes.nodeId.output.nestedProp}` 等占位符，拒绝任何外部类或字节码执行。
 */
public class SafeVariableResolver {

    private static final Logger log = LoggerFactory.getLogger(SafeVariableResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_.\\[\\]]+)\\}");

    /**
     * 替换模板字符串中的占位符变量。
     *
     * @param template 原始模板字符串
     * @param inputs   工作流入参映射
     * @param nodeOutputs 节点执行输出快照映射（nodeId -> output）
     * @return 替换后的完整字符串
     */
    public static String resolveTemplate(String template, Map<String, Object> inputs, Map<String, Object> nodeOutputs) {
        if (template == null || !template.contains("${")) {
            return template == null ? "" : template;
        }

        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String path = matcher.group(1);
            Object resolved = resolvePath(path, inputs, nodeOutputs);
            String replacement = stringify(resolved);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 解析单个路径值。
     */
    public static Object resolvePath(String path, Map<String, Object> inputs, Map<String, Object> nodeOutputs) {
        if (path == null || path.isBlank()) return null;

        String[] parts = path.split("\\.", 2);
        String root = parts[0];
        String subPath = parts.length > 1 ? parts[1] : null;

        if ("input".equalsIgnoreCase(root) || "inputs".equalsIgnoreCase(root)) {
            if (subPath == null) return inputs;
            return extractNested(inputs, subPath);
        }

        if ("nodes".equalsIgnoreCase(root) || "node".equalsIgnoreCase(root)) {
            if (subPath == null) return nodeOutputs;
            String[] nodeParts = subPath.split("\\.", 2);
            String nodeId = nodeParts[0];
            Object nodeOutput = nodeOutputs.get(nodeId);
            if (nodeParts.length == 1) return nodeOutput;

            String nodeSubPath = nodeParts[1];
            if (nodeSubPath.startsWith("output")) {
                if ("output".equals(nodeSubPath)) return nodeOutput;
                String remaining = nodeSubPath.substring("output.".length());
                return extractNested(nodeOutput, remaining);
            }
            return extractNested(nodeOutput, nodeSubPath);
        }

        // 兜底直接在 inputs 或 nodeOutputs 中查找
        if (inputs != null && inputs.containsKey(path)) {
            return inputs.get(path);
        }
        if (nodeOutputs != null && nodeOutputs.containsKey(path)) {
            return nodeOutputs.get(path);
        }
        return "";
    }

    /**
     * 安全求值条件分支表达式。
     * 例如："${nodes.check.output} == 'PASS'", "contains('合规')", "${input.score} > 80"
     */
    public static boolean evaluateCondition(
            String expression, Map<String, Object> inputs, Map<String, Object> nodeOutputs) {
        if (expression == null || expression.isBlank()) return true;

        String resolved = resolveTemplate(expression, inputs, nodeOutputs).trim();

        // 布尔字面量
        if ("true".equalsIgnoreCase(resolved) || "1".equals(resolved) || "yes".equalsIgnoreCase(resolved)) {
            return true;
        }
        if ("false".equalsIgnoreCase(resolved) || "0".equals(resolved) || "no".equalsIgnoreCase(resolved)) {
            return false;
        }

        // 相等判断: "A == B" or "A != B"
        if (resolved.contains("==")) {
            String[] tokens = resolved.split("==", 2);
            return cleanToken(tokens[0]).equalsIgnoreCase(cleanToken(tokens[1]));
        }
        if (resolved.contains("!=")) {
            String[] tokens = resolved.split("!=", 2);
            return !cleanToken(tokens[0]).equalsIgnoreCase(cleanToken(tokens[1]));
        }

        // 包含判断: "contains('keyword')"
        if (resolved.contains("contains(")) {
            int start = resolved.indexOf("contains(") + 9;
            int end = resolved.indexOf(")", start);
            if (end > start) {
                String target = cleanToken(resolved.substring(start, end));
                String source = resolved.substring(0, resolved.indexOf("contains("));
                return source.contains(target);
            }
        }

        // 数值比较: ">", "<", ">=", "<="
        try {
            if (resolved.contains(">=")) {
                String[] tokens = resolved.split(">=", 2);
                return Double.parseDouble(cleanToken(tokens[0])) >= Double.parseDouble(cleanToken(tokens[1]));
            }
            if (resolved.contains("<=")) {
                String[] tokens = resolved.split("<=", 2);
                return Double.parseDouble(cleanToken(tokens[0])) <= Double.parseDouble(cleanToken(tokens[1]));
            }
            if (resolved.contains(">")) {
                String[] tokens = resolved.split(">", 2);
                return Double.parseDouble(cleanToken(tokens[0])) > Double.parseDouble(cleanToken(tokens[1]));
            }
            if (resolved.contains("<")) {
                String[] tokens = resolved.split("<", 2);
                return Double.parseDouble(cleanToken(tokens[0])) < Double.parseDouble(cleanToken(tokens[1]));
            }
        } catch (Exception ignored) {
        }

        return !resolved.isBlank();
    }

    @SuppressWarnings("unchecked")
    private static Object extractNested(Object root, String path) {
        if (root == null || path == null || path.isBlank()) return root;

        try {
            if (root instanceof Map<?, ?> map) {
                String[] parts = path.split("\\.", 2);
                Object val = map.get(parts[0]);
                if (parts.length == 1) return val;
                return extractNested(val, parts[1]);
            }

            // 若为 JSON 字符串对象，解析为 JsonNode 查询
            if (root instanceof String str && (str.startsWith("{") || str.startsWith("["))) {
                JsonNode json = MAPPER.readTree(str);
                String[] parts = path.split("\\.");
                JsonNode curr = json;
                for (String part : parts) {
                    if (curr == null) return null;
                    curr = curr.get(part);
                }
                return curr != null ? (curr.isTextual() ? curr.asText() : curr.toString()) : null;
            }
        } catch (Exception e) {
            log.debug("提取嵌套属性失败 {} from {}: {}", path, root, e.getMessage());
        }
        return null;
    }

    private static String cleanToken(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String stringify(Object val) {
        if (val == null) return "";
        if (val instanceof String s) return s;
        if (val instanceof Number || val instanceof Boolean) return String.valueOf(val);
        try {
            return MAPPER.writeValueAsString(val);
        } catch (Exception e) {
            return String.valueOf(val);
        }
    }
}
