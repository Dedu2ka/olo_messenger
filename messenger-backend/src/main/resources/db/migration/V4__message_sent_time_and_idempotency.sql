ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ;

UPDATE messages
SET sent_at = created_at
WHERE sent_at IS NULL;

ALTER TABLE messages
    ALTER COLUMN sent_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN sent_at SET NOT NULL;

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS client_message_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS ux_messages_client_message_id
    ON messages (client_message_id)
    WHERE client_message_id IS NOT NULL;
