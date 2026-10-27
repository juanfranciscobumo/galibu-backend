-- Precio sugerido por hora (lo define el cliente al publicar; se replica en la oferta).
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS suggested_price DOUBLE NULL;
ALTER TABLE bids ADD COLUMN IF NOT EXISTS suggested_price DOUBLE NULL;
