package org.jsus.netpilot
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

@Composable private fun RouterField(label:String,value:String,onChange:(String)->Unit,secret:Boolean=false){
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
        Label(label,muted)
        BasicTextField(
            value=value,onValueChange=onChange,
            modifier=Modifier.fillMaxWidth().border(1.dp,muted).padding(12.dp),
            singleLine=true,textStyle=TextStyle(color=green,fontSize=15.sp,fontFamily=FontFamily.Monospace),
            cursorBrush=SolidColor(orange),
            visualTransformation=if(secret)PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions=KeyboardOptions(keyboardType=if(secret)KeyboardType.Password else KeyboardType.Text)
        )
    }
}

@Composable fun RouterConnectionScreen(){
    val scope=rememberCoroutineScope()
    var host by remember{mutableStateOf("")}
    var user by remember{mutableStateOf("")}
    var password by remember{mutableStateOf("")}
    var fingerprint by remember{mutableStateOf("")}
    var working by remember{mutableStateOf(false)}
    var problem by remember{mutableStateOf("")}
    var snapshot by remember{mutableStateOf<RouterSnapshot?>(null)}
    var expanded by remember{mutableStateOf<String?>(null)}
    Label("MIKROTIK / LECTURA REAL",orange,20)
    Label("LAN · API-SSL puerto 8729 · TLS obligatorio · sin comandos de escritura",muted,11)
    RouterField("IP o hostname del router",host,{host=it})
    RouterField("Usuario de lectura",user,{user=it})
    RouterField("Contraseña (no se guarda)",password,{password=it},true)
    RouterField("Huella SHA-256 de certificado (opcional)",fingerprint,{fingerprint=it})
    Label("Sin huella: requiere certificado confiable y nombre/IP coincidente. Con huella: debes verificarla por otro medio antes de introducirla.",muted,11)
    if(working) Label("Consultando el router...",orange)
    if(problem.isNotBlank())Label(problem,orange)
    Action("CONECTAR Y CONSULTAR"){
        if(!working){
            val submittedPassword=password
            password=""
            working=true
            problem=""
            snapshot=null
            scope.launch{
                try{
                    snapshot=RouterOsReader.query(host,user,submittedPassword,fingerprint)
                }catch(e:Exception){
                    problem=when(e){
                        is javax.net.ssl.SSLHandshakeException -> "Conexión TLS rechazada: verifica el certificado, nombre/IP o huella."
                        is java.net.SocketTimeoutException -> "Tiempo agotado: revisa que el puerto 8729 sea accesible desde la LAN."
                        is java.net.ConnectException -> "No conecta a 8729: comprueba API-SSL y firewall."
                        else -> e.message?:"Error desconocido al leer RouterOS."
                    }
                }finally{working=false}
            }
        }
    }
    snapshot?.let{data->
        Rule()
        Label("DATOS REALES / "+data.host,green,18)
        Label("Captura UTC: "+data.at,muted,11)
        Label("Máximo 200 registros por apartado. No se persisten las lecturas.",muted,11)
        data.sections.forEach{section->
            Rule()
            Action(section.title.uppercase()+" ("+section.records.size+")"){
                expanded=if(expanded==section.title)null else section.title
            }
            section.error?.let{Label("No disponible: "+it,orange,11)}
            if(expanded==section.title){
                if(section.records.isEmpty())Label("Sin registros visibles.",muted)
                section.records.take(40).forEachIndexed{index,row->
                    Label((index+1).toString()+". "+row.entries.joinToString(" · "){it.key+": "+it.value},green,11)
                }
                if(section.records.size>40)Label("Mostrando 40 de "+section.records.size,muted,11)
            }
        }
        Action("CERRAR CONSULTA / BORRAR LECTURA"){snapshot=null;expanded=null;problem=""}
    }
}
