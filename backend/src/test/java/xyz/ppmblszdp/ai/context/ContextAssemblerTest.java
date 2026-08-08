package xyz.ppmblszdp.ai.context;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import xyz.ppmblszdp.ai.config.AiProviderProperties;
import xyz.ppmblszdp.ai.dto.ChatMessageDto;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblerTest {

	private final AiProviderProperties properties = new AiProviderProperties(null, null, null, null, "You are a helpful assistant", null, null, null, null);
	private final TokenEstimator estimator = new JTokkitTokenEstimator();
	private final ContextAssembler assembler = new ContextAssembler(properties, estimator);

	@Test
	void testAssembleWithTokenBudgetSlidingWindow() {
		List<ChatMessageDto> history = List.of(
				new ChatMessageDto("user", "Old user message 1"),
				new ChatMessageDto("assistant", "Old assistant reply 1"),
				new ChatMessageDto("user", "Recent user message 2"),
				new ChatMessageDto("assistant", "Recent assistant reply 2")
		);

		// 给定足够大的 Token 窗口（32768）
		List<Message> result = assembler.assemble("Current question", history, null, null, 32768);

		// 包含 system 保底在首，加上历史与当前消息
		assertTrue(result.size() >= 2);
		assertTrue(result.get(0) instanceof SystemMessage);
		assertTrue(result.get(result.size() - 1) instanceof UserMessage);
		assertEquals("Current question", result.get(result.size() - 1).getText());
	}

	@Test
	void testTrimMessagesSpringAiMessageList() {
		List<Message> messages = List.of(
				new SystemMessage("You are a helpful assistant"),
				new UserMessage("Message 1"),
				new AssistantMessage("Reply 1"),
				new UserMessage("Message 2"),
				new AssistantMessage("Reply 2")
		);

		List<Message> trimmed = assembler.trimMessages(messages, 32768);

		assertEquals(5, trimmed.size());
		assertTrue(trimmed.get(0) instanceof SystemMessage);
	}

	@Test
	void testSlidingWindowStripsOrphanLeadingAssistantMessage() {
		List<ChatMessageDto> history = List.of(
				new ChatMessageDto("assistant", "Orphan assistant reply without user prompt"),
				new ChatMessageDto("user", "User question 1"),
				new ChatMessageDto("assistant", "Assistant answer 1")
		);

		List<Message> result = assembler.assemble("Current question", history, null, null, 32768);

		assertEquals(4, result.size());
		assertTrue(result.get(0) instanceof SystemMessage);
		assertTrue(result.get(1) instanceof UserMessage);
		assertEquals("User question 1", result.get(1).getText());
		assertTrue(result.get(2) instanceof AssistantMessage);
		assertEquals("Assistant answer 1", result.get(2).getText());
		assertTrue(result.get(3) instanceof UserMessage);
		assertEquals("Current question", result.get(3).getText());
	}

	@Test
	void testTrimMessagesStripsOrphanLeadingAssistantMessage() {
		List<Message> messages = List.of(
				new SystemMessage("You are a helpful assistant"),
				new AssistantMessage("Orphan assistant reply"),
				new UserMessage("Message 1"),
				new AssistantMessage("Reply 1")
		);

		List<Message> trimmed = assembler.trimMessages(messages, 32768);

		assertEquals(3, trimmed.size());
		assertTrue(trimmed.get(0) instanceof SystemMessage);
		assertTrue(trimmed.get(1) instanceof UserMessage);
		assertEquals("Message 1", trimmed.get(1).getText());
		assertTrue(trimmed.get(2) instanceof AssistantMessage);
	}
}
