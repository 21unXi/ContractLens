package com.contractlens.dto;

import java.time.LocalDateTime;

public record ChatHistoryMessageResponse(
        String role,
        String content,
        LocalDateTime createdAt
) {
}

