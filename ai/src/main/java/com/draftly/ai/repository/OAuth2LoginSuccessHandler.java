package com.draftly.ai.repository;

import com.draftly.ai.models.OAuthToken;
import com.draftly.ai.models.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final OAuthTokenRepository tokenRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    public OAuth2LoginSuccessHandler(
            UserRepository userRepository,
            OAuthTokenRepository tokenRepository,
            OAuth2AuthorizedClientService authorizedClientService
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuth2User googleUser = (OAuth2User) authentication.getPrincipal();

        String email = googleUser.getAttribute("email");
        String name = googleUser.getAttribute("name");
        String googleId = googleUser.getAttribute("sub");
        String pictureUrl = googleUser.getAttribute("picture");

        User user = userRepository.findByEmail(email).orElseGet(User::new);

        boolean isNewUser = user.getId() == null;

        user.setEmail(email);
        user.setName(name);
        user.setGoogleId(googleId);
        user.setPictureurl(pictureUrl);
        user.setProvider("google");

        if (isNewUser) {
            user.setCreatedAt(LocalDateTime.now());
        }

        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                "google",
                authentication.getName()
        );

        if (client != null) {
            String accessToken = client.getAccessToken().getTokenValue();
            Instant expiresAt = client.getAccessToken().getExpiresAt();

            String refreshToken = null;
            if (client.getRefreshToken() != null) {
                refreshToken = client.getRefreshToken().getTokenValue();
            }

            OAuthToken token = tokenRepository
                    .findByUserAndProvider(savedUser, "google")
                    .orElseGet(OAuthToken::new);

            token.setUser(savedUser);
            token.setProvider("google");
            token.setAccessToken(accessToken);
            token.setAccessTokenExpiresAt(expiresAt);

            // Very important: Google may return refresh token only first time.
            // Do not overwrite old refresh token with null.
            if (refreshToken != null) {
                token.setRefreshToken(refreshToken);
            }

            if (token.getCreatedAt() == null) {
                token.setCreatedAt(Instant.now());
            }

            token.setUpdatedAt(Instant.now());

            tokenRepository.save(token);
        }

        response.sendRedirect("http://localhost:5173/dashboard");
    }
}