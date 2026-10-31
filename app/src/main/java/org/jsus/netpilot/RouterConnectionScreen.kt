package org.jsus.netpilot

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

@Composable fun RouterConnectionScreen(session:VolatileRouterSession){
    val scope=rememberCoroutineScope()
    var form by remember{mutableStateOf(false)}
    var address by remember{mutableStateOf("")}
    var port by remember{mutableStateOf("8729")}
    var username by remember{mutableStateOf("")}
    var password by remember{mutableStateOf("")}
    var fingerprint by remember{mutableStateOf("")}
    var error by remember{mutableStateOf("")}
    Panel{
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            T("CONEXIÓN MIKROTIK",13,text,FontWeight.SemiBold)
            Pill(if(session.connected)"SESIÓN ACTIVA" else "SIN SESIÓN",session.connected)
        }
        if(session.connected){
            T("Router: "+session.host,13,text,monoText=true)
            T("API-SSL · acceso local · sin escritura",11,muted)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SolidButton(if(session.loading)"Leyendo…" else "Actualizar",{
                    scope.launch{session.refresh()}
                },!session.loading)
                SoftButton("Desconectar",{session.disconnect();form=false})
            }
        }else{
            T("El teléfono debe tener acceso a la LAN donde está el MikroTik. Se requiere el servicio API-SSL con certificado.",12,muted)
            if(!form)SolidButton("Conectar router",{form=true})
        }
        if(form && !session.connected){
            DividerLine()
            Entry("IP / hostname",address,{address=it})
            Entry("Puerto API-SSL",port,{port=it},numeric=true)
            Entry("Usuario de solo lectura",username,{username=it})
            Entry("Contraseña (no persistente)",password,{password=it},secret=true)
            Entry("SHA-256 del certificado (opcional)",fingerprint,{fingerprint=it})
            T("Sin huella: exige certificado confiable y hostname correcto. Si el certificado es propio, verifica su huella fuera de esta aplicación antes de introducirla.",11,muted)
            if(error.isNotBlank())T(error,11,danger)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SoftButton("Cancelar",{
                    password="";username="";address="";fingerprint="";form=false;error=""
                })
                SolidButton(if(session.loading)"Conectando…" else "Conectar",{
                    val inputAddress=address;val inputPort=port.toIntOrNull()
                    val inputUser=username;val inputPass=password;val inputPin=fingerprint
                    password="";username="";address="";fingerprint="";error=""
                    scope.launch{
                        try{
                            session.connect(inputAddress,inputPort?:8729,inputUser,inputPass,inputPin)
                            form=false
                        }catch(e:Exception){
                            error=session.problem.ifBlank{"Verifica IP, puerto, permisos y certificado."}
                        }
                    }
                },!session.loading&&password.isNotBlank()&&address.isNotBlank()&&username.isNotBlank())
            }
        }
        if(session.problem.isNotBlank())T(session.problem,11,amber)
        T("La sesión y sus claves se eliminan al abandonar la aplicación. No se almacena la contraseña.",10,muted)
    }
}
