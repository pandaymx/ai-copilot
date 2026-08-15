package xyz.ppmblszdp.ai.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import xyz.ppmblszdp.ai.dto.ChatFeedbackRequest;
import xyz.ppmblszdp.ai.evaluation.service.EvaluationService;
import xyz.ppmblszdp.ai.feedback.IntentFeedbackAccumulator;
import xyz.ppmblszdp.ai.reflection.ReflectionEngine;
import xyz.ppmblszdp.ai.repository.FeedbackRepository;

class FeedbackServiceTest {

    private static ChatFeedbackRequest makeRequest(
            String cid, String mid, String rating, String comment, String userId) {
        return new ChatFeedbackRequest(cid, mid, rating, comment, userId, null, null, null, null);
    }

    @Test
    void testSaveFeedbackDelegatesToRepository() {
        FeedbackRepository repository = mock(FeedbackRepository.class);
        EvaluationService evaluationService = mock(EvaluationService.class);
        IntentFeedbackAccumulator intentAccumulator = mock(IntentFeedbackAccumulator.class);
        ReflectionEngine reflectionEngine = mock(ReflectionEngine.class);
        FeedbackService service =
                new FeedbackService(repository, evaluationService, intentAccumulator, reflectionEngine);

        ChatFeedbackRequest request = makeRequest("conv-1", "msg-1", "THUMBS_UP", "Great answer", "user-1");
        service.saveFeedback("user-1", request);

        verify(repository).saveFeedback(eq("user-1"), eq(request));
    }

    @Test
    void testThumbsDownTriggersEvaluationPipeline() throws InterruptedException {
        FeedbackRepository repository = mock(FeedbackRepository.class);
        EvaluationService evaluationService = mock(EvaluationService.class);
        IntentFeedbackAccumulator intentAccumulator = mock(IntentFeedbackAccumulator.class);
        ReflectionEngine reflectionEngine = mock(ReflectionEngine.class);
        FeedbackService service =
                new FeedbackService(repository, evaluationService, intentAccumulator, reflectionEngine);

        ChatFeedbackRequest request = new ChatFeedbackRequest(
                "conv-1",
                "msg-2",
                "THUMBS_DOWN",
                "Wrong answer",
                "user-1",
                "gpt-4o",
                "CODE",
                "Write a quicksort",
                "Here is a bubblesort...");
        service.saveFeedback("user-1", request);

        // 等待异步管道完成（虚拟线程，通常 < 100ms）
        Thread.sleep(200);

        verify(evaluationService).ingestFeedbackCase(eq(request));
        verify(intentAccumulator).record(eq("CODE"), org.mockito.ArgumentMatchers.anyInt());
    }
}
