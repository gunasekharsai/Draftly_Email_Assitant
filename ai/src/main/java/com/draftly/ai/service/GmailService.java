package com.draftly.ai.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.draftly.ai.models.EmailMessage;
import com.draftly.ai.models.OAuthToken;
import com.draftly.ai.models.User;
import com.draftly.ai.repository.OAuthTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GmailService {
    private static final String GMAIL_API_BASE_URL = "https://gmail.googleapis.com/gmail/v1/users/me";
    private static final int MAX_STORED_BODY_LENGTH = 12000;

    private final OAuthTokenRepository tokenRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String clientId;
    private final String clientSecret;

    public GmailService(
            OAuthTokenRepository tokenRepository,
            ObjectMapper objectMapper,
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret}") String clientSecret
    ) {
        this.tokenRepository = tokenRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Transactional
    public List<EmailMessage> fetchRecentEmails(User user) {
        String accessToken = getValidAccessToken(user);
        List<String> messageIds = fetchInboxMessageIds(accessToken);

        List<EmailMessage> emails = new ArrayList<>();
        for (String messageId : messageIds) {
            fetchMessage(accessToken, user, messageId).ifPresent(emails::add);
        }

        return emails;
    }

    @Transactional
    public List<String> fetchRecentSentEmailBodies(User user) {
        String accessToken = getValidAccessToken(user);
        List<String> messageIds = fetchMessageIds(accessToken, "in:sent newer_than:90d", 20);
        List<String> bodies = new ArrayList<>();

        for (String messageId : messageIds) {
            fetchMessageBody(accessToken, messageId)
                    .filter(body -> !body.isBlank())
                    .map(body -> limit(body, 2500))
                    .ifPresent(bodies::add);
        }

        return bodies;
    }

    @Transactional
    public void sendReply(User user, EmailMessage email, String content) {
        String accessToken = getValidAccessToken(user);
        String to = extractEmailAddress(email.getSender());
        String subject = email.getSubject() == null ? "Re:" : email.getSubject();
        if (!subject.toLowerCase().startsWith("re:")) {
            subject = "Re: " + subject;
        }

        String rawMessage = """
                To: %s
                Subject: %s
                Content-Type: text/plain; charset=UTF-8

                %s
                """.formatted(to, subject, content);

        String encodedRawMessage = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawMessage.getBytes(StandardCharsets.UTF_8));

        String json = """
                {
                  "raw": "%s",
                  "threadId": "%s"
                }
                """.formatted(encodedRawMessage, email.getThreadId());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GMAIL_API_BASE_URL + "/messages/send"))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        send(request);
    }

    private List<String> fetchInboxMessageIds(String accessToken) {
        return fetchMessageIds(accessToken, "category:primary newer_than:1d", 20);
    }

    private List<String> fetchMessageIds(String accessToken, String gmailQuery, int maxResults) {
        String query = URLEncoder.encode(gmailQuery, StandardCharsets.UTF_8);
        String url = GMAIL_API_BASE_URL + "/messages?maxResults=" + maxResults + "&q=" + query;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        JsonNode root = sendForJson(request);
        JsonNode messages = root.path("messages");
        List<String> ids = new ArrayList<>();

        if (messages.isArray()) {
            for (JsonNode message : messages) {
                ids.add(message.path("id").asText());
            }
        }

        return ids;
    }

    private Optional<String> fetchMessageBody(String accessToken, String messageId) {
        String url = GMAIL_API_BASE_URL + "/messages/" + messageId + "?format=full";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        JsonNode root = sendForJson(request);
        String body = extractBody(root.path("payload"));
        if (body.isBlank()) {
            body = root.path("snippet").asText("");
        }

        return Optional.of(normalizeBody(body));
    }

    private Optional<EmailMessage> fetchMessage(String accessToken, User user, String messageId) {
        String url = GMAIL_API_BASE_URL + "/messages/" + messageId + "?format=full";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        JsonNode root = sendForJson(request);
        JsonNode payload = root.path("payload");

        String sender = header(payload, "From");
        String subject = header(payload, "Subject");
        String date = header(payload, "Date");
        String body = extractBody(payload);

        String snippet = root.path("snippet").asText("");
        if (body.isBlank()) {
            body = snippet;
        }
        body = normalizeBody(limit(body, MAX_STORED_BODY_LENGTH));

        return Optional.of(EmailMessage.builder()
                .user(user)
                .gmailMessageId(root.path("id").asText(messageId))
                .threadId(root.path("threadId").asText())
                .labelIds(labels(root.path("labelIds")))
                .sender(sender)
                .subject(subject)
                .body(body)
                .receivedAt(parseReceivedAt(date))
                .build());
    }

    private String getValidAccessToken(User user) {
        OAuthToken token = tokenRepository.findByUserAndProvider(user, "google")
                .orElseThrow(() -> new IllegalStateException("Google OAuth token not found. Please login again."));

        Instant expiresAt = token.getAccessTokenExpiresAt();
        if (token.getAccessToken() != null && expiresAt != null && expiresAt.isAfter(Instant.now().plusSeconds(60))) {
            return token.getAccessToken();
        }

        if (token.getRefreshToken() == null || token.getRefreshToken().isBlank()) {
            throw new IllegalStateException("Refresh token missing. Reconnect Google with consent to allow Gmail access.");
        }

        refreshAccessToken(token);
        return token.getAccessToken();
    }

    private void refreshAccessToken(OAuthToken token) {
        String body = "client_id=" + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(token.getRefreshToken())
                + "&grant_type=refresh_token";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        JsonNode root = sendForJson(request);
        token.setAccessToken(root.path("access_token").asText());
        token.setAccessTokenExpiresAt(Instant.now().plusSeconds(root.path("expires_in").asLong(3600)));
        token.setUpdatedAt(Instant.now());
        tokenRepository.save(token);
    }

    private JsonNode sendForJson(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gmail API request failed: " + response.statusCode() + " " + response.body());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to call Gmail API.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gmail API request interrupted.", e);
        }
    }

    private void send(HttpRequest request) {
        sendForJson(request);
    }

    private String header(JsonNode payload, String name) {
        JsonNode headers = payload.path("headers");
        if (headers.isArray()) {
            for (JsonNode header : headers) {
                if (name.equalsIgnoreCase(header.path("name").asText())) {
                    return header.path("value").asText("");
                }
            }
        }
        return "";
    }

    private String labels(JsonNode labelIds) {
        if (!labelIds.isArray()) {
            return "";
        }

        List<String> labels = new ArrayList<>();
        for (JsonNode label : labelIds) {
            labels.add(label.asText());
        }
        return String.join(",", labels);
    }

    private String extractBody(JsonNode payload) {
        String directBody = decodeBody(payload.path("body").path("data").asText(""));
        if (!directBody.isBlank()) {
            return directBody;
        }

        JsonNode parts = payload.path("parts");
        if (parts.isArray()) {
            String plain = findMimeBody(parts, "text/plain");
            if (!plain.isBlank()) {
                return plain;
            }

            String html = findMimeBody(parts, "text/html");
            if (!html.isBlank()) {
                return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
            }
        }

        return "";
    }

    private String findMimeBody(JsonNode parts, String mimeType) {
        for (JsonNode part : parts) {
            if (mimeType.equalsIgnoreCase(part.path("mimeType").asText())) {
                String decoded = decodeBody(part.path("body").path("data").asText(""));
                if (!decoded.isBlank()) {
                    return decoded;
                }
            }

            JsonNode nestedParts = part.path("parts");
            if (nestedParts.isArray()) {
                String nested = findMimeBody(nestedParts, mimeType);
                if (!nested.isBlank()) {
                    return nested;
                }
            }
        }

        return "";
    }

    private String decodeBody(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }

        byte[] decoded = Base64.getUrlDecoder().decode(encoded);
        return new String(decoded, StandardCharsets.UTF_8).trim();
    }

    private String normalizeBody(String body) {
        if (body == null) {
            return "";
        }

        return body
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{4,}", "\n\n\n")
                .trim();
    }

    private LocalDateTime parseReceivedAt(String date) {
        if (date == null || date.isBlank()) {
            return LocalDateTime.now();
        }

        try {
            return ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private String extractEmailAddress(String sender) {
        if (sender == null || sender.isBlank()) {
            return "";
        }

        int start = sender.indexOf('<');
        int end = sender.indexOf('>');
        if (start >= 0 && end > start) {
            return sender.substring(start + 1, end);
        }

        return sender;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength) + "\n\n[Email trimmed for preview]";
    }
}
