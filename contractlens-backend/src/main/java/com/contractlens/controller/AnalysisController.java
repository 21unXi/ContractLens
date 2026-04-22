package com.contractlens.controller;

import com.contractlens.dto.AnalysisStreamRequest;
import com.contractlens.entity.AnalysisResult;
import com.contractlens.service.AnalysisService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

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
}
