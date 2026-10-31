package org.jsus.netpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalContext

internal val ink = Color(0xFF101412)
internal val green = Color(0xFF8AE9A0)
internal val orange = Color(0xFFFF983F)
internal val muted = Color(0xFF89958E)

class MainActivity : ComponentActivity() {
    private val opening = mutableStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent { Netpilot(opening.value) }
    }
    override fun onStart() { super.onStart(); opening.value++ }
}

@Composable internal fun Label(value: String, color: Color = green, size: Int = 13) {
    BasicText(value, style = TextStyle(color = color, fontSize = size.sp, fontFamily = FontFamily.Monospace))
}
@Composable internal fun Rule() { Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF303B34))) }
@Composable internal fun Action(title: String, block: () -> Unit) {
    Box(Modifier.border(1.dp, muted).clickable(onClick = block).heightIn(min = 48.dp).padding(12.dp), contentAlignment = Alignment.Center) { Label(title, orange) }
}
@Composable fun JsusOrgSplash() {
    Column(Modifier.fillMaxSize().background(ink), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Label("[ J / O ]", orange, 32)
        Spacer(Modifier.height(24.dp))
        Label("JsusOrg", green, 40)
        Label("NETPILOT / LOCAL NETWORK OPERATIONS", muted, 11)
    }
}
@Composable fun Netpilot(opening: Int = 0) {
    var splash by remember(opening) { mutableStateOf(true) }
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(opening) { delay(1100); splash = false }
    val context = LocalContext.current.applicationContext
    val store = remember { LocalStore(context) }
    val scope = rememberCoroutineScope()
    var ledger by remember { mutableStateOf(Ledger()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { ledger = withContext(Dispatchers.IO) { store.read() } }
    val perform: ((LocalStore)->Unit)->Unit = { action ->
        if (!busy) { busy = true; scope.launch {
            try { ledger = withContext(Dispatchers.IO) { action(store); store.read() }; notice = "Guardado localmente" }
            catch (e: Exception) { notice = e.message ?: "No se pudo guardar" }
            finally { busy = false }
        } }
    }
    if (splash) { JsusOrgSplash(); return }
    val tabs = listOf("Panel", "Clientes", "Planes", "Pagos", "Tools")
    Column(Modifier.fillMaxSize().background(ink).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) { Label("NETPILOT", size = 22); Label("JsusOrg", orange) }
        Label("  DATOS DE EJEMPLO / SIN CONEXIÓN", muted, 11)
        if (notice.isNotEmpty()) { Label(notice, orange); Action("OCULTAR AVISO") { notice = "" } }
        Rule()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            when (page) {
                0 -> Dashboard()
                else -> Operations(page, ledger, busy, perform)
            }
        }
        Rule()
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, name ->
                Column(Modifier.weight(1f).clickable { page = i }.padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Label("0${i+1}", if (page == i) orange else muted, 11)
                    Label(name, if (page == i) green else muted, 11)
                }
            }
        }
    }
}
@Composable fun Dashboard() {
    Label("CENTRO DE OPERACIONES", size = 22)
    Label("DATOS FICTICIOS · router de ejemplo", orange)
    Label("RB5009 / RouterOS 7.x", size = 18)
    Label("Sin sesión real. Última lectura: —", muted)
    Rule()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column { Label("CPU", muted); Label("18 %", size = 28) }
        Column { Label("RAM", muted); Label("32 %", size = 28) }
        Column { Label("ACTIVOS", muted); Label("124", size = 28) }
    }
    Rule()
    Label("TRÁFICO / EJEMPLO / 60 s")
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        repeat(5) { n -> val y = size.height*n/4; drawLine(Color(0xFF303B34), Offset(0f,y), Offset(size.width,y)) }
        val values = listOf(.4f,.5f,.3f,.65f,.55f,.8f,.5f,.7f,.6f,.85f,.7f,.9f)
        listOf(green, orange).forEachIndexed { k, color ->
            values.zipWithNext().forEachIndexed { i, pair ->
                val scale = if(k == 0) .85f else .35f
                drawLine(color, Offset(size.width*i/11,size.height*(1-pair.first*scale)), Offset(size.width*(i+1)/11,size.height*(1-pair.second*scale)), 3f)
            }
        }
    }
    Label("RX 84.2 Mbps     TX 21.7 Mbps", size = 14)
    Rule()
    Label("SALUD / SIN EVALUACIÓN REAL", orange)
    Label("La integración mostrará interfaces, recursos y sesiones disponibles según permisos.", muted)
}
