package com.contractlens.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
public class AnalysisChatSessionService {

    private static final int MAX_MESSAGES_PER_CONTRACT = 20;

    private final ConcurrentMap<Long, Deque<AnalysisChatMessage>> sessions = new ConcurrentHashMap<>();

    public void appendUserMessage(Long contractId, String content) {
        appendMessage(contractId, "user", content);
    }

    public void appendAssistantMessage(Long contractId, String content) {
        appendMessage(contractId, "assistant", content);
    }

    public List<AnalysisChatMessage> getHistory(Long contractId) {
        Deque<AnalysisChatMessage> messages = sessions.get(contractId);
        if (messages == null) {
            return List.of();
        }
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    public int getMessageCount(Long contractId) {
        return getHistory(contractId).size();
    }

    public String buildPromptHistory(Long contractId) {
        return getHistory(contractId).stream()
                .map(message -> ("user".equals(message.getRole()) ? "用户" : "助手") + "：" + message.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    private void appendMessage(Long contractId, String role, String content) {
        Deque<AnalysisChatMessage> messages = sessions.computeIfAbsent(contractId, key -> new ArrayDeque<>());
        synchronized (messages) {
            messages.addLast(new AnalysisChatMessage(role, content, LocalDateTime.now()));
            while (messages.size() > MAX_MESSAGES_PER_CONTRACT) {
                messages.removeFirst();
            }
        }
    }

    @Data
    @AllArgsConstructor
    public static class AnalysisChatMessage {
        private String role;
        private String content;
        private LocalDateTime createdAt;
    }
}
