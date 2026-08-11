package xyz.ppmblszdp.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Tool Argument Augmentation (inner thinking) 增强型工具回调提供者。
 *
 * <p>核心职责：
 * <ul>
 *   <li>包装 {@link ToolCallback}，向 JSON Schema 注入 {@code innerThought} 字段（带 required 强约束），
 *       提示模型在执行工具前先写下思考过程 / 推理逻辑；</li>
 *   <li>在 {@code call} 执行时极其宽容地解析 {@code toolInput}：若包含 {@code innerThought}，
 *       提取并存入 {@link ToolContext}，同时从 JSON 中剥离该字段再传递给底层 target 方法（保障 Java 方法签名兼容性）；</li>
 *   <li><b>防弹兜底（Shield）</b>：若 JSON 解析或提取失败，自动 log 警告并原封不动地将原始 {@code toolInput}
 *       传给底层 delegate，绝不阻断工具的正常调用。</li>
 * </ul>
 */
@Component
public class AugmentedToolCallbackProvider {

	private static final Logger log = LoggerFactory.getLogger(AugmentedToolCallbackProvider.class);
	private static final ObjectMapper MAPPER = new ObjectMapper();

	public static final String INNER_THOUGHT_KEY = "innerThought";
	public static final String INNER_THOUGHT_DESC = "思考过程：在调用工具前记录你的推理分析与解题步骤";

	private static final ThreadLocal<Map<String, Object>> THREAD_CONTEXT = ThreadLocal.withInitial(HashMap::new);

	/**
	 * 将 innerThought 安全写入 ToolContext 或 ThreadLocal 存储
	 */
	public static void putInnerThought(ToolContext toolContext, String thought) {
		if (thought == null || thought.isBlank()) {
			return;
		}
		if (toolContext != null && toolContext.getContext() != null) {
			try {
				toolContext.getContext().put(INNER_THOUGHT_KEY, thought);
				return;
			} catch (UnsupportedOperationException ignored) {
				// ToolContext context map is unmodifiable, fallback to ThreadLocal
			}
		}
		THREAD_CONTEXT.get().put(INNER_THOUGHT_KEY, thought);
	}

	/**
	 * 从 ToolContext 或 ThreadLocal 中读取 innerThought
	 */
	public static String getInnerThought(ToolContext toolContext) {
		if (toolContext != null && toolContext.getContext() != null) {
			Object val = toolContext.getContext().get(INNER_THOUGHT_KEY);
			if (val instanceof String s && !s.isBlank()) {
				return s;
			}
		}
		Object val = THREAD_CONTEXT.get().get(INNER_THOUGHT_KEY);
		return val instanceof String s ? s : "";
	}

	/**
	 * 清理 ThreadLocal 存储
	 */
	public static void clearThreadContext() {
		THREAD_CONTEXT.get().clear();
	}

	/**
	 * 包裹单个 ToolCallback
	 */
	public ToolCallback wrap(ToolCallback delegate) {
		if (delegate == null) {
			return null;
		}
		if (delegate instanceof AugmentedToolCallback) {
			return delegate;
		}
		return new AugmentedToolCallback(delegate);
	}

	/**
	 * 包裹本地工具数组
	 */
	public ToolCallback[] wrapLocalTools(ToolCallback[] localTools) {
		if (localTools == null || localTools.length == 0) {
			return new ToolCallback[0];
		}
		return Arrays.stream(localTools)
				.filter(Objects::nonNull)
				.map(this::wrap)
				.toArray(ToolCallback[]::new);
	}

	/**
	 * 包裹工具数组（包含可选的 MCP 远程工具包裹开关）
	 */
	public ToolCallback[] wrapTools(ToolCallback[] localTools, ToolCallback[] mcpTools, boolean augmentMcp) {
		ToolCallback[] wrappedLocal = wrapLocalTools(localTools);
		ToolCallback[] wrappedMcp = mcpTools != null ? mcpTools : new ToolCallback[0];
		if (augmentMcp && wrappedMcp.length > 0) {
			wrappedMcp = Arrays.stream(wrappedMcp)
					.filter(Objects::nonNull)
					.map(this::wrap)
					.toArray(ToolCallback[]::new);
		}

		if (wrappedMcp.length == 0) {
			return wrappedLocal;
		}
		if (wrappedLocal.length == 0) {
			return wrappedMcp;
		}

		ToolCallback[] merged = new ToolCallback[wrappedLocal.length + wrappedMcp.length];
		System.arraycopy(wrappedLocal, 0, merged, 0, wrappedLocal.length);
		System.arraycopy(wrappedMcp, 0, merged, wrappedLocal.length, wrappedMcp.length);
		return merged;
	}

