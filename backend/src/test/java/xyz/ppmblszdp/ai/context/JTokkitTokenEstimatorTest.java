package xyz.ppmblszdp.ai.context;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JTokkitTokenEstimatorTest {

	@Test
	void testExactEstimationForEnglishText() {
		TokenEstimator jtokkit = new JTokkitTokenEstimator();
		TokenEstimator heuristic = new HeuristicTokenEstimator(1.1d);

		String englishText = "Hello world! This is a test prompt for token estimation.";
		int jtokkitCount = jtokkit.estimate(englishText);
		int heuristicCount = heuristic.estimate(englishText);

		// JTokkit (o200k_base) 精确分词约为 12 tokens
		assertTrue(jtokkitCount > 0 && jtokkitCount <= 13);
		// 启发式字符估算对英文较保守 (58 字符 * 0.25 + 4) * 1.1 = 21 tokens
		assertTrue(jtokkitCount < heuristicCount);
	}

	@Test
	void testEstimationForMessages() {
		TokenEstimator estimator = new JTokkitTokenEstimator();
		UserMessage message = new UserMessage("Hello AI");
		int count = estimator.estimate(List.of(message));

		// "Hello AI" = 2 tokens + 4 overhead = 6 tokens
		assertEquals(6, count);
	}

	@Test
	void testEmptyAndNull() {
		TokenEstimator estimator = new JTokkitTokenEstimator();
		assertEquals(0, estimator.estimate((String) null));
		assertEquals(0, estimator.estimate(""));
		assertEquals(0, estimator.estimate((List<Message>) null));
		assertEquals(0, estimator.estimate(List.of()));
	}
}
