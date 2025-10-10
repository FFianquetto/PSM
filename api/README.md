# API REST para Migración de Datos SQLite a MySQL

## Estructura de Archivos

```
api/
├── config/
│   └── database.php          # Configuración de conexión MySQL
├── models/
│   └── UserModel.php         # Modelo para manejar usuarios
├── index.php                 # Controlador principal de la API
├── setup.php                 # Script de configuración inicial
└── .htaccess                 # Configuración de rutas
```

## Configuración Inicial

### 1. Configurar la Base de Datos

Tienes varias opciones para configurar tu base de datos:

#### Opción A: Configuración Automática (Recomendada)
```bash
php configure.php
```

#### Opción B: Configuración Manual
Edita el archivo `config.env` con tus credenciales:

```env
DB_HOST=localhost
DB_NAME=ejemplo2_db
DB_USERNAME=tu_usuario
DB_PASSWORD=tu_contraseña
DB_CHARSET=utf8mb4
```

### 2. Tipos de Base de Datos Soportados

- **Local**: XAMPP, WAMP, MAMP
- **Gratuitas en la nube**: PlanetScale, Railway, Supabase
- **Pago en la nube**: AWS RDS, Google Cloud SQL, Azure Database
- **Hosting compartido**: cPanel con MySQL

### 3. Ejecutar Setup

```bash
php setup.php
```

O visita: `http://tu-servidor/api/setup.php`

## Endpoints de la API

### Base URL: `http://tu-servidor/api/`

### 1. Health Check
- **GET** `/health`
- **Descripción**: Verificar que la API esté funcionando
- **Respuesta**:
```json
{
    "status": "OK",
    "message": "API funcionando"
}
```

### 2. Configurar Base de Datos
- **POST** `/setup`
- **Descripción**: Crear tablas en MySQL
- **Respuesta**:
```json
{
    "message": "Base de datos configurada exitosamente"
}
```

### 3. Migrar Usuarios
- **POST** `/migrate/users`
- **Descripción**: Migrar usuarios desde SQLite a MySQL
- **Body**:
```json
{
    "users": [
        {
            "name": "Juan",
            "lastName": "Pérez",
            "email": "juan@example.com",
            "password": "password123",
            "phone": "1234567890",
            "address": "Calle 123",
            "alias": "juanperez",
            "avatarPath": "/path/to/avatar.jpg",
            "createdAt": 1640995200000,
            "updatedAt": 1640995200000
        }
    ]
}
```
- **Respuesta**:
```json
{
    "message": "Migración completada",
    "migrated": 1,
    "total": 1,
    "errors": []
}
```

### 4. Crear Usuario Individual
- **POST** `/users`
- **Descripción**: Crear un usuario individual
- **Body**:
```json
{
    "name": "María",
    "lastName": "García",
    "email": "maria@example.com",
    "password": "password123",
    "phone": "0987654321",
    "address": "Avenida 456",
    "alias": "mariagarcia",
    "avatarPath": "/path/to/avatar.jpg"
}
```
- **Respuesta**:
```json
{
    "message": "Usuario creado exitosamente",
    "userId": 123
}
```

### 5. Obtener Todos los Usuarios
- **GET** `/users`
- **Descripción**: Obtener lista de todos los usuarios
- **Respuesta**:
```json
{
    "users": [
        {
            "id": 1,
            "name": "Juan",
            "last_name": "Pérez",
            "email": "juan@example.com",
            "phone": "1234567890",
            "address": "Calle 123",
            "alias": "juanperez",
            "avatar_path": "/path/to/avatar.jpg",
            "created_at": 1640995200000,
            "updated_at": 1640995200000
        }
    ]
}
```

## Códigos de Estado HTTP

- **200**: Éxito
- **201**: Creado exitosamente
- **400**: Solicitud incorrecta
- **404**: Endpoint no encontrado
- **405**: Método no permitido
- **409**: Conflicto (email/alias ya existe)
- **500**: Error interno del servidor

## Manejo de Errores

La API devuelve errores en formato JSON:

```json
{
    "error": "Descripción del error"
}
```

## CORS

La API está configurada para aceptar peticiones desde cualquier origen. Para producción, considera restringir los orígenes permitidos.

## Próximos Pasos

1. Configurar el servidor web (Apache/Nginx)
2. Configurar las credenciales de MySQL
3. Ejecutar el setup inicial
4. Probar los endpoints con datos de prueba
5. Integrar con la aplicación Android
