-- V8 (H2/testes): integridade de STORE_ADMIN — no máximo 1 admin de loja por loja.
-- DIFERENÇA EM RELAÇÃO AO POSTGRES: o H2 NÃO suporta índice único parcial
-- (CREATE UNIQUE INDEX ... WHERE role='STORE_ADMIN'), nem triggers equivalentes de forma
-- portátil para esta regra. Portanto, aqui apenas criamos um índice simples de suporte à
-- consulta por (store_id, role) e a REGRA é garantida pela validação em service
-- (StoreService.assertStoreAdminSlotFree, chamada ao criar STORE_ADMIN).
-- No PostgreSQL a mesma regra é reforçada por unique index parcial (V8 em db/migration-postgres).
CREATE INDEX IF NOT EXISTS idx_users_store_role ON users (store_id, role);
