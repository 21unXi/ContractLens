package com.contractlens.service.lightrag;

import com.contractlens.entity.KnowledgeDoc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class LightRagIngestService {

    private static final String REBUILD_META_FILENAME = ".contractlens_rebuild.json";
    private final LightRagProperties properties;
    private final ObjectMapper objectMapper;

    public LightRagIngestService(LightRagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public LightRagIngestResult rebuildInputs(List<KnowledgeDoc> knowledgeDocs) throws IOException {
        Instant startedAt = Instant.now();
        if (!properties.isEnabled()) {
            return new LightRagIngestResult(false, null, 0, 0, 0, 0, Instant.now(), "LightRAG 未启用（contractlens.lightrag.enabled=false）");
        }
        if (!StringUtils.hasText(properties.getInputsDir())) {
            return new LightRagIngestResult(false, null, 0, 0, 0, 0, Instant.now(), "contractlens.lightrag.inputs-dir 未配置");
        }

        Path inputsDir = Paths.get(properties.getInputsDir().trim());
        Files.createDirectories(inputsDir);

        if (properties.isClearInputsOnRebuild()) {
            clearTextFiles(inputsDir);
        }

        Set<String> expected = new HashSet<>();
        int skipped = 0;
        for (KnowledgeDoc doc : knowledgeDocs) {
            String docId = doc.getDocId();
            if (!StringUtils.hasText(docId)) {
                skipped++;
                continue;
            }
            expected.add(sanitizeFilename(docId) + ".txt");
        }

        int deleted = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputsDir, "*.txt")) {
            for (Path file : stream) {
                if (!expected.contains(file.getFileName().toString())) {
                    if (Files.deleteIfExists(file)) {
                        deleted++;
                    }
                }
            }
        }

        int written = 0;
        for (KnowledgeDoc doc : knowledgeDocs) {
            String docId = doc.getDocId();
            if (!StringUtils.hasText(docId)) {
                continue;
            }
            String filename = sanitizeFilename(docId) + ".txt";
            Path file = inputsDir.resolve(filename);
            String content = buildFileContent(doc);
            Files.writeString(file, content, StandardCharsets.UTF_8);
            written++;
        }

        Instant finishedAt = Instant.now();
        long durationMs = Duration.between(startedAt, finishedAt).toMillis();
        writeRebuildMetadata(inputsDir, written, deleted, skipped, durationMs, finishedAt);
        return new LightRagIngestResult(true, inputsDir.toString(), written, deleted, skipped, durationMs, finishedAt, null);
    }

    private static void clearTextFiles(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path file : stream) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static String buildFileContent(KnowledgeDoc doc) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(doc.getTitle())) sb.append("标题：").append(doc.getTitle().trim()).append("\n");
        if (StringUtils.hasText(doc.getDocType())) sb.append("类型：").append(doc.getDocType().trim()).append("\n");
        if (StringUtils.hasText(doc.getLawArticle())) sb.append("法律条文：").append(doc.getLawArticle().trim()).append("\n");
        if (StringUtils.hasText(doc.getRiskType())) sb.append("风险类型：").append(doc.getRiskType().trim()).append("\n");
        sb.append("\n");
        if (doc.getContent() != null) sb.append(doc.getContent());
        return sb.toString();
    }

    private static String sanitizeFilename(String value) {
        String trimmed = value.trim();
        return trimmed.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    private void writeRebuildMetadata(Path inputsDir, int written, int deleted, int skipped, long durationMs, Instant finishedAt) throws IOException {
        java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("writtenDocs", written);
        meta.put("deletedDocs", deleted);
        meta.put("skippedDocs", skipped);
        meta.put("durationMs", durationMs);
        meta.put("finishedAt", finishedAt.toString());
        meta.put("inputsDir", inputsDir.toString());
        String json = objectMapper.writeValueAsString(meta);
        Files.writeString(inputsDir.resolve(REBUILD_META_FILENAME), json, StandardCharsets.UTF_8);
    }
}
