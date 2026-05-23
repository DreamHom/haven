-- Dream AI: flexible per-message JSON envelope, idempotent client keys, SYSTEM/TOOL roles.
-- MVP stores assistant turns as structured JSON (kind, blocks, meta); legacy prompt/ids migrated.

ALTER TABLE dream_ai_chat_messages DROP CONSTRAINT IF EXISTS dream_ai_chat_messages_user_shape;
ALTER TABLE dream_ai_chat_messages DROP CONSTRAINT IF EXISTS dream_ai_chat_messages_user_prompt_len;
ALTER TABLE dream_ai_chat_messages DROP CONSTRAINT IF EXISTS dream_ai_chat_messages_assistant_json_len;
ALTER TABLE dream_ai_chat_messages DROP CONSTRAINT IF EXISTS dream_ai_chat_messages_role_check;

ALTER TABLE dream_ai_chat_messages
    ADD COLUMN client_message_id VARCHAR(64),
    ADD COLUMN content JSONB;

UPDATE dream_ai_chat_messages
SET content = jsonb_build_object(
        'schemaVersion', 1,
        'role', role,
        'userText', user_prompt
    )
WHERE role = 'USER';

UPDATE dream_ai_chat_messages
SET content = jsonb_build_object(
        'schemaVersion', 1,
        'role', role,
        'turn', jsonb_build_object(
                'kind', 'reply',
                'markdown', null,
                'blocks', jsonb_build_array(
                        jsonb_build_object(
                                'type', 'listings',
                                'listingIds', listing_ids_json::jsonb
                            )
                    ),
                'meta', jsonb_build_object('migratedFrom', 'v39-listing-ids')
            )
    )
WHERE role = 'ASSISTANT';

ALTER TABLE dream_ai_chat_messages
    ALTER COLUMN content SET NOT NULL,
    DROP COLUMN user_prompt,
    DROP COLUMN listing_ids_json;

ALTER TABLE dream_ai_chat_messages
    ADD CONSTRAINT dream_ai_chat_messages_role_check
        CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    ADD CONSTRAINT dream_ai_chat_messages_content_object_check
        CHECK (jsonb_typeof(content) = 'object' AND content ? 'schemaVersion');

CREATE UNIQUE INDEX dream_ai_chat_messages_client_per_chat_idx
    ON dream_ai_chat_messages (chat_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
