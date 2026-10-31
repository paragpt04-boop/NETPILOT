package org.jsus.netpilot

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import java.time.LocalDate
import java.math.BigDecimal

private fun amount(cents:Long)=money(cents)
@Composable private fun ScreenScroll(content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp),content=content)
}
@Composable private fun Editor(title:String,onClose:()->Unit,content:@Composable ColumnScope.()->Unit){
    AlertDialog(onDismissRequest=onClose,containerColor=surface,
        title={T(title,18,text,FontWeight.SemiBold)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(12.dp),content=content)},
        confirmButton={},dismissButton={})
}
@Composable fun ClientsScreen(data:Ledger,busy:Boolean,perform:((LocalStore)->Unit)->Unit){
    var search by remember{mutableStateOf("")}
    var overdueOnly by remember{mutableStateOf(false)}
    var edit by remember{mutableStateOf<Customer?>(null)}
    var showEdit by remember{mutableStateOf(false)}
    var name by remember{mutableStateOf("")}
    var ip by remember{mutableStateOf("")}
    var plan by remember{mutableStateOf("")}
    var changeState by remember{mutableStateOf<Customer?>(null)}
    fun prepare(customer:Customer?){
        edit=customer;name=customer?.name.orEmpty();ip=customer?.ip.orEmpty()
        plan=customer?.plan?:data.plans.firstOrNull()?.id.orEmpty()
        showEdit=true
    }
    ScreenScroll{
        PageHeading("Clientes","Gestión y estado comercial"){
            SolidButton("+ CLIENTE",{prepare(null)},enabled=data.plans.isNotEmpty())
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Pill(data.customers.size.toString()+" registrados")
            Pill(data.customers.count{data.overdue(it.id)}.toString()+" morosos",false)
        }
        Entry("Buscar nombre o IP",search,{search=it})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            SoftButton(if(overdueOnly)"Todos" else "Ver todos",{overdueOnly=false})
            SoftButton("Morosos",{overdueOnly=true})
        }
        if(data.plans.isEmpty())EmptyState("Primero crea un plan",
            "Los clientes necesitan un plan comercial. Abre la pestaña Planes para configurarlo.")
        else if(data.customers.isEmpty())EmptyState("Aún no hay clientes",
            "Tu base de datos está vacía. Crea el primero; los datos se guardan solo en este teléfono.","NUEVO CLIENTE",{prepare(null)})
        val customers=data.customers.filter{
            (!overdueOnly || data.overdue(it.id)) &&
            (it.name.contains(search,true)||it.ip.contains(search,true))
        }
        customers.forEach{c->
            Panel{
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){
                        T(c.name,16,text,FontWeight.SemiBold)
                        T(if(c.ip.isBlank())"Sin IP asignada" else c.ip,11,muted,monoText=true)
                    }
                    Pill(if(data.overdue(c.id))"MOROSO" else "AL DÍA",!data.overdue(c.id))
                }
                DividerLine()
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    T(data.plans.find{it.id==c.plan}?.name?:"Sin plan",12,muted)
                    T(if(c.suspended)"Estado local: suspendido" else "Estado local: activo",
                        11,if(c.suspended)amber else accent)
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    SoftButton("Editar",{prepare(c)})
                    SoftButton(if(c.suspended)"Reactivar" else "Suspender",{changeState=c})
                }
            }
        }
        if(customers.isEmpty() && data.customers.isNotEmpty())T("No hay clientes con estos filtros.",12,muted)
    }
    if(showEdit){
        Editor(if(edit==null)"Nuevo cliente" else "Editar cliente",{showEdit=false}){
            Entry("Nombre",name,{name=it})
            Entry("IP (opcional)",ip,{ip=it})
            T("PLAN CONTRATADO",10,muted,FontWeight.SemiBold,monoText=true)
            data.plans.forEach{p->
                Row(Modifier.fillMaxWidth().clickable{plan=p.id}.padding(vertical=5.dp),
                    horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    T(p.name,13,if(plan==p.id)accent else text)
                    if(plan==p.id)Pill("SELECCIONADO") else T(amount(p.cents),11,muted)
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                SoftButton("Cancelar",{showEdit=false})
                SolidButton("Guardar",{
                    perform{it.saveCustomer(edit?.id,name,ip,plan)}
                    showEdit=false
                },!busy&&name.isNotBlank()&&plan.isNotBlank())
            }
        }
    }
    changeState?.let{customer->
        Editor("Confirmar estado local",{changeState=null}){
            T("Cliente: "+customer.name,14,text,FontWeight.Medium)
            T("Esta acción solo cambia su estado administrativo en el teléfono. NO envía órdenes al MikroTik.",12,amber)
            Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
                SoftButton("Cancelar",{changeState=null})
                SolidButton("Confirmar",{
                    perform{it.suspend(customer)}
                    changeState=null
                },!busy)
            }
        }
    }
}
@Composable fun PlansScreen(data:Ledger,busy:Boolean,perform:((LocalStore)->Unit)->Unit){
    var show by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf<Plan?>(null)}
    var name by remember{mutableStateOf("")}
    var speed by remember{mutableStateOf("")}
    var price by remember{mutableStateOf("")}
    var profile by remember{mutableStateOf("")}
    fun edit(p:Plan?){
        editing=p;name=p?.name.orEmpty();speed=p?.speed.orEmpty()
        price=p?.let{BigDecimal.valueOf(it.cents,2).toPlainString()}.orEmpty()
        profile=p?.profile.orEmpty();show=true
    }
    ScreenScroll{
        PageHeading("Planes","Tarifas y perfiles RouterOS"){
            SolidButton("+ PLAN",{edit(null)})
        }
        if(data.plans.isEmpty())EmptyState("Sin planes configurados",
            "Crea un plan con velocidad y precio. El Profile RouterOS se guardará como referencia local.","CREAR PLAN",{edit(null)})
        data.plans.forEach{p->
            Panel{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,
                    verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        T(p.name,17,text,FontWeight.SemiBold)
                        T(p.speed,12,muted,monoText=true)
                    }
                    T(amount(p.cents),17,accent,FontWeight.SemiBold,monoText=true)
                }
                DividerLine()
                T("PROFILE  "+p.profile.ifBlank{"Sin vincular"},11,muted,monoText=true)
                T(data.customers.count{it.plan==p.id}.toString()+" clientes asociados",11,muted)
                SoftButton("Editar plan",{edit(p)})
            }
        }
    }
    if(show)Editor(if(editing==null)"Nuevo plan" else "Editar plan",{show=false}){
        Entry("Nombre del plan",name,{name=it})
        Entry("Bajada / subida (Mbps)",speed,{speed=it})
        Entry("Cuota mensual (CUP)",price,{price=it},numeric=true)
        Entry("Profile en RouterOS (referencia)",profile,{profile=it})
        T("La vinculación es administrativa; esta acción no crea ni modifica perfiles del router.",11,amber)
        Row(horizontalArrangement=Arrangement.spacedBy(9.dp)){
            SoftButton("Cancelar",{show=false})
            SolidButton("Guardar",{
                perform{it.savePlan(editing?.id,name,speed,parseMoney(price),profile)}
                show=false
            },!busy&&name.isNotBlank()&&speed.isNotBlank()&&price.isNotBlank())
        }
    }
}
@Composable fun PaymentsScreen(data:Ledger,busy:Boolean,perform:((LocalStore)->Unit)->Unit){
    val context=LocalContext.current
    var showCharge by remember{mutableStateOf(false)}
    var selectedCustomer by remember{mutableStateOf("")}
    var value by remember{mutableStateOf("")}
    var due by remember{mutableStateOf(LocalDate.now().plusDays(30).toString())}
    var bill by remember{mutableStateOf<Bill?>(null)}
    var method by remember{mutableStateOf("Efectivo")}
    var reference by remember{mutableStateOf("")}
    var reverse by remember{mutableStateOf<Payment?>(null)}
    ScreenScroll{
        PageHeading("Pagos","Cobranza y comprobantes manuales"){
            SolidButton("+ CARGO",{showCharge=true},data.customers.isNotEmpty())
        }
        val pending=data.bills.sumOf{data.balance(it)}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            Metric("Pendiente",amount(pending),"Saldo sin cobrar",Modifier.weight(1f),amber)
            Metric("Cargos",data.bills.size.toString(),"Registrados",Modifier.weight(1f))
        }
        T("FACTURAS PENDIENTES",11,muted,FontWeight.SemiBold,monoText=true)
        val unpaid=data.bills.filter{data.balance(it)>0}
        if(unpaid.isEmpty())EmptyState("Sin cuentas pendientes","Los cargos pendientes aparecerán aquí después de registrarlos.")
        unpaid.forEach{b->
            val c=data.customers.find{it.id==b.customer}
            Panel{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){
                        T(c?.name?:"Cliente desconocido",15,text,FontWeight.SemiBold)
                        T("Vence "+b.due,11,muted)
                    }
                    T(amount(data.balance(b)),15,amber,FontWeight.SemiBold,monoText=true)
                }
                SoftButton("Registrar cobro",{
                    bill=b;value=BigDecimal.valueOf(data.balance(b),2).toPlainString()
                    method="Efectivo";reference=""
                })
            }
        }
        T("HISTORIAL DE COBROS",11,muted,FontWeight.SemiBold,monoText=true)
        if(data.payments.isEmpty())T("No se han registrado cobros.",12,muted)
        data.payments.take(20).forEach{p->
            val b=data.bills.find{it.id==p.bill}
            val c=data.customers.find{it.id==b?.customer}
            Panel{
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){T(c?.name?:"Cliente",13,text,FontWeight.SemiBold);T(p.method+"  ·  "+p.at.take(10),11,muted)}
                    T(amount(p.cents),14,if(p.reversed)muted else accent,FontWeight.SemiBold,monoText=true)
                }
                if(p.reversed)Pill("ANULADO",false)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    SoftButton("Recibo",{
                        val receipt="NETPILOT · JsusOrg\nRECIBO DE REGISTRO MANUAL\nID: "+p.id+
                            "\nCliente: "+(c?.name?:"—")+"\nMonto: "+amount(p.cents)+
                            "\nMétodo: "+p.method+"\nReferencia: "+p.reference+
                            "\nFecha UTC: "+p.at+"\nEstado: "+(if(p.reversed)"ANULADO" else "REGISTRADO")+
                            "\nNo acredita verificación bancaria."
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{
                            type="text/plain";putExtra(Intent.EXTRA_TEXT,receipt)
                        },"Compartir recibo"))
                    })
                    if(!p.reversed)SoftButton("Anular",{reverse=p})
                }
            }
        }
    }
    if(showCharge)Editor("Nuevo cargo",{showCharge=false}){
        if(data.customers.isEmpty())T("Crea primero un cliente.",12,amber)
        data.customers.forEach{c->
            Row(Modifier.fillMaxWidth().clickable{
                selectedCustomer=c.id
                value=data.plans.find{it.id==c.plan}?.let{BigDecimal.valueOf(it.cents,2).toPlainString()}.orEmpty()
            }.padding(vertical=5.dp),horizontalArrangement=Arrangement.SpaceBetween){
                T(c.name,13,if(selectedCustomer==c.id)accent else text)
                if(selectedCustomer==c.id)Pill("SELECCIONADO")
            }
        }
        Entry("Importe (CUP)",value,{value=it},numeric=true)
        Entry("Vencimiento AAAA-MM-DD",due,{due=it})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            SoftButton("Cancelar",{showCharge=false})
            SolidButton("Crear cargo",{
                perform{it.charge(selectedCustomer,parseMoney(value),due)}
                showCharge=false
            },!busy&&selectedCustomer.isNotBlank()&&value.isNotBlank())
        }
    }
    bill?.let{current->
        Editor("Registrar cobro",{bill=null}){
            T("Saldo: "+amount(data.balance(current)),14,text,FontWeight.SemiBold)
            Entry("Importe recibido (CUP)",value,{value=it},numeric=true)
            T("MÉTODO",10,muted,FontWeight.SemiBold,monoText=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SoftButton("Efectivo"+if(method=="Efectivo")" ✓" else "",{method="Efectivo"})
                SoftButton("Transferencia"+if(method=="Transferencia")" ✓" else "",{method="Transferencia"})
            }
            Entry("Referencia de operación",reference,{reference=it})
            T("Registro manual; la APK no comprueba depósitos en bancos.",11,amber)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SoftButton("Cancelar",{bill=null})
                SolidButton("Confirmar",{
                    perform{it.pay(current.id,parseMoney(value),method,reference)}
                    bill=null
                },!busy&&value.isNotBlank()&&(method=="Efectivo"||reference.isNotBlank()))
            }
        }
    }
    reverse?.let{p->
        Editor("Anular registro",{reverse=null}){
            T("Se restaurará el saldo de "+amount(p.cents)+". Esta acción queda registrada como reverso.",12,amber)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                SoftButton("Cancelar",{reverse=null})
                SolidButton("Anular",{
                    perform{it.reverse(p)}
                    reverse=null
                },!busy)
            }
        }
    }
}
@Composable fun ToolsScreen(session:VolatileRouterSession,ledger:Ledger){
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var exportResult by remember{mutableStateOf("")}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null)scope.launch{
            exportResult=withContext(Dispatchers.IO){
                runCatching{
                    val json=org.json.JSONObject().put("format","netpilot-business-v1")
                        .put("currency","CUP").put("date",java.time.Instant.now().toString())
                    json.put("plans",org.json.JSONArray().apply{ledger.plans.forEach{
                        put(org.json.JSONObject().put("id",it.id).put("name",it.name).put("speed",it.speed)
                            .put("priceCents",it.cents).put("profile",it.profile))
                    }})
                    json.put("customers",org.json.JSONArray().apply{ledger.customers.forEach{
                        put(org.json.JSONObject().put("id",it.id).put("name",it.name).put("ip",it.ip)
                            .put("plan",it.plan).put("suspendedLocal",it.suspended))
                    }})
                    json.put("bills",org.json.JSONArray().apply{ledger.bills.forEach{
                        put(org.json.JSONObject().put("id",it.id).put("customer",it.customer)
                            .put("priceCents",it.cents).put("due",it.due))
                    }})
                    json.put("payments",org.json.JSONArray().apply{ledger.payments.forEach{
                        put(org.json.JSONObject().put("id",it.id).put("bill",it.bill)
                            .put("priceCents",it.cents).put("method",it.method).put("reference",it.reference)
                            .put("at",it.at).put("reversed",it.reversed))
                    }})
                    requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use{it.write(json.toString(2))}
                    "Exportación guardada; protege el archivo porque contiene datos comerciales."
                }.getOrElse{"Error al exportar"}
            }
        }
    }
    ScreenScroll{
        PageHeading("Herramientas","RouterOS, red local y seguridad")
        RouterConnectionScreen(session)
        val s=session.snapshot
        if(s!=null){
            Panel{
                T("INVENTARIO DE ROUTEROS",13,text,FontWeight.SemiBold)
                T("Las secciones son lecturas reales de la API. La disponibilidad depende de permisos y versión del router.",11,muted)
            }
            var expanded by remember{mutableStateOf("")}
            s.sections.forEach{section->
                Panel{
                    Row(Modifier.fillMaxWidth().clickable{expanded=if(expanded==section.title)"" else section.title},
                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
                        T(section.title,14,text,FontWeight.Medium)
                        Pill(section.records.size.toString()+" registros",section.error==null)
                    }
                    section.error?.let{T("No disponible: "+it,11,amber)}
                    if(expanded==section.title){
                        section.records.take(35).forEachIndexed{idx,item->
                            DividerLine()
                            T((idx+1).toString()+" · "+item.entries.joinToString("  /  "){it.key+": "+it.value},11,muted,monoText=true)
                        }
                        if(section.records.size>35)T("Se muestran los primeros 35 registros.",11,amber)
                    }
                }
            }
        }
        Panel{
            T("RESPALDO COMERCIAL",14,text,FontWeight.SemiBold)
            T("Exporta planes, clientes, cargos y cobros a JSON. No contiene contraseñas de RouterOS.",12,muted)
            SolidButton("Exportar datos locales",{export.launch("NETPILOT-"+LocalDate.now()+".json")})
            if(exportResult.isNotBlank())T(exportResult,11,accent)
        }
        Panel{
            T("RESPALDO DE ROUTEROS",14,text,FontWeight.SemiBold)
            T("No disponible en modo lectura. Crear un respaldo en el MikroTik requiere una acción privilegiada separada y confirmación explícita. Nunca lo ejecutaremos en segundo plano.",12,muted)
        }
    }
}
