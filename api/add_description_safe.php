<?php
/**
 * Script seguro para agregar solo el campo description sin perder datos
 */

require_once 'config/database.php';

header('Content-Type: application/json');

try {
    $database = new DatabaseConfig();
    $db = $database->getConnection();
    
    // Verificar si la columna ya existe
    $checkSql = "SHOW COLUMNS FROM recipes LIKE 'description'";
    $stmt = $db->prepare($checkSql);
    $stmt->execute();
    $columnExists = $stmt->fetch();
    
    if ($columnExists) {
        echo json_encode([
            'success' => true,
            'message' => 'La columna description ya existe en la tabla recipes'
        ], JSON_PRETTY_PRINT);
    } else {
        // Agregar la columna description después de title
        echo "Agregando columna description...\n";
        $alterSql = "ALTER TABLE recipes ADD COLUMN description TEXT AFTER title";
        $db->exec($alterSql);
        
        // Actualizar registros existentes con descripción por defecto
        echo "Actualizando registros existentes...\n";
        $updateSql = "UPDATE recipes SET description = CONCAT('Descripción de ', title) WHERE description IS NULL OR description = ''";
        $updatedRows = $db->exec($updateSql);
        
        // Verificar la estructura final
        $columnsSql = "SHOW COLUMNS FROM recipes";
        $stmt = $db->prepare($columnsSql);
        $stmt->execute();
        $columns = $stmt->fetchAll(PDO::FETCH_ASSOC);
        
        echo json_encode([
            'success' => true,
            'message' => 'Columna description agregada exitosamente',
            'updated_records' => $updatedRows,
            'columns' => array_column($columns, 'Field')
        ], JSON_PRETTY_PRINT);
    }
    
} catch (PDOException $e) {
    http_response_code(500);
    echo json_encode([
        'success' => false,
        'error' => 'Error al actualizar tabla: ' . $e->getMessage()
    ], JSON_PRETTY_PRINT);
}
?>
