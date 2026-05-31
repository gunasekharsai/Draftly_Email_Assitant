package com.draftly.ai.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.draftly.ai.models.EmailCategory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class EmailClassifierService {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String geminiApiKey;
    private final String geminiModel;

    public EmailClassifierService(
            ObjectMapper objectMapper,
            @Value("${gemini.api-key:}") String geminiApiKey,
            @Value("${gemini.model:gemini-2.5-flash}") String geminiModel
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.geminiApiKey = geminiApiKey;
        this.geminiModel = geminiModel;
    }

    public ClassificationResult classify(String sender, String subject, String body, String labelIds) {
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                return classifyWithGemini(sender, subject, body, labelIds);
            } catch (Exception exception) {
                ClassificationResult fallback = classifyWithRules(sender, subject, body, labelIds);
                return new ClassificationResult(
                        fallback.category(),
                        fallback.needsReply(),
                        fallback.confidence(),
                        fallback.reason() + " Gemini classifier fallback: " + exception.getMessage()
                );
            }
        }

        return classifyWithRules(sender, subject, body, labelIds);
    }

    private ClassificationResult classifyWithGemini(String sender, String subject, String body, String labelIds) {
        String prompt = """
                Classify whether this Gmail message needs a personal reply from the user.

                Use sender, Gmail labels, subject, and body together.

                Categories:
                - NEEDS_REPLY: a person or recruiter/client/friend is asking the user for availability, confirmation, acknowledgement, input, decision, review, update, scheduling, response, or action. This includes a requested follow-up after receiving a parcel, document, payment, or delivery.
                - PROMOTIONAL: offer, sale, ad, marketing, product promotion.
                - NEWSLETTER: digest, newsletter, bulk content, subscription update.
                - OTP_SECURITY: OTP, verification code, login/security alert that does not need a human reply.
                - SPAM: suspicious or irrelevant spam.
                - NO_REPLY_REQUIRED: notification, receipt, automated info, or message that does not ask the user to respond.

                Important:
                - Company/domain emails can still NEEDS_REPLY if they are about interviews, hiring, selection, meetings, orientation, availability, or direct action.
                - If the email asks "please tell me your availability", "can you attend", "are you coming", "are u coming", "please confirm", "please respond", "reply after you receive it", or similar, choose NEEDS_REPLY.
                - Casual questions such as "are u free", "are you free", "free to come", or "wanna join" also require a reply.
                - Do not ignore a mail just because it has no question mark.
                - Return JSON only with: category, needsReply, confidence, reason.

                Sender: %s
                Labels: %s
                Subject: %s
                Body:
                %s
                """.formatted(safe(sender), safe(labelIds), safe(subject), limit(safe(body), 4000)).trim();

        JsonNode root = callGemini(prompt);
        String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
        JsonNode result = parseJsonFromText(text);

        EmailCategory category = EmailCategory.valueOf(result.path("category").asText("NO_REPLY_REQUIRED"));
        boolean needsReply = result.path("needsReply").asBoolean(category == EmailCategory.NEEDS_REPLY);
        double confidence = result.path("confidence").asDouble(0.80);
        String reason = result.path("reason").asText("LLM classification.");

        return new ClassificationResult(category, needsReply, confidence, reason);
    }

    private ClassificationResult classifyWithRules(String sender, String subject, String body, String labelIds) {
        String safeSender = safe(sender);
        String text = (safeSender + " " + safe(subject) + " " + safe(body) + " " + safe(labelIds)).toLowerCase(Locale.ROOT);

        if (text.contains("otp") || text.contains("verification code") || text.contains("security alert")) {
            return new ClassificationResult(EmailCategory.OTP_SECURITY, false, 0.96, "OTP or security email does not need a reply.");
        }

        if (safeSender.toLowerCase(Locale.ROOT).contains("noreply") || safeSender.toLowerCase(Locale.ROOT).contains("no-reply")) {
            return new ClassificationResult(EmailCategory.NO_REPLY_REQUIRED, false, 0.93, "No-reply sender detected.");
        }

        if (text.contains("unsubscribe") || text.contains("newsletter")) {
            return new ClassificationResult(EmailCategory.NEWSLETTER, false, 0.90, "Newsletter or subscription email detected.");
        }

        if (text.contains("offer") || text.contains("discount") || text.contains("sale") || text.contains("limited time") || text.contains("promotion")) {
            return new ClassificationResult(EmailCategory.PROMOTIONAL, false, 0.88, "Promotional language detected.");
        }

        if (containsReplyIntent(text)) {
            return new ClassificationResult(EmailCategory.NEEDS_REPLY, true, 0.88, "The sender is asking for availability, confirmation, input, or a response.");
        }

        return new ClassificationResult(EmailCategory.NO_REPLY_REQUIRED, false, 0.70, "No clear reply request detected.");
    }

    private boolean containsReplyIntent(String text) {
        return text.contains("?")
                || text.contains("please confirm")
                || text.contains("let me know")
                || text.contains("can you")
                || text.contains("could you")
                || text.contains("please tell")
                || text.contains("please respond")
                || text.contains("please response")
                || text.contains("respond me")
                || text.contains("response me")
                || text.contains("reply back")
                || text.contains("respond back")
                || text.contains("response back")
                || text.contains("after you receive")
                || text.contains("after u receive")
                || text.contains("once you receive")
                || text.contains("once u receive")
                || text.contains("after receiving")
                || text.contains("once received")
                || text.contains("acknowledge")
                || text.contains("are you free")
                || text.contains("are u free")
                || text.contains("r u free")
                || text.contains("free to come")
                || text.contains("wanna join")
                || text.contains("want to join")
                || text.contains("are you coming")
                || text.contains("are u coming")
                || text.contains("r u coming")
                || text.contains("you coming")
                || text.contains("u coming")
                || text.contains("coming for")
                || text.contains("coming tomorrow")
                || text.contains("coming tommarow")
                || text.contains("coming tmrw")
                || text.contains("tell me your availability")
                || text.contains("your availability")
                || text.contains("available for")
                || text.contains("attend")
                || text.contains("orientation session")
                || text.contains("meeting")
                || text.contains("interview")
                || text.contains("selected")
                || text.contains("shortlisted")
                || text.contains("need your input")
                || text.contains("review")
                || text.contains("get back");
    }

    private JsonNode callGemini(String prompt) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);
        ObjectNode generationConfig = body.putObject("generationConfig");
        generationConfig.put("temperature", 0.1);
        generationConfig.put("maxOutputTokens", 220);
        generationConfig.put("responseMimeType", "application/json");

        ObjectNode responseSchema = generationConfig.putObject("responseSchema");
        responseSchema.put("type", "OBJECT");
        ObjectNode properties = responseSchema.putObject("properties");
        properties.putObject("category").put("type", "STRING");
        properties.putObject("needsReply").put("type", "BOOLEAN");
        properties.putObject("confidence").put("type", "NUMBER");
        properties.putObject("reason").put("type", "STRING");
        ArrayNode required = responseSchema.putArray("required");
        required.add("category");
        required.add("needsReply");
        required.add("confidence");
        required.add("reason");

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
                throw new IllegalStateException("Gemini classification failed: " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Unable to call Gemini classifier.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini classifier interrupted.", e);
        }
    }

    private JsonNode parseJsonFromText(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```json", "").replaceFirst("^```", "").replaceFirst("```$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (IOException e) {
            throw new IllegalStateException("Gemini did not return valid JSON.", e);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String limit(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record ClassificationResult(
            EmailCategory category,
            boolean needsReply,
            double confidence,
            String reason
    ) {
    }
}
