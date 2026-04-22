package com.contractlens.config;

import com.contractlens.ai.BatchingEmbeddingModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AiConfig {

    @Bean
    @Primary
    public EmbeddingModel batchingEmbeddingModel(
            @Qualifier("openAiEmbeddingModel") EmbeddingModel delegate,
            @Value("${contractlens.embedding.max-batch-size:10}") int maxBatchSize
    ) {
        return new BatchingEmbeddingModel(delegate, maxBatchSize);
    }

    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        return EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();
    }

    @Bean
    public Retriever<TextSegment> retriever(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        return EmbeddingStoreRetriever.from(
                embeddingStore,
                embeddingModel,
                retrieverTopK,
                retrieverMinScore
        );
    }

    @Value("${contractlens.rag.retriever.top-k:5}")
    private int retrieverTopK;

    @Value("${contractlens.rag.retriever.min-score:0.6}")
    private double retrieverMinScore;
}
