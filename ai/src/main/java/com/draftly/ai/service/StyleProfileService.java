package com.draftly.ai.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.draftly.ai.dto.WritingStyleProfile;
import com.draftly.ai.models.User;

@Service
public class StyleProfileService {
    private final GmailService gmailService;

    public StyleProfileService(GmailService gmailService) {
        this.gmailService = gmailService;
    }

    public WritingStyleProfile buildStyleProfile(User user) {
        List<String> sentEmails = gmailService.fetchRecentSentEmailBodies(user);
        if (sentEmails.isEmpty()) {
            return defaultProfile();
        }

        int totalWords = 0;
        int casualSignals = 0;
        int formalSignals = 0;
        Map<String, Integer> greetings = new LinkedHashMap<>();
        Map<String, Integer> signOffs = new LinkedHashMap<>();
        Map<String, Integer> phrases = new LinkedHashMap<>();

        for (String email : sentEmails) {
            String normalized = email == null ? "" : email.trim();
            String lower = normalized.toLowerCase(Locale.ROOT);
            totalWords += wordCount(normalized);

            countGreeting(lower, greetings);
            countSignOff(lower, signOffs);
            countPhrase(lower, phrases, "thanks");
            countPhrase(lower, phrases, "sure");
            countPhrase(lower, phrases, "will check");
            countPhrase(lower, phrases, "let me know");
            countPhrase(lower, phrases, "please");
            countPhrase(lower, phrases, "sounds good");
            countPhrase(lower, phrases, "get back to you");

            if (lower.contains("hey") || lower.contains("sure") || lower.contains("sounds good")) {
                casualSignals++;
            }
            if (lower.contains("regards") || lower.contains("kindly") || lower.contains("please find")) {
                formalSignals++;
            }
        }

        int averageWords = totalWords / Math.max(sentEmails.size(), 1);
        String tone = formalSignals > casualSignals ? "polite and formal" : "polite, natural, and concise";
        String replyLength = averageWords <= 60 ? "short" : averageWords <= 140 ? "medium" : "detailed";
        String greetingStyle = topValue(greetings, "Hi");
        String signOffStyle = topValue(signOffs, "Thanks");
        List<String> commonPhrases = topValues(phrases, 5);
        if (commonPhrases.isEmpty()) {
            commonPhrases = List.of("Thanks", "Sure", "I will check and get back");
        }

        return new WritingStyleProfile(
                tone,
                greetingStyle,
                signOffStyle,
                replyLength,
                commonPhrases,
                List.of("Use the user's usual greeting and sign-off", "Keep the reply direct", "Answer only what the incoming email asks"),
                List.of("Do not invent facts", "Do not over-explain", "Do not sound like a marketing email"),
                sentEmails.size()
        );
    }

    private WritingStyleProfile defaultProfile() {
        return new WritingStyleProfile(
                "polite and concise",
                "Hi",
                "Thanks",
                "short",
                List.of("Thanks", "Sure", "I will check and get back"),
                List.of("Keep the reply natural", "Be clear and helpful"),
                List.of("Do not invent facts", "Do not write long paragraphs"),
                0
        );
    }

    private int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private void countGreeting(String lower, Map<String, Integer> greetings) {
        if (lower.startsWith("hi ")) {
            increment(greetings, "Hi");
        } else if (lower.startsWith("hey ")) {
            increment(greetings, "Hey");
        } else if (lower.startsWith("hello ")) {
            increment(greetings, "Hello");
        }
    }

    private void countSignOff(String lower, Map<String, Integer> signOffs) {
        if (lower.contains("\nthanks") || lower.endsWith("thanks")) {
            increment(signOffs, "Thanks");
        }
        if (lower.contains("best regards") || lower.contains("\nregards")) {
            increment(signOffs, "Best regards");
        }
        if (lower.contains("\nbest")) {
            increment(signOffs, "Best");
        }
    }

    private void countPhrase(String lower, Map<String, Integer> phrases, String phrase) {
        if (lower.contains(phrase)) {
            increment(phrases, phrase);
        }
    }

    private void increment(Map<String, Integer> map, String key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    private String topValue(Map<String, Integer> map, String fallback) {
        return map.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(fallback);
    }

    private List<String> topValues(Map<String, Integer> map, int limit) {
        List<String> result = new ArrayList<>();
        map.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .forEach(result::add);
        return result;
    }
}
