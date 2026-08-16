package xyz.ppmblszdp.ai.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;

/**
 * 当前用户身份端点。前端建立协作 WebSocket 前，先经此获取 userId
 * （浏览器原生 WS 不支持自定义 Header，故身份经 query param 传递，见修正点 1）。
 */
@RestController
@RequestMapping("/api")
public class MeController {

    private final AuthProperties authProperties;

    public MeController(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    @GetMapping("/me")
    public Map<String, String> me(ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        return Map.of("userId", userId);
    }
}
