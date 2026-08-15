package xyz.ppmblszdp.ai.persona.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import xyz.ppmblszdp.ai.identity.AuthProperties;
import xyz.ppmblszdp.ai.identity.UserIdentityFilter;
import xyz.ppmblszdp.ai.persona.dto.CreatePersonaReq;
import xyz.ppmblszdp.ai.persona.dto.PersonaDto;
import xyz.ppmblszdp.ai.persona.dto.PersonaMatchDto;
import xyz.ppmblszdp.ai.persona.dto.UpdatePersonaReq;
import xyz.ppmblszdp.ai.persona.service.PersonaStoreService;

/**
 * 智能体角色市场 REST 控制器（/api/personas）。
 */
@RestController
@RequestMapping("/api/personas")
public class PersonaController {

    private final PersonaStoreService personaStoreService;
    private final AuthProperties authProperties;

    public PersonaController(PersonaStoreService personaStoreService, AuthProperties authProperties) {
        this.personaStoreService = personaStoreService;
        this.authProperties = authProperties;
    }

    /**
     * 获取角色市场列表（包含内置与当前用户的自定义角色）。
     */
    @GetMapping
    public ResponseEntity<List<PersonaDto>> listPersonas(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        List<PersonaDto> list = personaStoreService.listPersonas(userId, category, keyword);
        return ResponseEntity.ok(list);
    }

    /**
     * 获取特定角色详情。
     */
    @GetMapping("/{id}")
    public ResponseEntity<PersonaDto> getPersona(@PathVariable String id, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        PersonaDto persona = personaStoreService.getPersona(id, userId);
        if (persona == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(persona);
    }

    /**
     * 创建用户自定义角色。
     */
    @PostMapping
    public ResponseEntity<PersonaDto> createPersona(@RequestBody CreatePersonaReq req, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        PersonaDto created = personaStoreService.createCustomPersona(userId, req);
        return ResponseEntity.ok(created);
    }

    /**
     * 更新用户自定义角色。
     */
    @PutMapping("/{id}")
    public ResponseEntity<PersonaDto> updatePersona(
            @PathVariable String id, @RequestBody UpdatePersonaReq req, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            PersonaDto updated = personaStoreService.updateCustomPersona(id, userId, req);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * 删除用户自定义角色。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePersona(@PathVariable String id, ServerWebExchange exchange) {
        String userId = UserIdentityFilter.resolveIdentity(exchange, null, authProperties);
        try {
            boolean deleted = personaStoreService.deleteCustomPersona(id, userId);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(403).build();
        }
    }

    /**
     * 基于任务目标或意图进行智能角色推荐匹配。
     */
    @PostMapping("/match")
    public ResponseEntity<PersonaMatchDto.MatchResp> matchPersona(@RequestBody PersonaMatchDto.MatchReq req) {
        PersonaMatchDto.MatchResp resp = personaStoreService.matchPersona(req != null ? req.goal() : null);
        return ResponseEntity.ok(resp);
    }
}
