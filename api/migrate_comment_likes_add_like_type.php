<?php
/**
 * Script de migración: Agregar campo like_type a comment_likes
 * 
 * Este script agrega el campo 'like_type' a la tabla 'comment_likes'
 * para implementar likes por comentario (similar a recipe_votes)
 * 
 * like_type: 1 = like, 0 = sin like
 * 
 * INSTRUCCIONES:
 * Ejecutar desde la línea de comandos: php migrate_comment_likes_add_like_type.php
 * O abrir en el navegador: http://localhost/api/migrate_comment_likes_add_like_type.php
 */

require_once 'config/database.php';

try {
    echo "========================================\n";
    echo "Migración: Agregar campo like_type a comment_likes\n";
    echo "========================================\n\n";
    
    $database = new DatabaseConfig();
    $db = $database->getConnection();
    
    // Verificar si la columna ya existe
    $checkSql = "
        SELECT COUNT(*) as count 
        FROM information_schema.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() 
        AND TABLE_NAME = 'comment_likes' 
        AND COLUMN_NAME = 'like_type'
    ";
    
    $stmt = $db->prepare($checkSql);
    $stmt->execute();
    $result = $stmt->fetch(PDO::FETCH_ASSOC);
    
    if ($result && $result['count'] > 0) {
        echo "⚠️  La columna 'like_type' ya existe en la tabla 'comment_likes'\n";
        echo "   No es necesario ejecutar esta migración.\n\n";
    } else {
        echo "Agregando columna 'like_type' a la tabla 'comment_likes'...\n";
        
        // Agregar la columna
        $alterSql = "
            ALTER TABLE comment_likes 
            ADD COLUMN like_type TINYINT NOT NULL DEFAULT 1 
            COMMENT '1 = like, 0 = sin like' 
            AFTER user_id
        ";
        
        $db->exec($alterSql);
        echo "✓ Columna 'like_type' agregada exitosamente\n\n";
        
        // Actualizar los registros existentes para que tengan like_type = 1
        echo "Actualizando registros existentes...\n";
        $updateSql = "UPDATE comment_likes SET like_type = 1 WHERE like_type IS NULL OR like_type = 0";
        $affectedRows = $db->exec($updateSql);
        echo "✓ {$affectedRows} registros actualizados\n\n";
    }
    
    echo "========================================\n";
    echo "✓ Migración completada exitosamente\n";
    echo "========================================\n";
    
} catch (PDOException $e) {
    echo "❌ Error durante la migración: " . $e->getMessage() . "\n";
    echo "Código SQL: " . $e->getCode() . "\n";
    exit(1);
} catch (Exception $e) {
    echo "❌ Error: " . $e->getMessage() . "\n";
    exit(1);
}
?>

