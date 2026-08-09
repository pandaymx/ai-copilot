package xyz.ppmblszdp.ai.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import xyz.ppmblszdp.ai.dto.ChatFeedbackRequest;
import xyz.ppmblszdp.ai.dto.SessionDto;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.service.ChatService;
import xyz.ppmblszdp.ai.service.FeedbackService;
import xyz.ppmblszdp.ai.service.SessionService;
import xyz.ppmblszdp.ai.service.TitleService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 多租户认证与数据隔离测试（Controller 层，使用桩交换验证身份边界）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>strict 模式缺受信任 Header → 401</li>
 *   <li>dev 模式缺 Header + 请求体 userId → fallback 到请求体身份</li>
 *   <li>会话 GET / DELETE 跨用户访问 → 404</li>
 *   <li>会话列表仅返回当前用户数据</li>
 *   <li>反馈落库使用服务端身份而非请求体自报 userId</li>
 * </ul>
 */
class MultiTenantAuthTest {

	private SessionController sessionController;
	private ChatController chatController;
	private SessionService sessionService;
	private ChatService chatService;
	private FeedbackService feedbackService;
	private AuthProperties strictAuth;
	private AuthProperties devAuth;

	@BeforeEach
	void setUp() {
		sessionService = mock(SessionService.class);
		chatService = mock(ChatService.class);
		feedbackService = mock(FeedbackService.class);
		TitleService titleService = mock(TitleService.class);

		strictAuth = mock(AuthProperties.class);
		when(strictAuth.isStrict()).thenReturn(true);
		when(strictAuth.headerName()).thenReturn("X-User-Id");

		devAuth = mock(AuthProperties.class);
		when(devAuth.isStrict()).thenReturn(false);
		when(devAuth.headerName()).thenReturn("X-User-Id");

		sessionController = new SessionController(sessionService, strictAuth);
		chatController = new ChatController(chatService, titleService, sessionService, feedbackService, strictAuth);
	}

	private ServerWebExchange exchangeWithUser(String userId) {
		ServerWebExchange exchange = mock(ServerWebExchange.class);
		Map<String, Object> attrs = new HashMap<>();
		attrs.put(UserIdentityFilter.ATTR_HEADER_VALUE, userId);
		attrs.put(UserIdentityFilter.ATTR_HEADER_PRESENT, true);
		when(exchange.getAttributes()).thenReturn(attrs);
		return exchange;
	}

	private ServerWebExchange exchangeWithoutHeader() {
		ServerWebExchange exchange = mock(ServerWebExchange.class);
		Map<String, Object> attrs = new HashMap<>();
		attrs.put(UserIdentityFilter.ATTR_HEADER_VALUE, null);
		attrs.put(UserIdentityFilter.ATTR_HEADER_PRESENT, false);
		when(exchange.getAttributes()).thenReturn(attrs);
		return exchange;
	}

	@Test
	void strictModeMissingHeaderReturns401() {
		org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.web.server.ResponseStatusException.class,
				() -> sessionController.getSessions(exchangeWithoutHeader()));
		assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
	}

	@Test
	void devModeMissingHeaderFallsBackToAnonymous() {
		SessionController devController = new SessionController(sessionService, devAuth);
		when(sessionService.getAllSessions("anonymous")).thenReturn(List.of());
		var resp = devController.getSessions(exchangeWithoutHeader());
		assertEquals(0, resp.getBody().size());
		verify(sessionService).getAllSessions("anonymous");
	}

	@Test
	void listSessionsReturnsOnlyCurrentUser() {
		SessionDto own = new SessionDto("conv-own", "Own", 100L, true);
		when(sessionService.getAllSessions("alice")).thenReturn(List.of(own));
		var resp = sessionController.getSessions(exchangeWithUser("alice"));
		assertEquals(1, resp.getBody().size());
		assertEquals("conv-own", resp.getBody().get(0).id());
		verify(sessionService).getAllSessions("alice");
		verify(sessionService, never()).getAllSessions("bob");
	}

	@Test
	void crossUserGetSessionReturns404() {
		when(sessionService.getSessionDetail("conv-other", "alice")).thenReturn(Optional.empty());
		var resp = sessionController.getSessionDetail("conv-other", exchangeWithUser("alice"));
		assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
	}

	@Test
	void crossUserDeleteSessionReturns404() {
		when(sessionService.deleteSession("conv-other", "alice")).thenReturn(false);
		var resp = sessionController.deleteSession("conv-other", exchangeWithUser("alice"));
		assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
	}

	@Test
	void ownerDeleteSessionReturnsOk() {
		when(sessionService.deleteSession("conv-own", "alice")).thenReturn(true);
		var resp = sessionController.deleteSession("conv-own", exchangeWithUser("alice"));
		assertEquals(HttpStatus.OK, resp.getStatusCode());
	}

	@Test
	void titleEndpointCrossUserReturns404() {
		xyz.ppmblszdp.ai.dto.TitleRequest req = new xyz.ppmblszdp.ai.dto.TitleRequest("Hi", "Hello", "openai", "gpt-4o", "conv-other");
		when(sessionService.findSession("conv-other", "alice")).thenReturn(Optional.empty());
		StepVerifier.create(chatController.title(req, exchangeWithUser("alice")))
				.expectErrorMatches(t -> t instanceof org.springframework.web.server.ResponseStatusException ex
						&& ex.getStatusCode() == HttpStatus.NOT_FOUND)
				.verify();
	}

	@Test
	void feedbackUsesServerIdentityNotDtoUserId() {
		ChatFeedbackRequest req = new ChatFeedbackRequest("conv-1", "msg-1", "THUMBS_UP", "Good", "spoofed-user");
		doNothing().when(feedbackService).saveFeedback(eq("alice"), eq(req));
		StepVerifier.create(chatController.feedback(req, exchangeWithUser("alice")))
				.assertNext(map -> assertEquals(true, map.get("success")))
				.verifyComplete();
		verify(feedbackService).saveFeedback(eq("alice"), eq(req));
		verify(feedbackService, never()).saveFeedback(eq("spoofed-user"), eq(req));
	}
}
