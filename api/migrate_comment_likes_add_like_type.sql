-- ============================================
-- MIGRACIÓN: Agregar campo like_type a comment_likes
-- ============================================
-- Este script agrega el campo 'like_type' a la tabla 'comment_likes'
-- para implementar likes por comentario (similar a recipe_votes)
-- 
-- like_type: 1 = like, 0 = sin like
-- 
-- INSTRUCCIONES:
-- 1. Ejecuta este script en phpMyAdmin o tu cliente MySQL
-- 2. Selecciona la base de datos 'psm' antes de ejecutar
-- ============================================

USE psm;

-- Verificar si la columna ya existe antes de agregarla
SET @column_exists = (
    SELECT COUNT(*) 
    FROM information_schema.COLUMNS 
    WHERE TABLE_SCHEMA = 'psm' 
    AND TABLE_NAME = 'comment_likes' 
    AND COLUMN_NAME = 'like_type'
);

-- Agregar la columna si no existe
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE comment_likes ADD COLUMN like_type TINYINT NOT NULL DEFAULT 1 COMMENT ''1 = like, 0 = sin like'' AFTER user_id',
    'SELECT ''La columna like_type ya existe en comment_likes'' AS Resultado'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Actualizar los registros existentes para que tengan like_type = 1 (como like)
UPDATE comment_likes SET like_type = 1 WHERE like_type IS NULL OR like_type = 0;

-- Mensaje de confirmación
SELECT '✓ Migración completada: Campo like_type agregado a comment_likes' AS Resultado;

