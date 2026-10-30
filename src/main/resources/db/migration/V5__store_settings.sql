-- V5: configurações da loja (singleton id=1). Apenas DDL aditivo + INSERT idempotente da linha única.
CREATE TABLE store_settings (
    id BIGINT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slogan VARCHAR(200),
    description VARCHAR(600),
    phone VARCHAR(30),
    whatsapp VARCHAR(30),
    email VARCHAR(120),
    address_street VARCHAR(150),
    address_number VARCHAR(20),
    address_complement VARCHAR(100),
    address_neighborhood VARCHAR(100),
    address_city VARCHAR(100),
    address_state VARCHAR(2),
    address_zip VARCHAR(20),
    opening_hours TEXT,
    hero_title VARCHAR(200),
    hero_subtitle VARCHAR(400),
    delivery_time_note VARCHAR(120),
    delivery_fee NUMERIC(10,2) NOT NULL,
    brand_primary VARCHAR(7),
    brand_primary_dark VARCHAR(7),
    logo_path VARCHAR(300),
    updated_at TIMESTAMP,
    CONSTRAINT store_settings_singleton CHECK (id = 1)
);

INSERT INTO store_settings (
    id, name, slogan, description, phone, whatsapp, email,
    address_street, address_number, address_complement, address_neighborhood, address_city, address_state, address_zip,
    opening_hours, hero_title, hero_subtitle, delivery_time_note, delivery_fee,
    brand_primary, brand_primary_dark, logo_path, updated_at
) VALUES (
    1,
    'Restaurante',
    'Seu cardápio digital',
    'Configure o nome, o contato e a identidade do seu restaurante em Administração > Configurações da loja.',
    NULL, NULL, NULL,
    NULL, NULL, NULL, NULL, NULL, NULL, NULL,
    NULL,
    'Seu cardápio, do seu jeito',
    'Monte o pedido em poucos passos, personalize seu prato e acompanhe a entrega até a sua porta.',
    'Entrega em 35–50 min',
    6.00,
    '#ef5b2a', '#d94717',
    NULL,
    CURRENT_TIMESTAMP
);
