package com.draftly.ai.dto;

import java.time.LocalDateTime;

import com.draftly.ai.models.DraftStatus;
import com.draftly.ai.models.EmailCategory;

public record ReplyDraftDto(
        Long id,
        Long emailId,
        String sender,
        String subject,
        String emailBody,
        EmailCategory category,
        String draftContent,
        DraftStatus status,
        LocalDateTime updatedAt
) {
}
