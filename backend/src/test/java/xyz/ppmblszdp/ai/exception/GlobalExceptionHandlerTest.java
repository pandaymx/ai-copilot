package xyz.ppmblszdp.ai.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleAiException() {
        AiException ex = new ProviderNotFoundException("unknown", false, List.of("openai"));
        ResponseEntity<Map<String, Object>> response = handler.handleAi(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PROVIDER_NOT_FOUND", response.getBody().get("code"));
    }

    @Test
    void testHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input");
        ResponseEntity<Map<String, Object>> response = handler.handleIllegal(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_ARGUMENT", response.getBody().get("code"));
    }

    @Test
    void testHandleOtherException() {
        RuntimeException ex = new RuntimeException("Unexpected error");
        ResponseEntity<Map<String, Object>> response = handler.handleOther(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().get("code"));
    }
}
