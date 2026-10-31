# NETPILOT / JsusOrg

Estado: aplicación local con datos iniciales ficticios. Implementa SQLite, clientes, planes, cargos, pagos, reversos, recibos compartibles y exportación. No implementa conexión ni comandos de router. Sin permiso de Internet deliberadamente hasta integrar. Splash Compose en cada entrada a primer plano; la pantalla inicial del sistema utiliza un monograma J/O con SplashScreen compat. La verificación reproducible está en GitHub Actions; no se ha probado en un teléfono físico.

## Conectividad prevista
Android → red Wi-Fi seleccionada → socket TLS API-SSL 8729 → RouterOS. No REST: el protocolo binario RouterOS requiere codificar longitudes, sentencias, tags, !re/!done/!trap y límites de respuesta. 8728 transmite sin TLS y estará deshabilitado. 8729 exige certificado con cadena y nombre/IP válidos, o confianza explícita en certificado verificado fuera de banda. Nunca aceptar cualquier certificado; no usar modo anónimo sin certificado. No habilitar servicios, editar firewall ni crear usuarios automáticamente. Un fallo devuelve diagnóstico y no modifica la red.

Usar ConnectivityManager para escoger Wi-Fi y crear el socket sobre esa Network; manejar pérdida de LAN, cancelación y tiempos máximos. Sin necesidad de Internet, pero requiere ruta accesible y ausencia de aislamiento Wi-Fi. Permisos de acceso local dependen de versión Android y target SDK; revisarlos antes de integrar.

## Sesión y seguridad
Solicitar IP, puerto, usuario y contraseña únicamente en integración. No incluirlos en Room, preferencias, SavedStateHandle, rememberSaveable, backups, analytics ni logs. Custodia propuesta AES-GCM con clave aleatoria y ciphertext sólo en proceso; descifrado mínimo para autenticar. Usar buffers borrables cuando sea posible. La clave también reside en RAM: esto no protege frente a un proceso comprometido ni garantiza cero copias en JVM/TLS. No prometer eliminación física inmediata. Cerrar socket, cancelar trabajos y borrar referencias/buffers al salir a segundo plano, desconectar o expirar sesión. No depender de onDestroy; muerte del proceso elimina la sesión, no permite garantizar borrado físico. Esta custodia aún no está implementada.

Cuenta dedicada con lectura y acceso API mínimo; jamás permisos write/policy/sensitive por defecto. Allowlist de comandos y campos, sin consola arbitraria. Suspender/reactivar requiere sesión con permiso explícito, vista previa del cliente y objeto, confirmación y registro de resultado. No inferir suspensión por morosidad ni tratar DHCP lease como control universal del servicio.

## Datos disponibles y límites
Recursos CPU/RAM/uptime; interfaces y contadores; direcciones, ARP, leases; sesiones PPP/Hotspot; perfiles y colas; rutas, reglas firewall y logs según permisos. Wireless/WiFi y sensores dependen del hardware y paquetes. Seleccionar campos para no traer secretos. Rx/Tx se calcula por delta de contadores y tiempo, detectando reinicios; no inventar historial. Sesiones activas no equivalen a clientes. El teléfono cerrado no monitoriza continuamente. Escaneo LAN es explícito y acotado; no descubre todos los equipos. Backup RouterOS puede escribir archivos y contener secretos: acción privilegiada aparte, nunca consulta automática.

## Stack y evolución
Kotlin, Compose Foundation con componentes propios, coroutines/StateFlow, ViewModel y repositorios. SQLiteOpenHelper implementado para clientes, planes, facturas, pagos y eventos outbox; Room queda como evolución del repositorio. Credenciales separadas de toda entidad persistente. IDs UUID, importes en unidades monetarias menores con moneda, fechas UTC, facturas y aplicaciones de pagos; corregir mediante reversos, no borrar movimientos. Estado comercial separado del estado técnico del servicio.

Pagos fase 1: efectivo/transferencia, referencia, fecha, importe, moneda, operador y recibo local. Registro manual no verifica transferencia bancaria. Saldo por cargos menos pagos aplicados y reversos. Recibo no presume validez fiscal. Transacción local y evento outbox atómicos implementados; UUID idempotente, versiones y tombstones preparan sincronización futura.

VPS futuro: autenticación por organización, sincronización opcional, conflictos explícitos e idempotencia. Nunca sincronizar credenciales RouterOS. Acceso remoto mediante túnel privado o agente local autorizado, sin publicar 8729 en Internet.

## Construcción pendiente
Abrir en Android Studio con JDK 17, SDK 35 y Gradle 8.11.1. Dependencias fijadas como base, no afirmadas como últimas versiones. Wrapper Gradle incluido con checksum. GitHub Actions compila, analiza y prueba en emulador; consultar el resultado de la ejecución del commit.

Fuentes: https://help.mikrotik.com/docs/spaces/ROS/pages/47579160/API ; https://developer.android.com/develop/ui/compose/setup
