package xyz.ppmblszdp.ai.service;

import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.dto.ChatFeedbackRequest;
import xyz.ppmblszdp.ai.repository.FeedbackRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeedbackServiceTest {

	@Test
	void testSaveFeedbackDelegatesToRepository() {
		FeedbackRepository repository = mock(FeedbackRepository.class);
		FeedbackService service = new FeedbackService(repository);

		ChatFeedbackRequest request = new ChatFeedbackRequest("conv-1", "msg-1", "THUMBS_UP", "Great answer", "user-1");
		service.saveFeedback(request);

		verify(repository).saveFeedback(request);
	}
}
