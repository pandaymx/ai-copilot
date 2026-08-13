package xyz.ppmblszdp.ai.safeguard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * SafeGuardAdvisor 自动配置。
 */
@Configuration
@EnableConfigurationProperties(SafeGuardProperties.class)
@ConditionalOnProperty(prefix = "app.ai.safeguard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SafeGuardConfig {

    private static final Logger log = LoggerFactory.getLogger(SafeGuardConfig.class);

    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordMatcher sensitiveWordMatcher(SafeGuardProperties properties) {
        log.info(
                "初始化 DefaultSensitiveWordMatcher 敏感词匹配器，内置词库数={}",
                properties.getSensitiveWords().size());
        return new DefaultSensitiveWordMatcher(properties.getSensitiveWords());
    }

    @Bean
    @ConditionalOnMissingBean
    public SafeGuardEngine safeGuardEngine(SensitiveWordMatcher matcher, SafeGuardProperties properties) {
        log.info(
                "初始化 SafeGuardEngine 安全打码/阻断引擎 (RequestPolicy={}, ResponsePolicy={})",
                properties.getRequestPolicy(),
                properties.getResponsePolicy());
        return new SafeGuardEngine(matcher, properties.getMaskReplacement());
    }

    @Bean
    @ConditionalOnMissingBean
    public SafeGuardAdvisor safeGuardAdvisor(SafeGuardEngine engine, SafeGuardProperties properties) {
        log.info("装配 Spring AI SafeGuardAdvisor (顺序: HIGHEST_PRECEDENCE)");
        return new SafeGuardAdvisor(engine, properties, Ordered.HIGHEST_PRECEDENCE);
    }
}
