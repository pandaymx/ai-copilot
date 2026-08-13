package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.Stack;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 计算器工具：安全表达式求值（仅支持 {@code + - * / % ^ ( )} 与小数，禁止任何脚本引擎以防任意代码执行）。
 */
@Component
public class CalculatorTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Tool(description = "数学计算器：对四则运算表达式求值，支持 + - * / % ^ 与括号，例如 \"12 * (3 + 4) / 2\"")
    public String calculate(
            @ToolParam(description = "数学表达式，仅含数字、运算符 + - * / % ^ 与括号，例如 \"(1+2)*3\"") String expression,
            ToolContext toolContext) {
        String argsJson = toJson("expression", expression);
        return ToolEventEmitter.from(toolContext).executeWithEvent("calculator", argsJson, toolContext, () -> {
            double value = SafeMathEvaluator.eval(expression);
            return "{\"output\":" + value + "}";
        });
    }

    private static String toJson(String key, String value) {
        try {
            return MAPPER.writeValueAsString(java.util.Map.of(key, value == null ? "" : value));
        } catch (Exception e) {
            return "{\"" + key + "\":\"\"}";
        }
    }

    /** 受限算术求值器：基于 Dijkstra 双栈算法，拒绝任何非数学 token。 */
    static final class SafeMathEvaluator {
        private static final Set<Character> OPS = Set.of('+', '-', '*', '/', '%', '^');

        static double eval(String expr) {
            if (expr == null || expr.isBlank()) {
                throw new IllegalArgumentException("表达式为空");
            }
            String normalized = expr.replaceAll("\\s+", "");
            if (!normalized.matches("[0-9+\\-*/%^().]+")) {
                throw new IllegalArgumentException("表达式包含非法字符，仅允许数字与运算符 + - * / % ^ ()");
            }
            Stack<Double> values = new Stack<>();
            Stack<Character> ops = new Stack<>();
            for (int i = 0; i < normalized.length(); i++) {
                char c = normalized.charAt(i);
                if (c == ' ') continue;
                if (Character.isDigit(c) || c == '.') {
                    StringBuilder sb = new StringBuilder();
                    while (i < normalized.length()
                            && (Character.isDigit(normalized.charAt(i)) || normalized.charAt(i) == '.')) {
                        sb.append(normalized.charAt(i++));
                    }
                    i--;
                    values.push(Double.parseDouble(sb.toString()));
                } else if (c == '(') {
                    ops.push(c);
                } else if (c == ')') {
                    while (!ops.isEmpty() && ops.peek() != '(') {
                        values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                    }
                    if (ops.isEmpty()) throw new IllegalArgumentException("括号不匹配");
                    ops.pop();
                } else if (OPS.contains(c)) {
                    while (!ops.isEmpty() && ops.peek() != '(' && precedence(ops.peek()) >= precedence(c)) {
                        values.push(applyOp(ops.pop(), values.pop(), values.pop()));
                    }
                    ops.push(c);
                } else {
                    throw new IllegalArgumentException("非法字符: " + c);
                }
            }
            while (!ops.isEmpty()) {
                if (ops.peek() == '(') throw new IllegalArgumentException("括号不匹配");
                values.push(applyOp(ops.pop(), values.pop(), values.pop()));
            }
            if (values.size() != 1) throw new IllegalArgumentException("表达式解析失败");
            return values.pop();
        }

        private static int precedence(char op) {
            return switch (op) {
                case '+', '-' -> 1;
                case '*', '/', '%' -> 2;
                case '^' -> 3;
                default -> 0;
            };
        }

        private static double applyOp(char op, double b, double a) {
            return switch (op) {
                case '+' -> a + b;
                case '-' -> a - b;
                case '*' -> a * b;
                case '/' -> {
                    if (b == 0) throw new ArithmeticException("除数不能为 0");
                    yield a / b;
                }
                case '%' -> a % b;
                case '^' -> Math.pow(a, b);
                default -> throw new IllegalArgumentException("未知运算符: " + op);
            };
        }
    }
}
