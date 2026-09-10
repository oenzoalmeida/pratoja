ALTER TABLE order_item_options ADD COLUMN option_id BIGINT REFERENCES product_options(id);
