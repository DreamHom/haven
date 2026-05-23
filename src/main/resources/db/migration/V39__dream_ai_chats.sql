-- Dream AI: per-user conversation threads and persisted turns (user prompt + assistant listing ids).
-- Chats are owned by the authenticated user; messages cascade-delete with the thread.

CREATE TABLE dream_ai_chats (
    id          BIGSERIAL     PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users (id),
    preview     VARCHAR(200),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX dream_ai_chats_user_updated_idx
    ON dream_ai_chats (user_id, updated_at DESC);

CREATE TABLE dream_ai_chat_messages (
    id              BIGSERIAL    PRIMARY KEY,
    chat_id         BIGINT       NOT NULL REFERENCES dream_ai_chats (id) ON DELETE CASCADE,
    role            VARCHAR(20)  NOT NULL,
    user_prompt     TEXT,
    listing_ids_json TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT dream_ai_chat_messages_role_check
        CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT dream_ai_chat_messages_user_shape CHECK (
        (role = 'USER' AND user_prompt IS NOT NULL AND listing_ids_json IS NULL)
        OR (role = 'ASSISTANT' AND user_prompt IS NULL AND listing_ids_json IS NOT NULL)
    ),
    CONSTRAINT dream_ai_chat_messages_user_prompt_len CHECK (
        role <> 'USER' OR (char_length(user_prompt) BETWEEN 1 AND 500)
    ),
    CONSTRAINT dream_ai_chat_messages_assistant_json_len CHECK (
        role <> 'ASSISTANT' OR (char_length(listing_ids_json) BETWEEN 2 AND 4000)
    )
);

CREATE INDEX dream_ai_chat_messages_chat_created_idx
    ON dream_ai_chat_messages (chat_id, created_at ASC);
