CREATE INDEX IF NOT EXISTS ix_messages_chat_sent_at_id
    ON messages (chat_id, sent_at DESC, id DESC);
