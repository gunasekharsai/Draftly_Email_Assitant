package com.draftly.ai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.draftly.ai.models.EmailMessage;
import com.draftly.ai.models.User;

public interface EmailMessageRepository extends JpaRepository<EmailMessage, Long> {
    List<EmailMessage> findByUserOrderByReceivedAtDesc(User user);

    Optional<EmailMessage> findByUserAndGmailMessageId(User user, String gmailMessageId);
}
