plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "1.1.1"
    id("com.diffplug.spotless") version "8.9.0"
}

group = "xyz.ppmblszdp"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "2.0.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.1.0")
    implementation("com.knuddels:jtokkit:1.1.0")
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-deepseek")
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    // MCP Client（消费侧）：自动发现并暴露远程 MCP server 工具为 SyncMcpToolCallbackProvider
    implementation("org.springframework.ai:spring-ai-starter-mcp-client")
    // 记忆子系统：会话记忆(JDBC/pgvector) + 向量长期记忆 + Redis 热缓存/限流
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.ai:spring-ai-vector-store-advisor")
    // RAG ETL Pipeline：多源文档解析与向量化管道
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")
    implementation("org.springframework.ai:spring-ai-tika-document-reader")
    implementation("org.springframework.ai:spring-ai-markdown-document-reader")
    implementation("org.jsoup:jsoup:1.23.1")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.apache.commons:commons-pool2")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // 可观测性：OpenTelemetry tracing（ChatClient / Advisor 链 span 经 OTLP 导出）
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry:opentelemetry-exporter-otlp")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    developmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        // 覆盖 Spring Boot 托管的偏旧 netty 版本，修复 Trivy 报告的 HIGH 漏洞
        // netty 4.2.15.Final -> 4.2.16.Final（CVE-2026-59901/55831/55833/56745/56819/56816）
        mavenBom("io.netty:netty-bom:4.2.16.Final")
    }
    dependencies {
        // postgresql 42.7.11 -> 42.7.12（CVE-2026-54291 SCRAM 降级攻击）
        dependency("org.postgresql:postgresql:42.7.12")
        // jackson-databind 2.x：2.21.4 -> 2.21.5（CVE-2026-54515 / 59889 / GHSA-mhm7-754m-9p8w）
        dependency("com.fasterxml.jackson.core:jackson-databind:2.21.5")
        // jackson-databind 3.x（经 Spring AI 引入）：3.1.4 -> 3.1.5（CVE-2026-59889）
        dependency("tools.jackson.core:jackson-databind:3.1.5")
        // httpclient5 5.6.1 -> 5.6.3（CVE-2026-64607）
        dependency("org.apache.httpcomponents.client5:httpclient5:5.6.3")
        // httpcore5 5.4.2 -> 5.4.3（CVE-2026-54399）
        dependency("org.apache.httpcomponents.core5:httpcore5:5.4.3")
        // httpcore5-h2 5.4.2 -> 5.4.3（CVE-2026-54428）
        dependency("org.apache.httpcomponents.core5:httpcore5-h2:5.4.3")
        // log4j-api 2.25.4 -> 2.25.5（CVE-2026-49844）
        dependency("org.apache.logging.log4j:log4j-api:2.25.5")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootDir
}

// Spotless: enforce consistent Java formatting (AOSP / 4-space style) across the codebase.
// `./gradlew spotlessCheck` is run by the pre-commit hook; `./gradlew spotlessApply` reformats.
spotless {
    java {
        target("src/**/*.java")
        // Palantir Java Format enforces a 4-space indent (AOSP-style), unlike Google's 2-space.
        palantirJavaFormat()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    // Keep formatting consistent for Gradle Kotlin DSL build scripts too.
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
