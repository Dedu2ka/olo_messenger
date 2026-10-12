ALTER TABLE chat_members
    ADD COLUMN IF NOT EXISTS last_read_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS ix_messages_chat_sender_created_at
    ON messages (chat_id, sender_id, created_at);
