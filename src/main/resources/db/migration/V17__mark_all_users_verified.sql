-- La verificación de identidad se retiró del producto: todos los usuarios
-- quedan habilitados para ofertar y aceptar trabajos. Las columnas se conservan
-- por compatibilidad, pero ya no se usan para bloquear operaciones.
UPDATE users
SET is_verified = 1,
    verification_status = 'VERIFIED';
