package xyz.ppmblszdp.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import xyz.ppmblszdp.ai.dto.SessionDto;
import xyz.ppmblszdp.ai.repository.SessionRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

	private SessionRepository sessionRepository;
	private ObjectProvider<ChatMemory> chatMemoryProvider;
	private ChatMemory chatMemory;
	private SessionService sessionService;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		sessionRepository = mock(SessionRepository.class);
		chatMemoryProvider = mock(ObjectProvider.class);
		chatMemory = mock(ChatMemory.class);

		when(chatMemoryProvider.getIfAvailable()).thenReturn(chatMemory);
		sessionService = new SessionService(sessionRepository, chatMemoryProvider);
	}

	@Test
	void testGetAllSessions() {
		List<SessionDto> list = List.of(new SessionDto("conv-1", "Test Title", 1000L, true));
		when(sessionRepository.findAll()).thenReturn(list);

		List<SessionDto> result = sessionService.getAllSessions();
		assertEquals(1, result.size());
		assertEquals("conv-1", result.get(0).id());
	}

	@Test
	void testGetSessionDetailConstructsMessages() {
		SessionDto meta = new SessionDto("conv-1", "Title", 1000L, false);
		when(sessionRepository.findById("conv-1")).thenReturn(Optional.of(meta));
		when(chatMemory.get("conv-1")).thenReturn(List.of(
				new SystemMessage("System prompt"),
				new UserMessage("Hi"),
				new AssistantMessage("Hello")
		));

		Optional<SessionDto.SessionDetail> detailOpt = sessionService.getSessionDetail("conv-1");
		assertTrue(detailOpt.isPresent());
		SessionDto.SessionDetail detail = detailOpt.get();
		assertEquals("conv-1", detail.id());
		assertEquals(2, detail.messages().size()); // System message filtered out
		assertEquals("user", detail.messages().get(0).role());
		assertEquals("Hi", detail.messages().get(0).content());
		assertEquals("assistant", detail.messages().get(1).role());
		assertEquals("Hello", detail.messages().get(1).content());
	}

	@Test
	void testTouchSession() {
		sessionService.touchSession("conv-1", "Fallback Title");
		verify(sessionRepository).touchSession(eq("conv-1"), eq("Fallback Title"), anyLong());
	}

	@Test
	void testRenameSessionExisting() {
		when(sessionRepository.findById("conv-1")).thenReturn(Optional.of(new SessionDto("conv-1", "Old Title", 1000L, false)));

		boolean success = sessionService.renameSession("conv-1", "New Title");
		assertTrue(success);
		verify(sessionRepository).updateTitle("conv-1", "New Title", false);
	}

	@Test
	void testRenameSessionNew() {
		when(sessionRepository.findById("conv-1")).thenReturn(Optional.empty());

		boolean success = sessionService.renameSession("conv-1", "New Title");
		assertTrue(success);
		verify(sessionRepository).upsertSession(eq("conv-1"), eq("New Title"), anyLong(), eq(false));
	}

	@Test
	void testDeleteSession() {
		sessionService.deleteSession("conv-1");
		verify(sessionRepository).deleteById("conv-1");
		verify(chatMemory).clear("conv-1");
	}
}
