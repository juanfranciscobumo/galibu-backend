-- Categories reference table (seeded with the specialties used by the apps).
CREATE TABLE IF NOT EXISTS categories (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  CONSTRAINT uq_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO categories (name) VALUES
  ('Plomería'),
  ('Electricidad'),
  ('Carpintería'),
  ('Pintura'),
  ('Albañilería'),
  ('Jardinería'),
  ('Cerrajería'),
  ('Fumigación'),
  ('Mecánica'),
  ('Soldadura'),
  ('Climatización'),
  ('Limpieza'),
  ('Mudanzas'),
  ('Techados')
  ON DUPLICATE KEY UPDATE name = name;
