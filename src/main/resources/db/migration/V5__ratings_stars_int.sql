-- Ratings fix: Hibernate maps Java int to INTEGER, not TINYINT.
ALTER TABLE ratings MODIFY COLUMN stars INT NOT NULL;
