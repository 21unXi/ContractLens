package com.contractlens.service;

import com.contractlens.entity.AnalysisChatMessage;
import com.contractlens.repository.AnalysisChatMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AnalysisChatSessionService {

    private static final int MAX_MESSAGES_PER_CONTRACT = 20;

    private final AnalysisChatMessageRepository analysisChatMessageRepository;

    public AnalysisChatSessionService(AnalysisChatMessageRepository analysisChatMessageRepository) {
        this.analysisChatMessageRepository = analysisChatMessageRepository;
    }

    public void appendUserMessage(Long contractId, String content) {
        appendMessage(contractId, "user", content);
    }

    public void appendAssistantMessage(Long contractId, String content) {
        appendMessage(contractId, "assistant", content);
    }

    public List<AnalysisChatMessage> getHistory(Long contractId) {
        return analysisChatMessageRepository.findTop20ByContractIdOrderByCreatedAtAsc(contractId);
    }

    public int getMessageCount(Long contractId) {
        long count = analysisChatMessageRepository.countByContractId(contractId);
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    public String buildPromptHistory(Long contractId) {
        return getHistory(contractId).stream()
                .map(message -> ("user".equals(message.getRole()) ? "用户" : "助手") + "：" + message.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    @Transactional
    private void appendMessage(Long contractId, String role, String content) {
        AnalysisChatMessage message = new AnalysisChatMessage();
        message.setContractId(contractId);
        message.setRole(role);
        message.setContent(content);
        analysisChatMessageRepository.save(message);

        long count = analysisChatMessageRepository.countByContractId(contractId);
        int overflow = (int) (count - MAX_MESSAGES_PER_CONTRACT);
        if (overflow <= 0) {
            return;
        }
        List<AnalysisChatMessage> toDelete = analysisChatMessageRepository.findByContractIdOrderByCreatedAtAsc(
                contractId,
                PageRequest.of(0, overflow)
        );
        if (!toDelete.isEmpty()) {
            analysisChatMessageRepository.deleteAllInBatch(toDelete);
        }
    }
}
