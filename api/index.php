<?php
/**
 * Controlador principal de la API REST
 */

require_once 'models/UserModel.php';
require_once 'models/RecipeModel.php';

class ApiController {
    private $userModel;
    private $recipeModel;
    
    public function __construct() {
        $this->userModel = new UserModel();
        $this->recipeModel = new RecipeModel();
    }
    
    /**
     * Manejar las peticiones HTTP
     */
    public function handleRequest() {
        $method = $_SERVER['REQUEST_METHOD'];
        $path = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);
        
        // Configurar headers CORS
        header('Access-Control-Allow-Origin: *');
        header('Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS');
        header('Access-Control-Allow-Headers: Content-Type, Authorization');
        header('Content-Type: application/json');
        
        // Manejar preflight requests
        if ($method === 'OPTIONS') {
            http_response_code(200);
            exit();
        }
        
        try {
            switch ($method) {
                case 'GET':
                    $this->handleGet($path);
                    break;
                case 'POST':
                    $this->handlePost($path);
                    break;
                case 'PUT':
                    $this->handlePut($path);
                    break;
                default:
                    $this->sendResponse(['error' => 'Método no permitido'], 405);
            }
        } catch (Exception $e) {
            $this->sendResponse(['error' => $e->getMessage()], 500);
        }
    }
    
    /**
     * Manejar peticiones GET
     */
    private function handleGet($path) {
        switch ($path) {
            case '/api/users':
                $users = $this->userModel->getAllUsers();
                $this->sendResponse(['users' => $users]);
                break;
                
            case '/api/health':
                $this->sendResponse(['status' => 'OK', 'message' => 'API funcionando']);
                break;
                
            default:
                $this->sendResponse(['error' => 'Endpoint no encontrado'], 404);
        }
    }
    
    /**
     * Manejar peticiones PUT
     */
    private function handlePut($path) {
        $data = json_decode(file_get_contents('php://input'), true);
        
        if (json_last_error() !== JSON_ERROR_NONE) {
            $this->sendResponse(['error' => 'JSON inválido'], 400);
            return;
        }
        
        // Extraer el ID del path (ej: /api/users/123) o email (ej: /api/users/by-email)
        if (preg_match('/^\/api\/users\/(\d+)$/', $path, $matches)) {
            $userId = (int)$matches[1];
            $this->updateUser($userId, $data);
        } elseif ($path === '/api/users/by-email') {
            $this->updateUserByEmail($data);
        } else {
            $this->sendResponse(['error' => 'Endpoint no encontrado'], 404);
        }
    }
    
    /**
     * Manejar peticiones POST
     */
    private function handlePost($path) {
        $data = json_decode(file_get_contents('php://input'), true);
        
        if (json_last_error() !== JSON_ERROR_NONE) {
            $this->sendResponse(['error' => 'JSON inválido'], 400);
            return;
        }
        
        switch ($path) {
            case '/api/migrate/users':
                $this->migrateUsers($data);
                break;
                
            case '/api/migrate/recipes':
                $this->migrateRecipes($data);
                break;
                
            case '/api/migrate/recipe_images':
                $this->migrateRecipeImages($data);
                break;
                
            case '/api/users':
                $this->createUser($data);
                break;
                
            case '/api/login':
                $this->loginUser($data);
                break;
                
            case '/api/setup':
                $this->setupDatabase();
                break;
                
            case '/api/update_tables':
                $this->updateTables();
                break;
                
            default:
                $this->sendResponse(['error' => 'Endpoint no encontrado'], 404);
        }
    }
    
    /**
     * Migrar usuarios desde SQLite
     */
    private function migrateUsers($data) {
        if (!isset($data['users']) || !is_array($data['users'])) {
            $this->sendResponse(['error' => 'Datos de usuarios requeridos'], 400);
            return;
        }
        
        $result = $this->userModel->migrateUsers($data['users']);
        
        $this->sendResponse([
            'message' => 'Migración completada',
            'migrated' => $result['migrated'],
            'total' => $result['total'],
            'errors' => $result['errors']
        ]);
    }
    
    /**
     * Migrar recetas desde SQLite
     */
    private function migrateRecipes($data) {
        if (!isset($data['recipes']) || !is_array($data['recipes'])) {
            $this->sendResponse(['error' => 'Datos de recetas requeridos'], 400);
            return;
        }
        
        $result = $this->recipeModel->migrateRecipes($data['recipes']);
        
        $this->sendResponse([
            'message' => 'Migración de recetas completada',
            'migrated' => $result['migrated'],
            'total' => $result['total'],
            'errors' => $result['errors']
        ]);
    }
    
    /**
     * Migrar imágenes de recetas desde SQLite
     */
    private function migrateRecipeImages($data) {
        if (!isset($data['images']) || !is_array($data['images'])) {
            $this->sendResponse(['error' => 'Datos de imágenes requeridos'], 400);
            return;
        }
        
        $result = $this->recipeModel->migrateRecipeImages($data['images']);
        
        $this->sendResponse([
            'message' => 'Migración de imágenes completada',
            'migrated' => $result['migrated'],
            'total' => $result['total'],
            'errors' => $result['errors']
        ]);
    }
    
    /**
     * Actualizar un usuario existente por email
     */
    private function updateUserByEmail($data) {
        $requiredFields = ['email', 'name', 'lastName', 'password', 'alias'];
        
        foreach ($requiredFields as $field) {
            if (!isset($data[$field])) {
                $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                return;
            }
        }
        
        $email = $data['email'];
        
        // Verificar si el usuario existe por email
        $existingUser = $this->userModel->getUserByEmail($email);
        if (!$existingUser) {
            $this->sendResponse(['error' => 'Usuario no encontrado'], 404);
            return;
        }
        
        // Verificar si el alias ya existe en otros usuarios
        if ($data['alias'] !== $existingUser['alias'] && $this->userModel->aliasExists($data['alias'])) {
            $this->sendResponse(['error' => 'El alias ya existe'], 409);
            return;
        }
        
        $userData = [
            'name' => $data['name'],
            'last_name' => $data['lastName'],
            'email' => $data['email'],
            'password' => $data['password'],
            'phone' => $data['phone'] ?? null,
            'address' => $data['address'] ?? null,
            'alias' => $data['alias'],
            'avatar_path' => $data['avatarPath'] ?? null,
            'updated_at' => time() * 1000 // Convertir a milisegundos
        ];
        
        $success = $this->userModel->updateUserByEmail($email, $userData);
        
        if ($success) {
            $this->sendResponse([
                'data' => [
                    'message' => 'Usuario actualizado exitosamente',
                    'userId' => $existingUser['id']
                ]
            ]);
        } else {
            $this->sendResponse(['error' => 'Error actualizando usuario'], 500);
        }
    }
    
    /**
     * Actualizar un usuario existente
     */
    private function updateUser($userId, $data) {
        $requiredFields = ['name', 'lastName', 'email', 'password', 'alias'];
        
        foreach ($requiredFields as $field) {
            if (!isset($data[$field])) {
                $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                return;
            }
        }
        
        // Verificar si el usuario existe
        $existingUser = $this->userModel->getUserById($userId);
        if (!$existingUser) {
            $this->sendResponse(['error' => 'Usuario no encontrado'], 404);
            return;
        }
        
        // Verificar si el email o alias ya existen en otros usuarios
        if ($data['email'] !== $existingUser['email'] && $this->userModel->emailExists($data['email'])) {
            $this->sendResponse(['error' => 'El email ya existe'], 409);
            return;
        }
        
        if ($data['alias'] !== $existingUser['alias'] && $this->userModel->aliasExists($data['alias'])) {
            $this->sendResponse(['error' => 'El alias ya existe'], 409);
            return;
        }
        
        $userData = [
            'name' => $data['name'],
            'last_name' => $data['lastName'],
            'email' => $data['email'],
            'password' => $data['password'],
            'phone' => $data['phone'] ?? null,
            'address' => $data['address'] ?? null,
            'alias' => $data['alias'],
            'avatar_path' => $data['avatarPath'] ?? null,
            'updated_at' => time() * 1000 // Convertir a milisegundos
        ];
        
        $success = $this->userModel->updateUser($userId, $userData);
        
        if ($success) {
            $this->sendResponse([
                'message' => 'Usuario actualizado exitosamente',
                'userId' => $userId
            ]);
        } else {
            $this->sendResponse(['error' => 'Error actualizando usuario'], 500);
        }
    }
    
    /**
     * Crear un usuario individual
     */
    private function createUser($data) {
        $requiredFields = ['name', 'lastName', 'email', 'password', 'alias'];
        
        foreach ($requiredFields as $field) {
            if (!isset($data[$field])) {
                $this->sendResponse(['error' => "Campo requerido: $field"], 400);
                return;
            }
        }
        
        // Verificar si el email o alias ya existen
        if ($this->userModel->emailExists($data['email'])) {
            $this->sendResponse(['error' => 'El email ya existe'], 409);
            return;
        }
        
        if ($this->userModel->aliasExists($data['alias'])) {
            $this->sendResponse(['error' => 'El alias ya existe'], 409);
            return;
        }
        
        $userData = [
            'name' => $data['name'],
            'last_name' => $data['lastName'],
            'email' => $data['email'],
            'password' => $data['password'],
            'phone' => $data['phone'] ?? null,
            'address' => $data['address'] ?? null,
            'alias' => $data['alias'],
            'avatar_path' => $data['avatarPath'] ?? null,
            'created_at' => time() * 1000, // Convertir a milisegundos
            'updated_at' => time() * 1000
        ];
        
        $userId = $this->userModel->insertUser($userData);
        
        if ($userId) {
            $this->sendResponse([
                'data' => [
                    'message' => 'Usuario creado exitosamente',
                    'userId' => $userId
                ]
            ], 201);
        } else {
            $this->sendResponse(['error' => 'Error creando usuario'], 500);
        }
    }
    
    /**
     * Autenticar usuario (login)
     */
    private function loginUser($data) {
        if (!isset($data['email']) || !isset($data['password'])) {
            $this->sendResponse(['error' => 'Email y contraseña requeridos'], 400);
            return;
        }
        
        $email = trim(strtolower($data['email']));
        $password = $data['password'];
        
        // Obtener usuario por email
        $user = $this->userModel->getUserByEmail($email);
        
        if (!$user) {
            $this->sendResponse(['error' => 'Credenciales incorrectas'], 401);
            return;
        }
        
        // Verificar contraseña (en producción deberías usar hash)
        if ($user['password'] !== $password) {
            $this->sendResponse(['error' => 'Credenciales incorrectas'], 401);
            return;
        }
        
        // Login exitoso
        $this->sendResponse([
            'data' => [
                'message' => 'Login exitoso',
                'user' => [
                    'id' => $user['id'],
                    'name' => $user['name'],
                    'lastName' => $user['last_name'],
                    'email' => $user['email'],
                    'password' => $user['password'],
                    'phone' => $user['phone'],
                    'address' => $user['address'],
                    'alias' => $user['alias'],
                    'avatarPath' => $user['avatar_path'],
                    'createdAt' => $user['created_at'],
                    'updatedAt' => $user['updated_at']
                ]
            ]
        ]);
    }
    
    /**
     * Configurar la base de datos
     */
    private function setupDatabase() {
        try {
            $this->userModel->createTable();
            $this->recipeModel->createTables();
            $this->sendResponse(['message' => 'Base de datos configurada exitosamente (usuarios y recetas)']);
        } catch (Exception $e) {
            $this->sendResponse(['error' => $e->getMessage()], 500);
        }
    }
    
    /**
     * Actualizar tablas existentes
     */
    private function updateTables() {
        try {
            $this->recipeModel->createTables();
            $this->sendResponse(['message' => 'Tablas actualizadas exitosamente']);
        } catch (Exception $e) {
            $this->sendResponse(['error' => $e->getMessage()], 500);
        }
    }
    
    /**
     * Enviar respuesta JSON
     */
    private function sendResponse($data, $statusCode = 200) {
        http_response_code($statusCode);
        echo json_encode($data, JSON_UNESCAPED_UNICODE);
        exit();
    }
}

// Inicializar la API
$api = new ApiController();
$api->handleRequest();
?>
