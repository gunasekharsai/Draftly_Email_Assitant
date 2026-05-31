package com.draftly.ai.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.draftly.ai.dto.DashboardDto;
import com.draftly.ai.dto.EmailMessageDto;
import com.draftly.ai.dto.EmailSummaryDto;
import com.draftly.ai.dto.ReplyDraftDto;
import com.draftly.ai.models.DraftStatus;
import com.draftly.ai.models.EmailMessage;
import com.draftly.ai.models.ReplyDraft;
import com.draftly.ai.models.User;
import com.draftly.ai.repository.EmailMessageRepository;
import com.draftly.ai.repository.ReplyDraftRepository;

@Service
public class DraftlyService {
    private final GmailService gmailService;
    private final EmailClassifierService classifierService;
    private final AiDraftService aiDraftService;
    private final EmailMessageRepository emailRepository;
    private final ReplyDraftRepository draftRepository;

    public DraftlyService(
            GmailService gmailService,
            EmailClassifierService classifierService,
            AiDraftService aiDraftService,
            EmailMessageRepository emailRepository,
            ReplyDraftRepository draftRepository
    ) {
        this.gmailService = gmailService;
        this.classifierService = classifierService;
        this.aiDraftService = aiDraftService;
        this.emailRepository = emailRepository;
        this.draftRepository = draftRepository;
    }

    @Transactional
    public DashboardDto processInbox(User user) {
        removeOldMockEmails(user);

        for (EmailMessage fetchedEmail : gmailService.fetchRecentEmails(user)) {
            EmailMessage email = emailRepository.findByUserAndGmailMessageId(user, fetchedEmail.getGmailMessageId())
                    .orElse(fetchedEmail);

            email.setThreadId(fetchedEmail.getThreadId());
            email.setLabelIds(fetchedEmail.getLabelIds());
            email.setSender(fetchedEmail.getSender());
            email.setSubject(fetchedEmail.getSubject());
            email.setBody(fetchedEmail.getBody());
            email.setReceivedAt(fetchedEmail.getReceivedAt());

            EmailClassifierService.ClassificationResult result = classifierService.classify(
                    email.getSender(),
                    email.getSubject(),
                    email.getBody(),
                    email.getLabelIds()
            );

            email.setCategory(result.category());
            email.setNeedsReply(result.needsReply());
            email.setConfidence(result.confidence());
            email.setClassificationReason(result.reason());
            if (email.getCreatedAt() == null) {
                email.setCreatedAt(LocalDateTime.now());
            }

            EmailMessage savedEmail = emailRepository.save(email);

            if (savedEmail.isNeedsReply() && draftRepository.findByUserAndEmailMessageId(user, savedEmail.getId()).isEmpty()) {
                ReplyDraft draft = ReplyDraft.builder()
                        .user(user)
                        .emailMessage(savedEmail)
                        .draftContent(aiDraftService.generateDraft(user, savedEmail))
                        .status(DraftStatus.PENDING_REVIEW)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                draftRepository.save(draft);
            }
        }

        return getDashboard(user);
    }

    public DashboardDto getDashboard(User user) {
        List<EmailMessage> emails = emailRepository.findByUserOrderByReceivedAtDesc(user);
        List<ReplyDraft> drafts = draftRepository.findByUserOrderByUpdatedAtDesc(user);

        long needsReply = emails.stream().filter(EmailMessage::isNeedsReply).count();
        long pendingDrafts = drafts.stream().filter(draft -> draft.getStatus() == DraftStatus.PENDING_REVIEW || draft.getStatus() == DraftStatus.EDITED).count();
        long sentDrafts = drafts.stream().filter(draft -> draft.getStatus() == DraftStatus.SENT).count();

        return new DashboardDto(
                emails.size(),
                needsReply,
                emails.size() - needsReply,
                pendingDrafts,
                sentDrafts,
                emails.stream().map(this::toEmailSummaryDto).toList(),
                drafts.stream().map(this::toDraftDto).toList()
        );
    }

    public EmailMessageDto getEmail(User user, Long id) {
        EmailMessage email = emailRepository.findById(id)
                .filter(foundEmail -> foundEmail.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Email not found."));

        return toEmailDto(email);
    }

    @Transactional
    public ReplyDraftDto updateDraft(User user, Long id, String content) {
        ReplyDraft draft = getUserDraft(user, id);
        draft.setDraftContent(content);
        draft.setStatus(DraftStatus.EDITED);
        draft.setUpdatedAt(LocalDateTime.now());
        return toDraftDto(draftRepository.save(draft));
    }

    @Transactional
    public ReplyDraftDto approveDraft(User user, Long id) {
        ReplyDraft draft = getUserDraft(user, id);
        draft.setStatus(DraftStatus.APPROVED);
        draft.setUpdatedAt(LocalDateTime.now());
        return toDraftDto(draftRepository.save(draft));
    }

    @Transactional
    public ReplyDraftDto rejectDraft(User user, Long id) {
        ReplyDraft draft = getUserDraft(user, id);
        draft.setStatus(DraftStatus.REJECTED);
        draft.setUpdatedAt(LocalDateTime.now());
        return toDraftDto(draftRepository.save(draft));
    }

    @Transactional
    public ReplyDraftDto sendDraft(User user, Long id) {
        ReplyDraft draft = getUserDraft(user, id);
        if (draft.getStatus() != DraftStatus.APPROVED) {
            throw new IllegalStateException("Only approved drafts can be sent.");
        }

        gmailService.sendReply(user, draft.getEmailMessage(), draft.getDraftContent());
        draft.setStatus(DraftStatus.SENT);
        draft.setUpdatedAt(LocalDateTime.now());
        return toDraftDto(draftRepository.save(draft));
    }

    private void removeOldMockEmails(User user) {
        List<ReplyDraft> drafts = draftRepository.findByUserOrderByUpdatedAtDesc(user);
        drafts.stream()
                .filter(draft -> draft.getEmailMessage().getGmailMessageId().startsWith("gmail-"))
                .forEach(draftRepository::delete);

        emailRepository.findByUserOrderByReceivedAtDesc(user).stream()
                .filter(email -> email.getGmailMessageId().startsWith("gmail-"))
                .forEach(emailRepository::delete);
    }

    private ReplyDraft getUserDraft(User user, Long id) {
        return draftRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found."));
    }

    private EmailMessageDto toEmailDto(EmailMessage email) {
        return new EmailMessageDto(
                email.getId(),
                email.getGmailMessageId(),
                email.getThreadId(),
                email.getLabelIds(),
                email.getSender(),
                email.getSubject(),
                email.getBody(),
                email.getCategory(),
                email.isNeedsReply(),
                email.getConfidence(),
                email.getClassificationReason(),
                email.getReceivedAt()
        );
    }

    private EmailSummaryDto toEmailSummaryDto(EmailMessage email) {
        return new EmailSummaryDto(
                email.getId(),
                email.getSender(),
                email.getSubject(),
                email.getLabelIds(),
                email.getCategory(),
                email.isNeedsReply(),
                email.getConfidence(),
                email.getClassificationReason(),
                email.getReceivedAt()
        );
    }

    private ReplyDraftDto toDraftDto(ReplyDraft draft) {
        EmailMessage email = draft.getEmailMessage();
        return new ReplyDraftDto(
                draft.getId(),
                email.getId(),
                email.getSender(),
                email.getSubject(),
                email.getBody(),
                email.getCategory(),
                draft.getDraftContent(),
                draft.getStatus(),
                draft.getUpdatedAt()
        );
    }
}
