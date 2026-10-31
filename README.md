# NETPILOT · JsusOrg

Aplicación Android nativa para pequeños ISP/WISP. Panel principal con datos ficticios; la pestaña Tools incorpora una lectura real y segura de MikroTik en la LAN.

## Compilar

Requiere JDK 17, Android SDK 35 y Build Tools 35.0.0. Configure `ANDROID_HOME` o `sdk.dir` en `local.properties` (archivo excluido de Git).

Windows: `gradlew.bat assembleDebug lintDebug`

Linux/macOS: `./gradlew assembleDebug lintDebug`

El wrapper descarga Gradle 8.11.1 y verifica su SHA-256. Primera compilación requiere Internet para dependencias. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Verificación

GitHub Actions compila, ejecuta Android Lint y pruebas instrumentadas en Android 15 (API 35). Comprueban navegación, acciones de demostración y reentrada con JsusOrg. El artefacto `netpilot-demo-and-reports` incluye APK debug e informes. Localmente, con emulador activo: `./gradlew connectedDebugAndroidTest`.

## Alcance

Splash nativo con monograma J/O seguido de marca JsusOrg en Compose; reaparece al volver a primer plano. Dashboard con métricas de ejemplo. Clientes y planes editables, cargos, cobros parciales, anulación, recibos compartibles y exportación JSON. SQLite conserva datos comerciales; los ejemplos se cargan una sola vez. Moneda CUP. Suspender/reactivar cambia únicamente el estado simulado. Con permiso INTERNET exclusivamente para consultas API-SSL 8729. Contraseña solo en memoria transitoria, nunca en SQLite ni preferencias. No hay comandos de escritura; el panel inicial sigue con datos de demostración. Firma debug de instalación para verificación; una distribución de producción necesita firma de lanzamiento propia.

Diseño de conectividad y evolución: [ARCHITECTURE.md](ARCHITECTURE.md).
