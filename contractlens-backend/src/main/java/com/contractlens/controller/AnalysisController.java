package com.contractlens.controller;

import com.contractlens.dto.AnalysisStreamRequest;
import com.contractlens.dto.AnalysisResultPayload;
import com.contractlens.dto.ChatHistoryMessageResponse;
import com.contractlens.entity.AnalysisResult;
import com.contractlens.entity.Contract;
import com.contractlens.repository.AnalysisResultRepository;
import com.contractlens.repository.ContractRepository;
import com.contractlens.service.AnalysisChatSessionService;
import com.contractlens.service.AnalysisService;
import com.contractlens.util.ContentHashUtil;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private AnalysisChatSessionService analysisChatSessionService;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @PostMapping("/contracts/{contractId}")
    public ResponseEntity<AnalysisResult> analyzeContract(@PathVariable Long contractId, Authentication authentication) throws IOException {
        AnalysisResult result = analysisService.analyzeContract(contractId, authentication.getName());
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/contracts/{contractId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalyzeContract(
            @PathVariable Long contractId,
            @Valid @RequestBody(required = false) AnalysisStreamRequest request,
            Authentication authentication
    ) {
        String message = request != null ? request.getMessage() : null;
        return analysisService.streamContractAnalysis(contractId, authentication.getName(), message);
    }

    @GetMapping("/contracts/{contractId}/chat/history")
    public ResponseEntity<List<ChatHistoryMessageResponse>> getChatHistory(@PathVariable Long contractId, Authentication authentication) {
        String username = authentication.getName();
        contractRepository.findByIdAndUserUsername(contractId, username)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        List<ChatHistoryMessageResponse> items = analysisChatSessionService.getHistory(contractId).stream()
                .map(message -> new ChatHistoryMessageResponse(message.getRole(), message.getContent(), message.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(items);
    }

    @GetMapping("/contracts/{contractId}/result")
    public ResponseEntity<AnalysisResultPayload> getAnalysisResult(@PathVariable Long contractId, Authentication authentication) {
        String username = authentication.getName();
        Contract contract = contractRepository.findByIdAndUserUsername(contractId, username)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        if (contract.getContentHash() == null || contract.getContentHash().isBlank()) {
            String computed = ContentHashUtil.sha256HexNormalized(contract.getContent());
            if (computed != null) {
                contract.setContentHash(computed);
                contractRepository.save(contract);
            }
        }

        AnalysisResultPayload payload = null;
        AnalysisResult result = analysisResultRepository.findByContractId(contractId).orElse(null);
        if (result != null) {
            payload = AnalysisResultPayload.from(result);
            String contractHash = contract.getContentHash();
            String resultHash = result.getContractContentHash();
            boolean stale = contractHash == null || contractHash.isBlank() || resultHash == null || resultHash.isBlank() || !contractHash.equals(resultHash);
            payload.setStale(stale);
        }
        return ResponseEntity.ok(payload);
    }
}
