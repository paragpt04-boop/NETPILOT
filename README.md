# NETPILOT · JsusOrg

Aplicación Android nativa local-first para operadores ISP/WISP. No utiliza VPS en esta fase.

## Operación
- Splash JsusOrg al abrir y al regresar al primer plano.
- Cinco pestañas: Dashboard, Clientes, Planes, Pagos y Herramientas.
- Dashboard con CPU, RAM, sesiones, interfaces y muestras reales de tráfico; no utiliza datos ficticios.
- RouterOS API-SSL con TLS, 8729 predeterminado, puerto configurable, certificado de CA confiable o pin SHA-256 verificado.
- Sesión volátil AES-GCM cifrada en memoria y destruida al abandonar la app. La memoria de un proceso comprometido sigue siendo vulnerable; los Strings transitorios no pueden borrarse de forma garantizada en JVM.
- SQLite local para clientes, planes, cargos, pagos parciales y anulaciones. Base de datos vacía al instalar.
- Herramientas de solo lectura: vecinos LAN, logs, rutas, interfaces y otras secciones RouterOS.
- Suspensión y reactivación modifican únicamente el estado administrativo local, sin comandos al MikroTik.

## Compilación
JDK 17, SDK Android 35, Gradle 8.11.1. Ejecutar: ./gradlew assembleDebug lintDebug assembleDebugAndroidTest
GitHub Actions ejecuta también pruebas instrumentadas con emulador.

## Instalación y migración
El dispositivo Android debe alcanzar la LAN de administración del MikroTik por Wi-Fi. La API-SSL debe estar configurada con certificado y usuario restringido de lectura. NETPILOT no cambia router, firewall ni servicios por su cuenta.
Se utiliza netpilot-production.db, separada de la antigua netpilot-examples.db. La demostración antigua no se importa automáticamente para evitar mezclar datos ficticios.

Ver ARCHITECTURE.md.