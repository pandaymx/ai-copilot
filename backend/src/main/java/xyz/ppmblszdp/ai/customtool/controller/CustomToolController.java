package xyz.ppmblszdp.ai.customtool.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.ToolTestRequest;
import xyz.ppmblszdp.ai.customtool.dto.CustomToolDto.ToolTestResponse;
import xyz.ppmblszdp.ai.customtool.service.CustomToolService;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;

/**
 * 自定义工具 RESTful 管理控制器。
 *
 * <p>提供用户自定义工具的 CRUD、启用/停用开关以及在线运行测试端点。
 */
@RestController
@RequestMapping("/api/custom-tools")
public class CustomToolController {

    private final CustomToolService customToolService;
    private final AuthProperties authProperties;

    public CustomToolController(CustomToolService customToolService, AuthProperties authProperties) {
        this.customToolService = customToolService;
        this.authProperties = authProperties;
    }

    private String resolveUserId(ServerWebExchange exchange) {
        return UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
    }

    /**
     * 查询当前用户的所有自定义工具列表。
     */
    @GetMapping
    public ResponseEntity<List<CustomToolDto>> listTools(ServerWebExchange exchange) {
        String userId = resolveUserId(exchange);
        return ResponseEntity.ok(customToolService.listTools(userId));
    }

    /**
     * 获取指定工具详情。
     */
    @GetMapping("/{id}")
    public ResponseEntity<CustomToolDto> getTool(@PathVariable("id") String id, ServerWebExchange exchange) {
        String userId = resolveUserId(exchange);
        return customToolService.getTool(id, userId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }

    /**
     * 创建自定义工具。
     */
    @PostMapping
    public ResponseEntity<CustomToolDto> createTool(@RequestBody CustomToolDto dto, ServerWebExchange exchange) {
        String userId = resolveUserId(exchange);
        CustomToolDto created = customToolService.createTool(dto, userId);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新自定义工具。
     */
    @PutMapping("/{id}")
    public ResponseEntity<CustomToolDto> updateTool(
            @PathVariable("id") String id, @RequestBody CustomToolDto dto, ServerWebExchange exchange) {
        String userId = resolveUserId(exchange);
        CustomToolDto updated = customToolService.updateTool(id, dto, userId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 快速切换工具启用/禁用状态。
     */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleTool(@PathVariable("id") String id, ServerWebExchange exchange) {
        String userId = resolveUserId(exchange);
        boolean success = customToolService.toggleTool(id, userId);
        return ResponseEntity.ok(Map.of("success", success, "id", id));
    }

    /**
     * 删除指定工具。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTool(@PathVariable("id") String id, ServerWebExchange exchange) {
        String userId = resolveUserId(exchange);
        boolean deleted = customToolService.deleteTool(id, userId);
        return ResponseEntity.ok(Map.of("deleted", deleted, "id", id));
    }

    /**
     * 在线单次试运行与调试工具。
     */
    @PostMapping("/test")
    public ResponseEntity<ToolTestResponse> testTool(@RequestBody ToolTestRequest request, ServerWebExchange exchange) {
        String userId = resolveUserId(exchange);
        ToolTestResponse response = customToolService.testTool(request, userId);
        return ResponseEntity.ok(response);
    }
}
