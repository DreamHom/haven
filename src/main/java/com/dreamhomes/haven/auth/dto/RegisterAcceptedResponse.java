package com.dreamhomes.haven.auth.dto;

/**
 * 202 Accepted body returned from {@code POST /api/auth/register}. Sent unconditionally —
 * the same body lands whether the email was newly registered or already taken. Persona
 * audit (Temi, Amaka) flagged the previous "no body, no clue what to do next" response
 * as the single most disorienting moment in onboarding. The body now spells out the
 * next step explicitly without leaking which branch the request took.
 */
public record RegisterAcceptedResponse(
        String status,
        String message,
        String nextStep
) {
    /** Single canonical instance — the body is identical for every caller. */
    public static final RegisterAcceptedResponse DEFAULT = new RegisterAcceptedResponse(
            "ACCEPTED",
            "If your email is new, your account is being created. If it was already registered, "
                    + "no change was made. The response is identical in both cases — that is the "
                    + "anti-enumeration contract.",
            "POST /api/auth/login with your email + password");
}
