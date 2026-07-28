package com.meeting.rag.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.time.Duration;

/**
 * 解决 OpenAI 和 Ollama 同时注册 Bean 的冲突。
 * - EmbeddingModel：Ollama 为 @Primary（本地向量化）
 * - ChatModel：OpenAI 为 @Primary（DeepSeek 对话）
 *
 * 同时通过 RestClientCustomizer 使用 JdkClientHttpRequestFactory
 * 替代默认的 SimpleClientHttpRequestFactory（HttpURLConnection），解决
 * "cannot retry due to server authentication, in streaming mode" 问题。
 */
@Configuration
public class AiModelConfig {

    @Bean
    @Primary
    public EmbeddingModel primaryEmbeddingModel(
            @Qualifier("ollamaEmbeddingModel") EmbeddingModel ollamaEmbeddingModel) {
        return ollamaEmbeddingModel;
    }

    @Bean
    @Primary
    public ChatModel primaryChatModel(
            @Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        return openAiChatModel;
    }

    /**
     * Spring AI 自动配置构建 OpenAiApi 时会从容器获取 RestClient.Builder，
     * 此处统一替换底层 HTTP 客户端为 JDK HttpClient（支持带认证头的请求重试），
     * 并放宽读超时以适配大模型长耗时响应。
     */
    @Bean
    public RestClientCustomizer aiRestClientCustomizer() {
        return builder -> {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
            factory.setReadTimeout(Duration.ofSeconds(120));
            builder.requestFactory(factory);
        };
    }
}
