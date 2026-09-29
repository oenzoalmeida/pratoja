-- V7 (H2/testes): troca da UNIQUE global de categories.name por UNIQUE por loja.
-- O H2 gera nomes automáticos (CONSTRAINT_XX) para UNIQUE inline, então a constraint é
-- localizada dinamicamente no dicionário e removida via EXECUTE IMMEDIATE.
EXECUTE IMMEDIATE (SELECT 'ALTER TABLE categories DROP CONSTRAINT "' || CONSTRAINT_NAME || '"' FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'categories' AND CONSTRAINT_TYPE = 'UNIQUE' LIMIT 1);
ALTER TABLE categories ADD CONSTRAINT categories_store_id_name_key UNIQUE (store_id, name);
