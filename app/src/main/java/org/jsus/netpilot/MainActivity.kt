package org.jsus.netpilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity:ComponentActivity(){
    private val session=VolatileRouterSession()
    private val opening=mutableIntStateOf(0)
    override fun onCreate(savedInstanceState:Bundle?){
        installSplashScreen();super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(12,18,24)
        window.navigationBarColor=android.graphics.Color.rgb(12,18,24)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent{Netpilot(session,opening.intValue)}
    }
    override fun onStart(){super.onStart();opening.intValue++}
    override fun onStop(){session.disconnect();super.onStop()}
}
@Composable fun JsusOrgSplash(){
    Column(Modifier.fillMaxSize().background(bg),verticalArrangement=Arrangement.Center,
      horizontalAlignment=Alignment.CenterHorizontally){
        Box(Modifier.size(82.dp).border(1.dp,accent,RoundedCornerShape(20.dp))
          .background(accent.copy(alpha=.09f),RoundedCornerShape(20.dp)),contentAlignment=Alignment.Center){
            T("J/O",26,accent,FontWeight.Bold,monoText=true)
        }
        Spacer(Modifier.height(26.dp))
        T("JsusOrg",32,text,FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        T("NETWORK OPERATIONS",11,accent,FontWeight.Medium,monoText=true)
        Spacer(Modifier.height(50.dp))
        T("NETPILOT",11,muted,monoText=true)
    }
}
private data class NavTab(val name:String,val icon:androidx.compose.ui.graphics.vector.ImageVector)
private val tabs=listOf(
    NavTab("Dashboard",Icons.Outlined.Dashboard),
    NavTab("Clientes",Icons.Outlined.Groups),
    NavTab("Planes",Icons.Outlined.Tune),
    NavTab("Pagos",Icons.Outlined.ReceiptLong),
    NavTab("Herramientas",Icons.Outlined.BuildCircle)
)
@Composable private fun BottomBar(current:Int,onChange:(Int)->Unit){
    Row(Modifier.fillMaxWidth().background(bg).border(BorderStroke(1.dp,line))
      .navigationBarsPadding().padding(horizontal=4.dp,vertical=7.dp)){
        tabs.forEachIndexed{index,tab->
            val active=index==current
            Column(Modifier.weight(1f).clickable{onChange(index)}.padding(vertical=5.dp),
              horizontalAlignment=Alignment.CenterHorizontally,
              verticalArrangement=Arrangement.spacedBy(5.dp)){
                Icon(tab.icon,contentDescription=tab.name,tint=if(active)accent else muted,modifier=Modifier.size(21.dp))
                T(tab.name,if(index==4)9 else 10,if(active)accent else muted,
                    if(active)FontWeight.SemiBold else FontWeight.Normal,maxLines=1)
            }
        }
    }
}
@Composable fun Netpilot(session:VolatileRouterSession,opening:Int){
    var splash by remember(opening){mutableStateOf(true)}
    var page by rememberSaveable{mutableIntStateOf(0)}
    LaunchedEffect(opening){delay(900);splash=false}
    val context=androidx.compose.ui.platform.LocalContext.current.applicationContext
    val store=remember{LocalStore(context)}
    var ledger by remember{mutableStateOf(Ledger())}
    var busy by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf("")}
    val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){ledger=withContext(Dispatchers.IO){store.read()}}
    val perform:((LocalStore)->Unit)->Unit={block->
        if(!busy){busy=true;scope.launch{
            try{
                ledger=withContext(Dispatchers.IO){block(store);store.read()}
                notice="Guardado en este dispositivo"
            }catch(e:Exception){notice=e.message?:"No se pudo guardar"}
            finally{busy=false}
        }}
    }
    if(splash){JsusOrgSplash();return}
    MaterialTheme(colorScheme=darkColorScheme(primary=accent,background=bg,surface=surface,onSurface=text,onBackground=text)){
        Scaffold(containerColor=bg,topBar={
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp)
              .background(bg).padding(horizontal=18.dp),
              verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){
                    Box(Modifier.size(33.dp).background(accent.copy(alpha=.12f),RoundedCornerShape(9.dp)),
                      contentAlignment=Alignment.Center){T("J",17,accent,FontWeight.Bold,monoText=true)}
                    Column{T("NETPILOT",16,text,FontWeight.Bold);T("by JsusOrg",10,muted)}
                }
                Pill(if(session.connected)"LAN · ONLINE" else "SIN CONEXIÓN",session.connected)
            }
        },bottomBar={BottomBar(page){page=it}}){pad->
            Column(Modifier.fillMaxSize().padding(pad)){
                if(notice.isNotBlank()){
                    Row(Modifier.fillMaxWidth().background(panel2).padding(9.dp),
                      verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                        T(notice,11,amber,modifier=Modifier.weight(1f))
                        TextButton(onClick={notice=""}){T("CERRAR",10,accent)}
                    }
                }
                when(page){
                    0->DashboardScreen(session,ledger,onConnect={page=4})
                    1->ClientsScreen(ledger,busy,perform)
                    2->PlansScreen(ledger,busy,perform)
                    3->PaymentsScreen(ledger,busy,perform)
                    else->ToolsScreen(session,ledger)
                }
            }
        }
    }
}
