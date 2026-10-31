# NETPILOT · JsusOrg

Demo Android nativa para pequeños ISP/WISP. Datos ficticios y sin conexión de red.

## Compilar

Requiere JDK 17, Android SDK 35 y Build Tools 35.0.0. Configure `ANDROID_HOME` o `sdk.dir` en `local.properties` (archivo excluido de Git).

Windows: `gradlew.bat assembleDebug lintDebug`

Linux/macOS: `./gradlew assembleDebug lintDebug`

El wrapper descarga Gradle 8.11.1 y verifica su SHA-256. Primera compilación requiere Internet para dependencias. APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Verificación

GitHub Actions compila, ejecuta Android Lint y pruebas instrumentadas en Android 15 (API 35). Comprueban navegación, acciones de demostración y reentrada con JsusOrg. El artefacto `netpilot-demo-and-reports` incluye APK debug e informes. Localmente, con emulador activo: `./gradlew connectedDebugAndroidTest`.

## Alcance

Splash nativo con monograma J/O seguido de marca JsusOrg en Compose; reaparece al volver a primer plano. Dashboard y Clientes con muestras, Planes/Pagos/Herramientas como módulos pendientes. Suspender/reactivar no modifica ningún servicio. Sin permiso INTERNET, persistencia ni credenciales. Firma debug sólo para demostración.

Diseño de conectividad y evolución: [ARCHITECTURE.md](ARCHITECTURE.md).
