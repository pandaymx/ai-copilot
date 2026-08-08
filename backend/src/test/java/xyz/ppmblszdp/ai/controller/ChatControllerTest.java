package xyz.ppmblszdp.ai.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import xyz.ppmblszdp.ai.dto.ChatFeedbackRequest;
import xyz.ppmblszdp.ai.dto.ChatRequest;
import xyz.ppmblszdp.ai.dto.ChatResponseDto;
import xyz.ppmblszdp.ai.dto.TitleRequest;
import xyz.ppmblszdp.ai.service.ChatService;
import xyz.ppmblszdp.ai.service.FeedbackService;
import xyz.ppmblszdp.ai.service.SessionService;
import xyz.ppmblszdp.ai.service.TitleService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

	private ChatService chatService;
	private TitleService titleService;
	private SessionService sessionService;
	private FeedbackService feedbackService;
	private ChatController chatController;

	@BeforeEach
	void setUp() {
		chatService = mock(ChatService.class);
		titleService = mock(TitleService.class);
		sessionService = mock(SessionService.class);
		feedbackService = mock(FeedbackService.class);

		chatController = new ChatController(chatService, titleService, sessionService, feedbackService);
	}

	@Test
	void testChatEndpoint() {
		ChatRequest req = new ChatRequest("Hi", null, "openai", "gpt-4o", null, "conv-1", "user-1", null);
		ChatResponseDto dto = new ChatResponseDto("Hello!", "openai", "gpt-4o", "conv-1", null, null);
		when(chatService.chat(req)).thenReturn(Mono.just(dto));

		StepVerifier.create(chatController.chat(req))
				.assertNext(res -> {
					assertNotNull(res);
					assertEquals("Hello!", res.content());
					assertEquals("conv-1", res.conversationId());
				})
				.verifyComplete();
	}

	@Test
	void testTitleEndpoint() {
		TitleRequest req = new TitleRequest("Hi", "Hello", "openai", "gpt-4o", "conv-1");
		when(titleService.generateTitle("Hi", "Hello", "openai", "gpt-4o")).thenReturn(Mono.just("Greeting Session"));

		StepVerifier.create(chatController.title(req))
				.assertNext(res -> {
					assertNotNull(res);
					assertEquals("Greeting Session", res.title());
				})
				.verifyComplete();

		verify(sessionService).renameSession("conv-1", "Greeting Session");
	}

	@Test
	void testFeedbackEndpoint() {
		ChatFeedbackRequest req = new ChatFeedbackRequest("conv-1", "msg-1", "THUMBS_UP", "Good", "user-1");

		StepVerifier.create(chatController.feedback(req))
				.assertNext(map -> {
					assertNotNull(map);
					assertTrue((Boolean) map.get("success"));
				})
				.verifyComplete();

		verify(feedbackService).saveFeedback(req);
	}
}
