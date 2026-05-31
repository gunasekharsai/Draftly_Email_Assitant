package com.draftly.ai.dto;

import java.time.LocalDateTime;

import com.draftly.ai.models.EmailCategory;

public record EmailSummaryDto(
        Long id,
        String sender,
        String subject,
        String labelIds,
        EmailCategory category,
        boolean needsReply,
        double confidence,
        String classificationReason,
        LocalDateTime receivedAt
) {
}
