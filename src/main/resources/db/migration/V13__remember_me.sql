-- V13: "Manter conectado" (remember-me) com tokens persistentes.
-- Tabela padrão do Spring Security (JdbcTokenRepositoryImpl): um token novo a
-- cada acesso (rotação) e invalidação no logout ou na troca de senha.
CREATE TABLE persistent_logins (
    series VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL
);
