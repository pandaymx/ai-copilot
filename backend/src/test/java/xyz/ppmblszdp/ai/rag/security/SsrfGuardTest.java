package xyz.ppmblszdp.ai.rag.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SsrfGuardTest {

    @Test
    void shouldBlockMetadataServiceIP() {
        // 模拟云元数据服务地址 169.254.169.254，断言抛出 SsrfBlockedException
        assertThatThrownBy(() -> SsrfGuard.validate("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(SsrfBlockedException.class)
                .hasMessageContaining("169.254.169.254");
    }

    @Test
    void shouldBlockLoopbackAddress() {
        // 模拟传入 127.0.0.1:8080/admin，断言被拦截
        assertThatThrownBy(() -> SsrfGuard.validate("http://127.0.0.1:8080/admin"))
                .isInstanceOf(SsrfBlockedException.class)
                .hasMessageContaining("回环地址");
    }

    @Test
    void shouldBlockPrivateNetwork10() {
        // 内网 10.x.x.x 应被拦截
        assertThatThrownBy(() -> SsrfGuard.validate("http://10.0.0.1/api"))
                .isInstanceOf(SsrfBlockedException.class)
                .hasMessageContaining("内网地址");
    }

    @Test
    void shouldBlockPrivateNetwork192() {
        // 内网 192.168.x.x 应被拦截
        assertThatThrownBy(() -> SsrfGuard.validate("http://192.168.1.1:3000"))
                .isInstanceOf(SsrfBlockedException.class)
                .hasMessageContaining("内网地址");
    }

    @Test
    void shouldBlockPrivateNetwork172() {
        // 内网 172.16.x.x 应被拦截
        assertThatThrownBy(() -> SsrfGuard.validate("https://172.16.0.1/metrics"))
                .isInstanceOf(SsrfBlockedException.class)
                .hasMessageContaining("内网地址");
    }

    @Test
    void shouldBlockNonHttpProtocol() {
        // file:// 协议应被拦截
        assertThatThrownBy(() -> SsrfGuard.validate("file:///etc/passwd"))
                .isInstanceOf(SsrfBlockedException.class)
                .hasMessageContaining("仅允许 http/https");
    }

    @Test
    void shouldAllowPublicHttpsUrl() {
        // 合法的公网 HTTPS URL 应放行
        assertThatCode(() -> SsrfGuard.validate("https://docs.spring.io/spring-ai/reference"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://0.0.0.0:8080",
                "http://100.64.0.1/api",
                "http://100.127.255.254/api",
            })
    void shouldBlockReservedOrCgnatAddresses(String url) {
        assertThatThrownBy(() -> SsrfGuard.validate(url)).isInstanceOf(SsrfBlockedException.class);
    }
}
