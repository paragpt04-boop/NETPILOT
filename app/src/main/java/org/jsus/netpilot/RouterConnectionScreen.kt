package org.jsus.netpilot

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import javax.net.ssl.SSLException

/**
 * Fast setup: username, password, port. Wi-Fi gateway is only a suggested address.
 * Never send credentials during certificate inspection. User confirms peer fingerprint
 * as trust-on-first-use; for authenticated identity compare via trusted out-of-band channel.
 */
@Composable fun RouterConnectionScreen(session:VolatileRouterSession) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var form by remember{mutableStateOf(false)}
    var address by remember{mutableStateOf("")}
    var showAddress by remember{mutableStateOf(false)}
    var port by remember{mutableStateOf("8729")}
    var username by remember{mutableStateOf("")}
    var password by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    var pendingFingerprint by remember{mutableStateOf<String?>(null)}
    val detected=remember(form){if(form)LocalGateway.probableRouter(context) else null}
    LaunchedEffect(form){
        if(form && address.isBlank()){
            address=detected.orEmpty()
            showAddress=detected==null
        }
    }
    Panel {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            T("CONEXIÓN MIKROTIK",13,text,FontWeight.SemiBold)
            Pill(if(session.connected)"SESIÓN ACTIVA" else "SIN SESIÓN",session.connected)
        }
        if(session.connected) {
            T("Router: "+session.host,13,text,monoText=true)
            T("API-SSL · TLS · consultas de solo lectura",11,muted)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SolidButton(if(session.loading)"Leyendo…" else "Actualizar",{
                    scope.launch{session.refresh()}
                },!session.loading)
                SoftButton("Desconectar",{
                    session.disconnect();form=false;pendingFingerprint=null;error=""
                })
            }
        } else {
            T("Conecta tu teléfono al Wi-Fi que alcanza al MikroTik.",12,muted)
            if(!form)SolidButton("Conectar router",{form=true;error=""})
        }
        if(form && !session.connected){
            DividerLine()
            if(address.isNotBlank() && !showAddress){
                T("IP propuesta desde el Wi-Fi: "+address,12,accent,monoText=true)
                SoftButton("Cambiar IP",{showAddress=true})
            } else {
                Entry("IP del MikroTik",address,{address=it})
                if(detected!=null)T("Puerta de enlace detectada: "+detected,11,muted)
            }
            Entry("Usuario",username,{username=it})
            Entry("Contraseña",password,{password=it},secret=true)
            Entry("Puerto seguro",port,{port=it},numeric=true)
            T("Se utiliza TLS (8729 por defecto). No se emplea la API sin cifrado.",11,muted)
            if(error.isNotBlank())T(error,11,danger)
            if(session.loading)T("Verificando conexión segura…",11,amber)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SoftButton("Cancelar",{
                    password="";username="";address="";form=false
                    pendingFingerprint=null;error="";session.disconnect()
                })
                SolidButton("Conectar",{
                    val addressToTry=address.trim()
                    val portToTry=port.toIntOrNull()
                    val usernameToTry=username
                    val passwordToTry=password
                    password="";username="";error=""
                    try{
                        session.stage(addressToTry,portToTry?:8729,usernameToTry,passwordToTry)
                        scope.launch {
                            try {
                                session.connectStaged()
                                form=false
                            }catch(e:SSLException) {
                                // TLS failed before login; inspect certificate without password.
                                try {
                                    pendingFingerprint=session.inspectCertificate()
                                    form=false
                                }catch(e2:Exception) {
                                    error="No se pudo comprobar el certificado TLS. Revisa el servicio API-SSL."
                                }
                            }catch(e:Exception) {
                                error=session.problem.ifBlank{e.message?:"No se pudo conectar"}
                            }
                        }
                    }catch(e:Exception){error=e.message?:"Datos de conexión inválidos"}
                },!session.loading && address.isNotBlank() && username.isNotBlank()
                    && password.isNotBlank() && (port.toIntOrNull()?.let{it in 1..65535}==true))
            }
        }
        if(!form && !session.connected && session.problem.isNotBlank() && pendingFingerprint==null)
            T(session.problem,11,amber)
        T("Las credenciales no se guardan en el teléfono. Se eliminan al salir de la app.",10,muted)
    }
    pendingFingerprint?.let{fingerprint->
        AlertDialog(
            onDismissRequest={pendingFingerprint=null;session.disconnect()},
            containerColor=surface,
            title={T("Verificar MikroTik",19,text,FontWeight.SemiBold)},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                    T("El router usa un certificado propio que Android no reconoce automáticamente.",13,text)
                    T("Huella SHA-256 presentada por el equipo:",11,muted)
                    T(fingerprint.chunked(16).joinToString("\n"),13,accent,FontWeight.Medium,monoText=true)
                    T("Antes de confiar, compara esta huella con la del certificado de tu MikroTik por un medio independiente. Si no coincide, CANCELA.",12,amber)
                    T("Si aceptas, solo se confiará en este certificado durante la sesión actual. No se guardará en disco.",11,muted)
                    if(error.isNotBlank())T(error,11,danger)
                }
            },
            dismissButton={SoftButton("Cancelar",{
                pendingFingerprint=null;session.disconnect();error=""
            })},
            confirmButton={
                SolidButton(if(session.loading)"Conectando…" else "Verificado · conectar",{
                    scope.launch{
                        try{
                            session.connectStaged(fingerprint)
                            pendingFingerprint=null;form=false;error=""
                        }catch(e:Exception){
                            error=session.problem.ifBlank{e.message?:"No se pudo conectar"}
                        }
                    }
                },!session.loading)
            }
        )
    }
}
