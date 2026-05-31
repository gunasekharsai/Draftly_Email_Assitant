package com.draftly.ai.dto;

import java.time.LocalDateTime;

import com.draftly.ai.models.EmailCategory;

public record EmailMessageDto(
        Long id,
        String gmailMessageId,
        String threadId,
        String labelIds,
        String sender,
        String subject,
        String body,
        EmailCategory category,
        boolean needsReply,
        double confidence,
        String classificationReason,
        LocalDateTime receivedAt
) {
}
