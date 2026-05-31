package com.draftly.ai.dto;

import java.util.List;

public record WritingStyleProfile(
        String tone,
        String greetingStyle,
        String signOffStyle,
        String replyLength,
        List<String> commonPhrases,
        List<String> doRules,
        List<String> avoidRules,
        int analyzedEmailCount
) {
    public String toPromptBlock() {
        return """
                Tone: %s
                Greeting style: %s
                Sign-off style: %s
                Reply length: %s
                Common phrases: %s
                Do rules: %s
                Avoid rules: %s
                Sent emails analyzed: %d
                """.formatted(
                tone,
                greetingStyle,
                signOffStyle,
                replyLength,
                String.join(", ", commonPhrases),
                String.join(", ", doRules),
                String.join(", ", avoidRules),
                analyzedEmailCount
        ).trim();
    }
}
