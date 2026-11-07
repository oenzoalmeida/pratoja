-- Loja de demonstração "restaurante": identidade terracota da Direção A (editorial),
-- aprovada em imagem. Só a loja demo; demais lojas seguem com suas cores.
UPDATE stores SET brand_primary = '#c24e27', brand_primary_dark = '#a03d1c', updated_at = CURRENT_TIMESTAMP
WHERE slug = 'restaurante';
