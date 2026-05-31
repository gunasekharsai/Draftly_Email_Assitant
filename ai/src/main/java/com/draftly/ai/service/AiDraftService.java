package com.draftly.ai.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.draftly.ai.dto.WritingStyleProfile;
import com.draftly.ai.models.EmailMessage;
import com.draftly.ai.models.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AiDraftService {
    private final StyleProfileService styleProfileService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String geminiApiKey;
    private final String geminiModel;

    public AiDraftService(
            StyleProfileService styleProfileService,
            ObjectMapper objectMapper,
            @Value("${gemini.api-key:}") String geminiApiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String geminiModel
    ) {
        this.styleProfileService = styleProfileService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
    }

    public String generateDraft(User user, EmailMessage email) {
        WritingStyleProfile styleProfile = styleProfileService.buildStyleProfile(user);

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is missing. Start the backend with GEMINI_API_KEY configured.");
        }

        String prompt = buildPrompt(email, styleProfile);
        return callGemini(prompt);
    }

    private String buildPrompt(EmailMessage email, WritingStyleProfile styleProfile) {
        return """
                You are Draftly, an email reply assistant.

                Write a reply to the incoming email.
                Treat the subject and body together as the complete message context.
                Match the user's usual email writing style using the style profile.
                Adapt the wording to the incoming email's formality level:
                - If the sender writes informally or casually, reply naturally and informally.
                - If the sender writes formally or professionally, reply formally and professionally.
                - Keep the user's own tone, greeting habits, sign-off habits, and usual reply length as the personal baseline.

                Strict rules:
                - Output only the email reply body.
                - Do not include explanation, markdown, labels, or subject line.
                - Do not invent facts or promise completed work.
                - If the incoming email asks for a personal decision that is not known, do not imply acceptance or rejection. Write a useful temporary response such as "Let me check and confirm shortly."
                - Answer the sender's actual request directly. Avoid generic replies that only say thanks.
                - Use details from both the subject and body when writing the reply.
                - Identify the main topic, event, product, document, meeting, company, or named entity in the subject and body. Mention that specific detail naturally in the reply when it is relevant.
                - Do not replace a specific named detail with a vague phrase. For example, if the incoming mail mentions "AR Rehman Concert", write "AR Rehman concert" instead of only saying "the invite" or "the event".
                - Do not use a formal greeting or sign-off for a very casual message unless the user's style profile strongly requires it.
                - Do not make a professional message overly casual even if the user often uses short replies.
                - Generate a complete email reply with 2 to 4 meaningful sentences by default.
                - Do not return a one-line response unless the incoming email is extremely simple and a one-line reply is genuinely sufficient.
                - Include a greeting, a direct response to the sender's request, and an appropriate closing.
                - Keep the reply concise but not abrupt. Add only relevant context from the subject and body.
                - Use the user's usual reply length as a guide, but prefer a complete response over an overly short draft.
                - Use the user's usual greeting and sign-off.
                - Do not automatically begin with "Thanks", "Thank you", or "Thanks for reaching out."
                - Vary the opening naturally based on the email purpose. Prefer a direct opening when suitable.
                - For meeting or availability requests, start with a confirmation-oriented line such as "I can confirm..." or "Let me check my schedule..."
                - For follow-ups, start with an update-oriented line such as "I am reviewing..." or "I will share an update..."
                - For delivery or acknowledgement requests, start with an acknowledgement-oriented line such as "Sure, I will let you know..." or "Noted..."
                - For informal invitations, use a casual opening such as "Hey, sounds good..." or "Let me confirm once..."
                - Use gratitude only when it adds value, such as after receiving help, an attachment, an invitation, or useful information. Do not force it into every reply.
                - Do not mention AI or automation.
                - Return a complete, send-ready reply. Never end with an incomplete sentence or an orphan word such as "Please."

                User style profile:
                %s

                Incoming email:
                Sender: %s
                Subject: %s
                Body:
                %s
                """.formatted(
                styleProfile.toPromptBlock(),
                safe(email.getSender()),
                safe(email.getSubject()),
                safe(email.getBody())
        ).trim();
    }

    private String callGemini(String prompt) {
        String draft = requestGemini(prompt);
        if (isLowQualityDraft(draft)) {
            draft = requestGemini(prompt + """

                    Your previous draft was incomplete or too generic.
                    Rewrite it as a complete, send-ready reply.
                    Directly respond to the sender's request.
                    If the user's decision is unknown, clearly say that the user will check and confirm shortly.
                    Do not end with an orphan word such as "Please."
                    """);
        }

        if (isLowQualityDraft(draft)) {
            throw new IllegalStateException("Gemini returned an incomplete draft. Please regenerate or edit the reply manually.");
        }

        return draft;
    }

    private String requestGemini(String prompt) {
        ObjectNode body = objectMapper.createObjectNode();

        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("temperature", 0.35);
        generationConfig.put("maxOutputTokens", 350);

        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel
                + ":generateContent?key="
                + URLEncoder.encode(geminiApiKey, StandardCharsets.UTF_8);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Gemini request failed: " + response.statusCode() + " " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String generatedText = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");

            if (generatedText.isBlank()) {
                throw new IllegalStateException("Gemini returned an empty draft.");
            }

            return generatedText.trim();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to call Gemini.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request interrupted.", e);
        }
    }

    private boolean isLowQualityDraft(String draft) {
        if (draft == null || draft.isBlank()) {
            return true;
        }

        String normalized = draft.trim();
        String lower = normalized.toLowerCase();
        int wordCount = normalized.split("\\s+").length;

        return wordCount < 8
                || lower.endsWith("\nplease")
                || lower.endsWith("\nplease.")
                || lower.matches("(?s).*\\n\\s*please\\.?\\s*$");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
