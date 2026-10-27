-- Eliminar la cuenta debe borrar por completo los datos del usuario.
-- wallet_transactions pasa a borrarse en cascada igual que devices.
-- Los trabajos (jobs) los borra UserService.deleteAccount antes de la cuenta,
-- y con ellos caen en cascada las ofertas (bids) y las calificaciones (ratings).
ALTER TABLE wallet_transactions DROP FOREIGN KEY fk_wallet_user;

ALTER TABLE wallet_transactions
  ADD CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
