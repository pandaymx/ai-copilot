package xyz.ppmblszdp.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 动态检测 compose.yaml 文件绝对路径的 ApplicationContextInitializer。
 * 解决在根目录（如 IDE 调试）与 backend 目录（如 Gradle bootRun）下 CWD 不同导致相对路径无法找到 compose.yaml 的问题。
 */
public class DockerComposeEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        // 如果用户已通过环境变量或系统属性显式指定了 spring.docker.compose.file，则不覆盖
        if (environment.containsProperty("spring.docker.compose.file")) {
            return;
        }

        File composeFile = findComposeFile();
        if (composeFile != null) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("spring.docker.compose.file", composeFile.getAbsolutePath());
            environment.getPropertySources().addFirst(
                    new MapPropertySource("dockerComposeFileAutoDetector", properties)
            );
        }
    }

    private File findComposeFile() {
        File[] candidates = new File[] {
            new File("compose.yaml"),
            new File("../compose.yaml"),
            new File("docker-compose.yml"),
            new File("../docker-compose.yml"),
            new File("backend/compose.yaml")
        };
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                return candidate.getAbsoluteFile();
            }
        }
        return null;
    }
}
