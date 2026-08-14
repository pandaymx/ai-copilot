package xyz.ppmblszdp.ai.clarification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 主动澄清自动配置类。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClarificationProperties.class)
public class ClarificationConfig {

    @Bean
    @ConditionalOnMissingBean
    public ClarificationEngine clarificationEngine(ClarificationProperties properties) {
        return new ClarificationEngine(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ClarificationAdvisor clarificationAdvisor(ClarificationEngine engine, ClarificationProperties properties) {
        return new ClarificationAdvisor(engine, properties);
    }
}
