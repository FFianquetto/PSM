<?php
/**
 * Script para migrar datos directamente desde SQLite a MySQL
 */

require_once 'models/UserModel.php';

// Configuración de SQLite
$sqliteFile = '../app/src/main/assets/databases/app_database'; // Ruta relativa al archivo SQLite
$sqliteDb = new PDO("sqlite:$sqliteFile");

try {
    // Verificar si la tabla existe en SQLite
    $stmt = $sqliteDb->query("SELECT name FROM sqlite_master WHERE type='table' AND name='users'");
    $tableExists = $stmt->fetch();
    
    if (!$tableExists) {
        echo "❌ La tabla 'users' no existe en SQLite\n";
        exit(1);
    }
    
    // Obtener todos los usuarios de SQLite
    $stmt = $sqliteDb->query("SELECT * FROM users");
    $sqliteUsers = $stmt->fetchAll(PDO::FETCH_ASSOC);
    
    if (empty($sqliteUsers)) {
        echo "ℹ️ No hay usuarios en SQLite para migrar\n";
        exit(0);
    }
    
    echo "📱 Usuarios encontrados en SQLite: " . count($sqliteUsers) . "\n\n";
    
    // Mostrar los usuarios encontrados
    foreach ($sqliteUsers as $index => $user) {
        echo "Usuario " . ($index + 1) . ":\n";
        echo "  - ID: " . $user['id'] . "\n";
        echo "  - Nombre: " . $user['name'] . " " . $user['lastName'] . "\n";
        echo "  - Email: " . $user['email'] . "\n";
        echo "  - Alias: " . $user['alias'] . "\n";
        echo "  - Teléfono: " . ($user['phone'] ?? 'N/A') . "\n";
        echo "  - Dirección: " . ($user['address'] ?? 'N/A') . "\n";
        echo "  - Creado: " . date('Y-m-d H:i:s', $user['createdAt'] / 1000) . "\n";
        echo "\n";
    }
    
    // Preparar datos para migración
    $migrationData = [];
    foreach ($sqliteUsers as $user) {
        $migrationData[] = [
            'name' => $user['name'],
            'lastName' => $user['lastName'],
            'email' => $user['email'],
            'password' => $user['password'],
            'phone' => $user['phone'] ?? null,
            'address' => $user['address'] ?? null,
            'alias' => $user['alias'],
            'avatarPath' => $user['avatarPath'] ?? null,
            'createdAt' => $user['createdAt'],
            'updatedAt' => $user['updatedAt']
        ];
    }
    
    // Migrar a MySQL
    echo "🔄 Iniciando migración a MySQL...\n";
    
    $userModel = new UserModel();
    $result = $userModel->migrateUsers($migrationData);
    
    echo "\n✅ Migración completada:\n";
    echo "  - Total usuarios: " . $result['total'] . "\n";
    echo "  - Migrados exitosamente: " . $result['migrated'] . "\n";
    echo "  - Errores: " . count($result['errors']) . "\n";
    
    if (!empty($result['errors'])) {
        echo "\n❌ Errores encontrados:\n";
        foreach ($result['errors'] as $error) {
            echo "  - " . $error . "\n";
        }
    }
    
    echo "\n🎉 ¡Migración completada exitosamente!\n";
    
} catch (Exception $e) {
    echo "❌ Error: " . $e->getMessage() . "\n";
    exit(1);
}
?>
