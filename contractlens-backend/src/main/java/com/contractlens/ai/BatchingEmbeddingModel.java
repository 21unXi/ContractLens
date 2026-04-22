package com.contractlens.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BatchingEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;
    private final int maxBatchSize;

    public BatchingEmbeddingModel(EmbeddingModel delegate, int maxBatchSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("maxBatchSize must be > 0");
        }
        this.maxBatchSize = maxBatchSize;
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            return Response.from(List.of());
        }

        List<Embedding> all = new ArrayList<>(textSegments.size());
        TokenUsage tokenUsage = null;
        FinishReason finishReason = null;

        for (int start = 0; start < textSegments.size(); start += maxBatchSize) {
            int end = Math.min(start + maxBatchSize, textSegments.size());
            List<TextSegment> batch = textSegments.subList(start, end);

            Response<List<Embedding>> response = delegate.embedAll(batch);
            List<Embedding> embeddings = response.content();
            if (embeddings != null && !embeddings.isEmpty()) {
                all.addAll(embeddings);
            }

            TokenUsage batchUsage = response.tokenUsage();
            if (batchUsage != null) {
                tokenUsage = tokenUsage == null ? batchUsage : TokenUsage.sum(tokenUsage, batchUsage);
            }

            if (finishReason == null) {
                finishReason = response.finishReason();
            }
        }

        if (tokenUsage == null && finishReason == null) {
            return Response.from(all);
        }

        return Response.from(all, tokenUsage, finishReason);
    }

    @Override
    public int dimension() {
        return delegate.dimension();
    }
}

