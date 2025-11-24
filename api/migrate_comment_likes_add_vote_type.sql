-- MIGRACIÓN: Agregar campo vote_type a comment_likes
-- Este script agrega el campo 'vote_type' a la tabla 'comment_likes'
-- para soportar likes (1) y dislikes (0), similar a recipe_votes

-- Verificar si la columna ya existe antes de agregarla
SET @col_exists = (
    SELECT COUNT(*) 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'comment_likes' 
    AND COLUMN_NAME = 'vote_type'
);

-- Si la columna no existe, agregarla
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE comment_likes ADD COLUMN vote_type TINYINT NOT NULL DEFAULT 1 COMMENT ''1 = me gusta, 0 = no me gusta'' AFTER user_id',
    'SELECT ''La columna vote_type ya existe en comment_likes'' AS Resultado'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Actualizar registros existentes (si los hay) para que tengan vote_type = 1 (like)
UPDATE comment_likes SET vote_type = 1 WHERE vote_type IS NULL OR vote_type = 0;

-- Resultado
SELECT '✓ Migración completada: Campo vote_type agregado a comment_likes' AS Resultado;

