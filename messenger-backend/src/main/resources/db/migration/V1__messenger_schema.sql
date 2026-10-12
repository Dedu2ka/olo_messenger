CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL,
    password_hash TEXT NOT NULL,
    display_name VARCHAR(255),
    avatar_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username ON users (username);

CREATE TABLE IF NOT EXISTS chats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type VARCHAR(32) NOT NULL DEFAULT 'DIRECT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_members (
    chat_id UUID NOT NULL,
    user_id UUID NOT NULL,
    CONSTRAINT pk_chat_members PRIMARY KEY (chat_id, user_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_chat_members_chat_user ON chat_members (chat_id, user_id);
CREATE INDEX IF NOT EXISTS ix_chat_members_user_id ON chat_members (user_id);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'chat_members'::regclass AND conname = 'fk_chat_members_chat') THEN
        ALTER TABLE chat_members ADD CONSTRAINT fk_chat_members_chat
            FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'chat_members'::regclass AND conname = 'fk_chat_members_user') THEN
        ALTER TABLE chat_members ADD CONSTRAINT fk_chat_members_user
            FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SENT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_messages_chat_created_at ON messages (chat_id, created_at, id);

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'messages'::regclass AND conname = 'fk_messages_chat') THEN
        ALTER TABLE messages ADD CONSTRAINT fk_messages_chat
            FOREIGN KEY (chat_id) REFERENCES chats (id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = 'messages'::regclass AND conname = 'fk_messages_sender') THEN
        ALTER TABLE messages ADD CONSTRAINT fk_messages_sender
            FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE;
    END IF;
END $$;
