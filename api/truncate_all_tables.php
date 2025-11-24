<?php
/**
 * Script para resetear todas las tablas de MySQL
 * 
 * Este script elimina todos los datos de las tablas manteniendo la estructura
 * Usa DELETE FROM en lugar de TRUNCATE para evitar problemas con foreign keys
 * 
 * INSTRUCCIONES:
 * Ejecutar desde la línea de comandos: php truncate_all_tables.php
 * O abrir en el navegador: http://localhost/api/truncate_all_tables.php
 */

require_once 'config/database.php';

try {
    echo "========================================\n";
    echo "Resetear todas las tablas\n";
    echo "========================================\n\n";
    
    $database = new DatabaseConfig();
    $db = $database->getConnection();
    
    // Deshabilitar verificación de foreign keys temporalmente
    $db->exec("SET FOREIGN_KEY_CHECKS = 0");
    
    // Lista de tablas en orden: primero las dependientes, al final las independientes
    // Orden correcto para respetar foreign keys:
    $tables = [
        'comment_replies',      // Depende de: recipe_comments, users
        'comment_likes',        // Depende de: recipe_comments, users  
        'recipe_comments',       // Depende de: recipes, users
        'recipe_votes',          // Depende de: recipes, users
        'recipe_images',         // Depende de: recipes
        'recipes',               // Depende de: users
        'users'                  // No depende de nada (tabla base)
    ];
    
    $truncatedCount = 0;
    $errors = [];
    
    foreach ($tables as $table) {
        try {
            // Verificar que la tabla existe
            $checkSql = "
                SELECT COUNT(*) as count 
                FROM information_schema.TABLES 
                WHERE TABLE_SCHEMA = DATABASE() 
                AND TABLE_NAME = :table_name
            ";
            
            $checkStmt = $db->prepare($checkSql);
            $checkStmt->execute(['table_name' => $table]);
            $result = $checkStmt->fetch(PDO::FETCH_ASSOC);
            
            if ($result && $result['count'] > 0) {
                echo "Limpiando tabla '$table'...\n";
                // Usar DELETE FROM en lugar de TRUNCATE para evitar problemas con foreign keys
                $db->exec("DELETE FROM `$table`");
                // Reiniciar AUTO_INCREMENT (equivalente a TRUNCATE)
                $db->exec("ALTER TABLE `$table` AUTO_INCREMENT = 1");
                echo "✓ Tabla '$table' limpiada exitosamente\n\n";
                $truncatedCount++;
            } else {
                echo "⚠️  Tabla '$table' no existe, omitiendo...\n\n";
            }
        } catch (PDOException $e) {
            $errorMsg = "Error limpiando tabla '$table': " . $e->getMessage();
            echo "❌ $errorMsg\n\n";
            error_log("❌ $errorMsg");
            $errors[] = $errorMsg;
        }
    }
    
    // Rehabilitar verificación de foreign keys
    $db->exec("SET FOREIGN_KEY_CHECKS = 1");
    
    echo "========================================\n";
    echo "✓ Proceso completado\n";
    echo "✓ $truncatedCount tablas limpiadas\n";
    echo "✓ AUTO_INCREMENT reiniciado en todas las tablas\n";
    if (!empty($errors)) {
        echo "⚠️  Errores encontrados: " . count($errors) . "\n";
    }
    echo "========================================\n";
    
} catch (PDOException $e) {
    echo "❌ Error durante el proceso: " . $e->getMessage() . "\n";
    echo "Código SQL: " . $e->getCode() . "\n";
    exit(1);
} catch (Exception $e) {
    echo "❌ Error: " . $e->getMessage() . "\n";
    exit(1);
}
?>
