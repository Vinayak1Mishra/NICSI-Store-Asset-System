CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE SCHEMA IF NOT EXISTS store;
CREATE SCHEMA IF NOT EXISTS workflow;
CREATE SCHEMA IF NOT EXISTS audit;

CREATE OR REPLACE FUNCTION store.set_updated_at()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END $$;

CREATE OR REPLACE FUNCTION store.prevent_update_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION '% is immutable; % not allowed', TG_TABLE_NAME, TG_OP; END $$;
