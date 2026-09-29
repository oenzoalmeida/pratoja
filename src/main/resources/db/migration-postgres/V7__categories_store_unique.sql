-- V7 (Postgres): troca da UNIQUE global de categories.name por UNIQUE por loja.
-- O nome gerado pelo Postgres para o UNIQUE inline de V1 é categories_name_key.
ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_name_key;
ALTER TABLE categories ADD CONSTRAINT categories_store_id_name_key UNIQUE (store_id, name);
