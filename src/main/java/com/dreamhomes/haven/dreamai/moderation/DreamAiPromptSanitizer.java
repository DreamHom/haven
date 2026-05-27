package com.dreamhomes.haven.dreamai.moderation;

import org.springframework.stereotype.Component;

/**
 * Cheap, no-config cleanup applied to every Dream AI prompt BEFORE moderation /
 * persistence / orchestration. Two jobs:
 *
 * <ul>
 *   <li>Strip ASCII C0/C1 control characters (except {@code \t} and {@code \n})
 *       — these have no legitimate use in a natural-language search box.</li>
 *   <li>Strip zero-width and BOM characters (U+200B–U+200D, U+FEFF) — the classic
 *       hidden-instruction smuggling vector for prompt-injection attacks.</li>
 * </ul>
 *
 * <p>This is mutation, not rejection: the cleaned string flows through. Rejection
 * (banned substrings, blank input) lives in {@link DreamAiModerationService}.</p>
 *
 * <p>Returns {@code ""} for null / blank input so callers can rely on a non-null
 * value without extra branching.</p>
 */
@Component
public class DreamAiPromptSanitizer {

    public String sanitize(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        int len = input.length();
        for (int i = 0; i < len; i++) {
            char c = input.charAt(i);
            if (isStrippableControl(c) || isZeroWidthOrBom(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    private static boolean isStrippableControl(char c) {
        // C0 controls (0x00-0x1F) except TAB (0x09) and LF (0x0A); DEL (0x7F); C1 controls (0x80-0x9F).
        if (c == '\t' || c == '\n') {
            return false;
        }
        return c < 0x20 || c == 0x7F || (c >= 0x80 && c <= 0x9F);
    }

    private static boolean isZeroWidthOrBom(char c) {
        return c == '​' || c == '‌' || c == '‍' || c == '﻿';
    }
}
