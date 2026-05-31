package com.draftly.ai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.draftly.ai.models.ReplyDraft;
import com.draftly.ai.models.User;

public interface ReplyDraftRepository extends JpaRepository<ReplyDraft, Long> {
    List<ReplyDraft> findByUserOrderByUpdatedAtDesc(User user);

    Optional<ReplyDraft> findByUserAndEmailMessageId(User user, Long emailMessageId);

    Optional<ReplyDraft> findByIdAndUser(Long id, User user);
}
