<?php
/**
 * Script para recrear la tabla recipes con la nueva estructura incluyendo el campo description
 */

require_once 'config/database.php';

header('Content-Type: application/json');

try {
    $database = new DatabaseConfig();
    $db = $database->getConnection();
    
    // 1. Hacer backup de datos existentes (opcional)
    echo "1. Haciendo backup de datos existentes...\n";
    $backupSql = "CREATE TABLE recipes_backup AS SELECT * FROM recipes";
    $db->exec($backupSql);
    echo "Backup creado: recipes_backup\n";
    
    // 2. Eliminar la tabla actual
    echo "2. Eliminando tabla recipes actual...\n";
    $db->exec("DROP TABLE IF EXISTS recipes");
    
    // 3. Crear la nueva tabla con la estructura correcta
    echo "3. Creando nueva tabla recipes...\n";
    $createTableSql = "
        CREATE TABLE recipes (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            title VARCHAR(255) NOT NULL,
            description TEXT,
            ingredients TEXT,
            steps TEXT,
            author_id BIGINT NOT NULL,
            author_name VARCHAR(255) NOT NULL,
            tags TEXT,
            cooking_time INT DEFAULT 0,
            servings INT DEFAULT 1,
            rating FLOAT DEFAULT 0.0,
            is_published BOOLEAN DEFAULT FALSE,
            created_at BIGINT NOT NULL,
            updated_at BIGINT NOT NULL,
            FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
            INDEX idx_author_id (author_id),
            INDEX idx_created_at (created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    ";
    
    $db->exec($createTableSql);
    echo "Tabla recipes creada exitosamente\n";
    
    // 4. Migrar datos del backup (sin image_path y agregando description)
    echo "4. Migrando datos del backup...\n";
    $migrateSql = "
        INSERT INTO recipes (
            id, title, description, ingredients, steps, author_id, author_name, 
            tags, cooking_time, servings, rating, is_published, created_at, updated_at
        ) 
        SELECT 
            id, title, CONCAT('Descripción de ', title), ingredients, steps, author_id, author_name, 
            tags, cooking_time, servings, rating, is_published, created_at, updated_at
        FROM recipes_backup
    ";
    
    $db->exec($migrateSql);
    $migratedCount = $db->query("SELECT COUNT(*) FROM recipes")->fetchColumn();
    echo "Migrados $migratedCount registros\n";
    
    // 5. Los campos description ya se asignaron en la migración, no necesitamos actualizar
    
    // 6. Limpiar backup (opcional)
    echo "6. Limpiando backup...\n";
    $db->exec("DROP TABLE recipes_backup");
    
    // Verificar la nueva estructura
    $columnsSql = "SHOW COLUMNS FROM recipes";
    $stmt = $db->prepare($columnsSql);
    $stmt->execute();
    $columns = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    echo json_encode([
        'success' => true,
        'message' => 'Tabla recipes recreada exitosamente con campo description',
        'migrated_records' => $migratedCount,
        'columns' => array_column($columns, 'Field')
    ], JSON_PRETTY_PRINT);
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => 'Error recreando tabla: ' . $e->getMessage()
    ], JSON_PRETTY_PRINT);
}
?>
