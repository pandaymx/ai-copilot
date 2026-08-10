package xyz.ppmblszdp.ai.tool;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 工具注册表：将首批四个 {@code @Tool} 方法封装为 {@link ToolCallback} 列表，供 {@code ChatService}
 * 在 Agent 模式开启时注入 {@code ChatClient.toolCallbacks(...)}。
 */
@Configuration
public class AgentToolRegistry {

	@Bean
	public ToolCallback[] agentToolCallbacks(
			CalculatorTool calculatorTool,
			HttpRequestTool httpRequestTool,
			FileTool fileTool,
			KnowledgeQueryTool knowledgeQueryTool) {
		// ToolCallbacks.from 自动扫描对象上所有 @Tool 注解方法，FileTool 含 fileRead/fileWrite 两个
		ToolCallback[] calc = ToolCallbacks.from(calculatorTool);
		ToolCallback[] http = ToolCallbacks.from(httpRequestTool);
		ToolCallback[] file = ToolCallbacks.from(fileTool);
		ToolCallback[] rag = ToolCallbacks.from(knowledgeQueryTool);
		int total = calc.length + http.length + file.length + rag.length;
		ToolCallback[] all = new ToolCallback[total];
		int i = 0;
		for (ToolCallback c : calc) all[i++] = c;
		for (ToolCallback c : http) all[i++] = c;
		for (ToolCallback c : file) all[i++] = c;
		for (ToolCallback c : rag) all[i++] = c;
		return all;
	}
}
