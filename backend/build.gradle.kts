plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "1.1.1"
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
	implementation("org.jsoup:jsoup:1.18.1")
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
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	workingDir = rootDir
}
