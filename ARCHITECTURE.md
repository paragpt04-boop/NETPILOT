# NETPILOT — Arquitectura local-first

## Conectividad
Android Wi-Fi/LAN → socket TLS sobre RouterOS API-SSL (por defecto 8729). El puerto 8728 carece de cifrado y no se usa. Se requiere certificado de CA válido con hostname/IP coincidente o huella SHA-256 previamente verificada fuera de la APK. No se admite trust-all ni consola genérica.
La aplicación no habilita servicios, no modifica firewall y no ejecuta comandos RouterOS que escriban estado. Usa consultas print con .proplist. La disponibilidad de datos depende de permisos, hardware y firmware.

## Seguridad de sesión
MainActivity posee una instancia de VolatileRouterSession no persistente. Se cifra la configuración completa de conexión en AES-GCM dentro del mismo proceso con clave y nonce aleatorios guardados solo en RAM. Los campos de entrada se limpian tras empezar la conexión. onStop() elimina clave, datos cifrados y estadísticas. El cifrado protege frente a persistencia accidental, no frente a ataques al proceso; Android/JVM puede realizar copias temporales no borrables de Strings.
Se recomienda crear un usuario API dedicado de solo lectura y limitar el servicio al segmento de gestión.

## Modelo de datos
LocalStore usa SQLiteOpenHelper, tablas planes/clientes/bills/payments/outbox con IDs y transacciones. Los cobros son manuales: no verifican depósitos bancarios. El saldo de cargos se calcula neto de cobros y reversos. El esquema outbox prepara sincronización futura con un VPS, sin guardarse credenciales RouterOS.
La primera moneda es CUP. Una futura versión deberá elegir moneda ISO por organización y migrar el esquema.

## Dashboard y herramientas
Solo presenta lecturas devueltas por RouterOS. Las muestras Rx/Tx estiman tasas por diferencia de contadores de interfaces físicas y tiempo monotónico; pueden incluir tráfico LAN y NO sustituyen medición facturable de WAN. El teléfono cerrado deja de muestrear. Vecinos LAN reflejan información vista por MikroTik, no un escaneo exhaustivo de subred.
Logs y datos de red se mantienen volátiles. El backup del router no se ejecuta automáticamente porque requiere permisos adicionales. Suspender/reactivar clientes en esta fase es un estado comercial local con confirmación explícita.

## Futuro
Se podrán añadir roles, facturación electrónica, sincronización opt-in multiempresa y acceso remoto por túnel o agente, jamás exponiendo RouterOS API-SSL a Internet público por defecto.
Cuando el target llegue a Android 17 / API 37 habrá que solicitar ACCESS_LOCAL_NETWORK de acuerdo con https://developer.android.com/privacy-and-security/local-network-permission
API RouterOS: https://help.mikrotik.com/docs/spaces/ROS/pages/47579160/API