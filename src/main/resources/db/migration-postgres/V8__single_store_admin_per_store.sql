-- V8 (PostgreSQL): integridade de STORE_ADMIN — no máximo 1 admin de loja por loja.
-- Índice único PARCIAL: só aplica a role='STORE_ADMIN', permitindo N CUSTOMER e
-- N PLATFORM_ADMIN com store_id NULL, e N clientes com a mesma loja (futuro).
-- O H2 (perfil de teste/desenvolvimento) não suporta partial index — ver V8 em db/migration-h2:
-- lá a regra é garantida pela validação em service (StoreService.assertStoreAdminSlotFree).
-- Não destrutivo: o backfill da V6 deixou exatamente 1 STORE_ADMIN (loja 1).
CREATE UNIQUE INDEX IF NOT EXISTS uq_users_store_admin_per_store
    ON users (store_id)
    WHERE role = 'STORE_ADMIN' AND store_id IS NOT NULL;
