package com.contractlens.ai;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BatchingEmbeddingModelTest {

    @Test
    void embedAll_batchesByMaxSize_andAggregatesTokenUsage() {
        RecordingEmbeddingModel delegate = new RecordingEmbeddingModel();
        BatchingEmbeddingModel batching = new BatchingEmbeddingModel(delegate, 10);

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            segments.add(TextSegment.from("t" + i));
        }

        Response<List<Embedding>> response = batching.embedAll(segments);

        assertEquals(List.of(10, 10, 5), delegate.batchSizes);
        assertNotNull(response);
        assertEquals(25, response.content().size());
        assertNotNull(response.tokenUsage());
        assertEquals(3, response.tokenUsage().totalTokenCount());
    }

    static class RecordingEmbeddingModel implements EmbeddingModel {

        final List<Integer> batchSizes = new ArrayList<>();

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            batchSizes.add(textSegments.size());
            List<Embedding> embeddings = new ArrayList<>(textSegments.size());
            for (int i = 0; i < textSegments.size(); i++) {
                embeddings.add(Embedding.from(new float[]{(float) i}));
            }
            return Response.from(embeddings, new TokenUsage(1, 0, 1));
        }
    }
}

