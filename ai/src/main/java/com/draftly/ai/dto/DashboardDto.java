package com.draftly.ai.dto;

import java.util.List;

public record DashboardDto(
        long totalEmails,
        long needsReply,
        long skipped,
        long pendingDrafts,
        long sentDrafts,
        List<EmailSummaryDto> emails,
        List<ReplyDraftDto> drafts
) {
}