	/**
	 * 装饰器模式实现 ToolCallback
	 */
	public static class AugmentedToolCallback implements ToolCallback {

		private final ToolCallback delegate;
		private final ToolDefinition augmentedDefinition;

		public AugmentedToolCallback(ToolCallback delegate) {
			this.delegate = Objects.requireNonNull(delegate, "delegate ToolCallback must not be null");
			this.augmentedDefinition = augmentSchema(delegate.getToolDefinition());
		}

		public ToolCallback getDelegate() {
			return delegate;
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return augmentedDefinition;
		}

		@Override
		public String call(String toolInput) {
			return call(toolInput, null);
		}

		@Override
		public String call(String toolInput, ToolContext toolContext) {
			String cleanedInput = toolInput;
			try {
				if (toolInput != null && !toolInput.isBlank()) {
					try {
						JsonNode node = MAPPER.readTree(toolInput);
						if (node instanceof ObjectNode objNode) {
							if (objNode.has(INNER_THOUGHT_KEY)) {
								JsonNode thoughtNode = objNode.remove(INNER_THOUGHT_KEY);
								String thought = thoughtNode != null && !thoughtNode.isNull() ? thoughtNode.asText() : "";
								putInnerThought(toolContext, thought);
								cleanedInput = MAPPER.writeValueAsString(objNode);
							}
						}
					} catch (Exception e) {
						log.warn("[{}] Unstrict JSON parsing fallback for innerThought: {}, cause: {}",
								delegate.getToolDefinition().name(), toolInput, e.getMessage());
						cleanedInput = toolInput;
					}
				}

				if (toolContext != null) {
					return delegate.call(cleanedInput, toolContext);
				}
				return delegate.call(cleanedInput);
			} finally {
				clearThreadContext();
			}
		}

		private ToolDefinition augmentSchema(ToolDefinition original) {
			if (original == null) {
				return null;
			}
			String schema = original.inputSchema();
			if (schema == null || schema.isBlank()) {
				schema = "{\"type\":\"object\",\"properties\":{}}";
			}
			try {
				JsonNode root = MAPPER.readTree(schema);
				if (root instanceof ObjectNode objRoot) {
					ObjectNode propertiesNode;
					if (objRoot.has("properties") && objRoot.get("properties").isObject()) {
						propertiesNode = (ObjectNode) objRoot.get("properties");
					} else {
						propertiesNode = objRoot.putObject("properties");
					}

					// 注入 innerThought 字段定义
					ObjectNode innerThoughtProp = propertiesNode.putObject(INNER_THOUGHT_KEY);
					innerThoughtProp.put("type", "string");
					innerThoughtProp.put("description", INNER_THOUGHT_DESC);

					// 注入 required 强约束
					ArrayNode requiredNode;
					if (objRoot.has("required") && objRoot.get("required").isArray()) {
						requiredNode = (ArrayNode) objRoot.get("required");
					} else {
						requiredNode = objRoot.putArray("required");
					}
					boolean exists = false;
					for (JsonNode elem : requiredNode) {
						if (INNER_THOUGHT_KEY.equals(elem.asText())) {
							exists = true;
							break;
						}
					}
					if (!exists) {
						requiredNode.add(INNER_THOUGHT_KEY);
					}

					String augmentedSchema = MAPPER.writeValueAsString(objRoot);
					return ToolDefinition.builder()
							.name(original.name())
							.description(original.description())
							.inputSchema(augmentedSchema)
							.build();
				}
			} catch (Exception e) {
				log.warn("[{}] Failed to augment tool JSON schema with innerThought, returning original schema. Cause: {}",
						original.name(), e.getMessage());
			}
			return original;
		}
	}
}
