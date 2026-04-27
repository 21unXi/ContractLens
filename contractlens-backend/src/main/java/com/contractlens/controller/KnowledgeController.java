package com.contractlens.controller;

import com.contractlens.dto.KnowledgeDocSummary;
import com.contractlens.dto.KnowledgeRebuildResponse;
import com.contractlens.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @PostMapping("/rebuild")
    public ResponseEntity<KnowledgeRebuildResponse> rebuildKnowledgeBase() {
        KnowledgeRebuildResponse response = knowledgeService.rebuild();
        if (response != null && !response.ok()) {
            return ResponseEntity.status(500).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/docs")
    public ResponseEntity<Page<KnowledgeDocSummary>> listDocs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        Page<KnowledgeDocSummary> docs = knowledgeService.listKnowledgeDocs(PageRequest.of(page, Math.min(Math.max(size, 1), 100)))
                .map(KnowledgeDocSummary::from);
        return ResponseEntity.ok(docs);
    }
}
