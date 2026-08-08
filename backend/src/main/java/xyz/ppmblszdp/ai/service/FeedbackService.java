package xyz.ppmblszdp.ai.service;

import org.springframework.stereotype.Service;
import xyz.ppmblszdp.ai.dto.ChatFeedbackRequest;
import xyz.ppmblszdp.ai.repository.FeedbackRepository;

/**
 * 消息评价反馈服务。
 */
@Service
public class FeedbackService {

	private final FeedbackRepository feedbackRepository;

	public FeedbackService(FeedbackRepository feedbackRepository) {
		this.feedbackRepository = feedbackRepository;
	}

	/**
	 * 保存点赞/点踩反馈。
	 */
	public void saveFeedback(ChatFeedbackRequest request) {
		feedbackRepository.saveFeedback(request);
	}
}
