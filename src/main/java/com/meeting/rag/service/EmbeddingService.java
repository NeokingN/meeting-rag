package com.meeting.rag.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Embedding服务 - 文本 → 向量
 *
 * 底层可切换：
 * - Ollama本地 (nomic-embed-text, 免费)
 * - 通义 text-embedding-v3 (云端，效果好)
 * - bge-m3 (开源SOTA)
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /**
     * 单条文本 → 向量
     */
    public float[] embed(String text) {
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
        return response.getResults().get(0).getOutput();
    }

    /**
     * 批量文本 → 向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        EmbeddingResponse response = embeddingModel.embedForResponse(texts);
        return response.getResults().stream()
                .map(r -> r.getOutput())
                .toList();
    }

    /**
     * double[] → float[] (PGVector用float4)
     */
    private float[] toFloatArray(List<Double> doubles) {
        float[] result = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            result[i] = doubles.get(i).floatValue();
        }
        return result;
    }
}
