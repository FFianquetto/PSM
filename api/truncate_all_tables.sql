-- ============================================
-- SCRIPT: Resetear todas las tablas (TRUNCATE)
-- ============================================
-- Este script elimina todos los datos de las tablas
-- Respetando el orden de foreign keys
-- 
-- INSTRUCCIONES:
-- 1. Ejecuta este script en phpMyAdmin o tu cliente MySQL
-- 2. Selecciona la base de datos 'psm' antes de ejecutar
-- ============================================

USE psm;

-- Deshabilitar verificación de foreign keys temporalmente
SET FOREIGN_KEY_CHECKS = 0;

-- Eliminar datos de tablas en orden: primero las dependientes, al final las independientes
-- Usamos DELETE FROM en lugar de TRUNCATE porque TRUNCATE tiene problemas con foreign keys
-- Primero las tablas que dependen de otras (hojas del árbol de dependencias)
DELETE FROM comment_replies;      -- Depende de: recipe_comments, users
DELETE FROM comment_likes;        -- Depende de: recipe_comments, users
DELETE FROM recipe_comments;      -- Depende de: recipes, users
DELETE FROM recipe_votes;         -- Depende de: recipes, users
DELETE FROM recipe_images;        -- Depende de: recipes
DELETE FROM recipes;               -- Depende de: users
DELETE FROM users;                 -- No depende de nada (tabla base)

-- Rehabilitar verificación de foreign keys
SET FOREIGN_KEY_CHECKS = 1;

-- Reiniciar AUTO_INCREMENT (equivalente a TRUNCATE)
ALTER TABLE comment_replies AUTO_INCREMENT = 1;
ALTER TABLE comment_likes AUTO_INCREMENT = 1;
ALTER TABLE recipe_comments AUTO_INCREMENT = 1;
ALTER TABLE recipe_votes AUTO_INCREMENT = 1;
ALTER TABLE recipe_images AUTO_INCREMENT = 1;
ALTER TABLE recipes AUTO_INCREMENT = 1;
ALTER TABLE users AUTO_INCREMENT = 1;

-- Mensaje de confirmación
SELECT '✓ Todas las tablas han sido reseteadas (TRUNCATE)' AS Resultado;

