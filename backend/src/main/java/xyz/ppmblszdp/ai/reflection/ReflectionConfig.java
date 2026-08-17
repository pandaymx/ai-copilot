package xyz.ppmblszdp.ai.reflection;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.ppmblszdp.ai.registry.ProviderRegistry;
import xyz.ppmblszdp.ai.registry.TaskModelResolver;

/**
 * AI 自我反思与纠错 Spring 自动装配配置类。
 */
@Configuration
@EnableConfigurationProperties(ReflectionProperties.class)
public class ReflectionConfig {

    @Bean
    @ConditionalOnMissingBean
    public ReflectionEngine reflectionEngine(
            ProviderRegistry providerRegistry, ReflectionProperties properties, TaskModelResolver taskModelResolver) {
        return new ReflectionEngine(providerRegistry, properties, taskModelResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ai.reflection", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ReflectionAdvisor reflectionAdvisor(ReflectionEngine reflectionEngine, ReflectionProperties properties) {
        return new ReflectionAdvisor(reflectionEngine, properties);
    }
}
