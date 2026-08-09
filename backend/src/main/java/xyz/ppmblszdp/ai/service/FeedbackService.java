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
	 * 保存点赞/点踩反馈。userId 来自服务端受信任身份，不再信任请求体。
	 */
	public void saveFeedback(String userId, ChatFeedbackRequest request) {
		feedbackRepository.saveFeedback(userId, request);
	}
}
