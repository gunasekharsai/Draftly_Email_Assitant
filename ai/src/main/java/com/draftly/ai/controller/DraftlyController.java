package com.draftly.ai.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.draftly.ai.dto.DashboardDto;
import com.draftly.ai.dto.EmailMessageDto;
import com.draftly.ai.dto.ReplyDraftDto;
import com.draftly.ai.dto.UpdateDraftRequest;
import com.draftly.ai.models.User;
import com.draftly.ai.service.CurrentUserService;
import com.draftly.ai.service.DraftlyService;

@RestController
@RequestMapping("/api/draftly")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class DraftlyController {
    private final DraftlyService draftlyService;
    private final CurrentUserService currentUserService;

    public DraftlyController(DraftlyService draftlyService, CurrentUserService currentUserService) {
        this.draftlyService = draftlyService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/dashboard")
    public DashboardDto dashboard(@AuthenticationPrincipal OAuth2User principal) {
        User user = currentUserService.getCurrentUser(principal);
        return draftlyService.getDashboard(user);
    }

    @PostMapping("/inbox/process")
    public DashboardDto processInbox(@AuthenticationPrincipal OAuth2User principal) {
        User user = currentUserService.getCurrentUser(principal);
        return draftlyService.processInbox(user);
    }

    @GetMapping("/emails/{id}")
    public EmailMessageDto getEmail(@AuthenticationPrincipal OAuth2User principal, @PathVariable Long id) {
        User user = currentUserService.getCurrentUser(principal);
        return draftlyService.getEmail(user, id);
    }

    @PatchMapping("/drafts/{id}")
    public ReplyDraftDto updateDraft(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable Long id,
            @RequestBody UpdateDraftRequest request
    ) {
        User user = currentUserService.getCurrentUser(principal);
        return draftlyService.updateDraft(user, id, request.content());
    }

    @PostMapping("/drafts/{id}/approve")
    public ReplyDraftDto approveDraft(@AuthenticationPrincipal OAuth2User principal, @PathVariable Long id) {
        User user = currentUserService.getCurrentUser(principal);
        return draftlyService.approveDraft(user, id);
    }

    @PostMapping("/drafts/{id}/reject")
    public ReplyDraftDto rejectDraft(@AuthenticationPrincipal OAuth2User principal, @PathVariable Long id) {
        User user = currentUserService.getCurrentUser(principal);
        return draftlyService.rejectDraft(user, id);
    }

    @PostMapping("/drafts/{id}/send")
    public ReplyDraftDto sendDraft(@AuthenticationPrincipal OAuth2User principal, @PathVariable Long id) {
        User user = currentUserService.getCurrentUser(principal);
        return draftlyService.sendDraft(user, id);
    }
}
