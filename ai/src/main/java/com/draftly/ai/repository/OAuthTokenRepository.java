package com.draftly.ai.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.draftly.ai.models.OAuthToken;
import com.draftly.ai.models.User;

public interface  OAuthTokenRepository extends JpaRepository<OAuthToken, Long> {
    Optional<OAuthToken> findByUserAndProvider(User user, String provider);
}
