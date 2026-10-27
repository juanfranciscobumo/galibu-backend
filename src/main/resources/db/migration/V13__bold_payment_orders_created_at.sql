-- Las órdenes creadas con id asignado quedaron con created_at = 0 porque el
-- callback @PrePersist no se reflejaba en el INSERT. updated_at sí es válido,
-- así que se usa como fecha de creación para las filas antiguas.
UPDATE bold_payment_orders
SET created_at = updated_at
WHERE created_at = 0;
