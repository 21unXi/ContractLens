package com.contractlens.service.lightrag;

import com.contractlens.entity.KnowledgeDoc;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class LightRagIngestService {

    private final LightRagProperties properties;

    public LightRagIngestService(LightRagProperties properties) {
        this.properties = properties;
    }

    public LightRagIngestResult rebuildInputs(List<KnowledgeDoc> knowledgeDocs) throws IOException {
        if (!properties.isEnabled()) {
            return new LightRagIngestResult(false, 0, "LightRAG 未启用（contractlens.lightrag.enabled=false）");
        }
        if (!StringUtils.hasText(properties.getInputsDir())) {
            return new LightRagIngestResult(false, 0, "contractlens.lightrag.inputs-dir 未配置");
        }

        Path inputsDir = Paths.get(properties.getInputsDir().trim());
        Files.createDirectories(inputsDir);

        if (properties.isClearInputsOnRebuild()) {
            clearTextFiles(inputsDir);
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

        return new LightRagIngestResult(true, written, null);
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
}

