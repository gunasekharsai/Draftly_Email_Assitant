package com.draftly.ai.dto;

import java.time.Instant;

public record ApiErrorDto(
        String error,
        String message,
        String path,
        Instant timestamp
) {
}
