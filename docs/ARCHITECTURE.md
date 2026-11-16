# Arquitectura VISION PRO

- Android nativo: Kotlin + Jetpack Compose.
- Reproducción prevista: AndroidX Media3 (HLS/DASH).
- API: Node.js + Express en Render.
- Datos: Neon PostgreSQL.
- Seguridad: JWT, contraseñas con bcrypt, conexión a DB sólo desde backend.
- Tipos de contenido: live, movie, series.
- Fuentes permitidas: contenido propio, licenciado, dominio público y listas de usuario con confirmación de derechos.

## Endpoints iniciales
- GET /health
- POST /api/auth/register
- POST /api/auth/login
- GET /api/me
- GET /api/catalog/:type
- GET /api/categories/:type
- POST /api/playlists
