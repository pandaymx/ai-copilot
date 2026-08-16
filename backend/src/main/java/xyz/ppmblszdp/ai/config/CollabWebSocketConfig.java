package xyz.ppmblszdp.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy;
import xyz.ppmblszdp.ai.collab.CollaborationBus;
import xyz.ppmblszdp.ai.controller.CollabController;
import xyz.ppmblszdp.ai.service.ParticipantAuthService;

/**
 * WebFlux 协作 WebSocket 路由配置。
 *
 * <p>将 {@code /api/collab} 映射到 {@link CollabController}。使用响应式 Netty 升级策略，
 * 不引入阻塞式 STOMP broker（保持 WebFlux 非阻塞语义）。
 */
@Configuration
public class CollabWebSocketConfig {

    @Bean
    public CollaborationBus collaborationBus() {
        return new CollaborationBus();
    }

    @Bean
    public CollabController collabController(CollaborationBus bus, ParticipantAuthService authService) {
        return new CollabController(bus, authService, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Bean
    public WebSocketHandlerAdapter collabWebSocketHandlerAdapter(WebSocketService webSocketService) {
        return new WebSocketHandlerAdapter(webSocketService);
    }

    @Bean
    public WebSocketService collabWebSocketService() {
        return new org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService(
                new ReactorNettyRequestUpgradeStrategy());
    }

    @Bean
    public SimpleUrlHandlerMapping collabHandlerMapping(CollabController collabController) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(java.util.Map.of("/api/collab", collabController));
        // 低优先级，避免覆盖现有关注 REST 路由
        mapping.setOrder(-1);
        return mapping;
    }
}
