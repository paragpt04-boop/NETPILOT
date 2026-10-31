package org.jsus.netpilot

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

private fun RouterSnapshot.section(name:String):List<Map<String,String>> =
    sections.firstOrNull{it.title==name}?.records.orEmpty()
private fun Map<String,String>.str(key:String)=this[key]?:"—"
private fun Long.toTraffic():String=when{
    this>=1_000_000_000L->String.format(java.util.Locale.US,"%.2f GB",this/1e9)
    this>=1_000_000L->String.format(java.util.Locale.US,"%.1f MB",this/1e6)
    this>=1000L->String.format(java.util.Locale.US,"%.1f KB",this/1e3)
    else->this.toString()+" B"
}
@Composable private fun TrafficPanel(session:VolatileRouterSession){
    Panel{
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            Column{T("TRÁFICO DE INTERFACES FÍSICAS",12,text,FontWeight.SemiBold);T("Rx / Tx · diferencia de contadores",11,muted)}
            Pill(if(session.traffic.size>=2)"REAL" else "MUESTREANDO")
        }
        val series=session.traffic
        if(series.size<2){
            Box(Modifier.fillMaxWidth().height(120.dp),contentAlignment=Alignment.Center){
                T("Esperando lecturas para generar la gráfica.",12,muted)
            }
        }else{
            val rx=series.map{it.first.toFloat().coerceAtLeast(0f)}
            val tx=series.map{it.second.toFloat().coerceAtLeast(0f)}
            val max=(rx+tx).maxOrNull()?.coerceAtLeast(1f)?:1f
            Canvas(Modifier.fillMaxWidth().height(135.dp)){
                repeat(4){i->
                    val y=size.height*i/3f
                    drawLine(line,Offset(0f,y),Offset(size.width,y),strokeWidth=1.dp.toPx())
                }
                fun graph(values:List<Float>,color:Color){
                    val p=Path()
                    values.forEachIndexed{i,v->
                        val x=size.width*i/(values.size-1)
                        val y=size.height*(1f-v/max)
                        if(i==0)p.moveTo(x,y) else p.lineTo(x,y)
                    }
                    drawPath(p,color,style=Stroke(width=2.5.dp.toPx(),cap=StrokeCap.Round))
                }
                graph(rx,accent);graph(tx,amber)
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            T("● RX  "+(series.lastOrNull()?.first?.let{String.format(java.util.Locale.US,"%.2f",it)}?:"—")+" Mbps",11,accent,monoText=true)
            T("● TX  "+(series.lastOrNull()?.second?.let{String.format(java.util.Locale.US,"%.2f",it)}?:"—")+" Mbps",11,amber,monoText=true)
        }
    }
}
@Composable fun DashboardScreen(session:VolatileRouterSession,ledger:Ledger,onConnect:()->Unit){
    LaunchedEffect(session.connected){
        while(session.connected){
            delay(30000)
            if(session.connected)session.refresh()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)){
        PageHeading("Centro de operaciones","Estado de la infraestructura en tiempo real"){
            if(session.connected)SoftButton("Actualizar",{session.refreshAsync()}) else SolidButton("Conectar",onConnect)
        }
        if(!session.connected){
            EmptyState("Conecta tu primer MikroTik",
                "El Dashboard mostrará exclusivamente mediciones del router. Conéctate a la red Wi-Fi y abre una sesión API-SSL.",
                "CONFIGURAR CONEXIÓN",onConnect)
        }else{
            val s=session.snapshot
            val sys=s?.section("Sistema")?.firstOrNull().orEmpty()
            val identity=s?.section("Identidad")?.firstOrNull()?.get("name")?:session.host
            Panel{
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){T(identity,18,text,FontWeight.SemiBold,maxLines=1);T(session.host,11,muted,monoText=true)}
                    Pill(if(session.problem.isBlank())"CONECTADO" else "LECTURA FALLIDA",session.problem.isBlank())
                }
                DividerLine()
                T((sys["board-name"]?:"MikroTik")+"  ·  RouterOS "+(sys["version"]?:"—"),12,muted)
                T("Uptime "+(sys["uptime"]?:"—"),11,muted,monoText=true)
                if(session.problem.isNotBlank())T(session.problem,11,danger)
                if(session.loading)T("Actualizando métricas…",11,amber)
            }
            val total=sys["total-memory"]?.toLongOrNull()?:0L
            val free=sys["free-memory"]?.toLongOrNull()?:0L
            val ram=if(total>0)((total-free).coerceAtLeast(0)*100/total).toString()+" %" else "—"
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Metric("CPU",sys["cpu-load"]?.let{it+" %"}?:"—","Carga actual",Modifier.weight(1f),accent)
                Metric("RAM",ram,"Memoria en uso",Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                val ppp=s?.section("PPPoE / PPP")?.size?:0
                val hotspot=s?.section("Hotspot")?.size?:0
                Metric("Sesiones",(ppp+hotspot).toString(),"PPP + Hotspot",Modifier.weight(1f))
                val interfaces=s?.section("Interfaces").orEmpty()
                Metric("Interfaces",interfaces.count{it["running"]=="true"}.toString(),
                    "de "+interfaces.size+" visibles",Modifier.weight(1f))
            }
            TrafficPanel(session)
            Panel{
                T("INTERFACES",13,text,FontWeight.SemiBold)
                val ints=s?.section("Interfaces").orEmpty()
                if(ints.isEmpty())T("Sin interfaces visibles para esta cuenta.",12,muted)
                ints.take(8).forEach{it->
                    DividerLine()
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                        Column(Modifier.weight(1f)){
                            T(it.str("name"),13,text,FontWeight.Medium)
                            T(it.str("type"),10,muted,monoText=true)
                        }
                        Column(horizontalAlignment=Alignment.End){
                            Pill(if(it["running"]=="true")"UP" else "DOWN",it["running"]=="true")
                            T("RX "+(it["rx-byte"]?.toLongOrNull()?.toTraffic()?:"—"),10,muted,monoText=true)
                        }
                    }
                }
            }
        }
        Panel{
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                T("GESTIÓN COMERCIAL",13,text,FontWeight.SemiBold);Pill("LOCAL")
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Metric("Clientes",ledger.customers.size.toString(),"Registrados",Modifier.weight(1f))
                Metric("Morosos",ledger.customers.count{ledger.overdue(it.id)}.toString(),
                    "Facturas vencidas",Modifier.weight(1f),amber)
            }
        }
        T("NETPILOT / JsusOrg  ·  Todos los datos permanecen en el equipo",10,muted,monoText=true)
    }
}
