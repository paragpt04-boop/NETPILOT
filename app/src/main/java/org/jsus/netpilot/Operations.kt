package org.jsus.netpilot

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable private fun Field(title: String, value: String, change: (String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Label(title,muted)
        BasicTextField(value,change,Modifier.fillMaxWidth().border(1.dp,muted).padding(12.dp),singleLine=true,textStyle=TextStyle(color=green,fontFamily=FontFamily.Monospace,fontSize=16.sp),cursorBrush=SolidColor(orange))
    }
}
@Composable fun Operations(page: Int, ledger: Ledger, busy: Boolean, perform: ((LocalStore)->Unit)->Unit) {
    when(page) {
        1 -> CustomerScreen(ledger,busy,perform)
        2 -> PlanScreen(ledger,busy,perform)
        3 -> PaymentScreen(ledger,busy,perform)
        4 -> ToolScreen(ledger)
    }
}
@Composable private fun CustomerScreen(data: Ledger, busy: Boolean, perform: ((LocalStore)->Unit)->Unit) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var id by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }; var ip by remember { mutableStateOf("") }; var plan by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<Customer?>(null) }
    LaunchedEffect(data) { editing=false; confirm=null }
    Label("CLIENTES",size=26)
    Field("Buscar cliente",query) { query=it }
    Action("NUEVO CLIENTE") { id=null;name="";ip="";plan=data.plans.firstOrNull()?.id.orEmpty();editing=true }
    if(editing) {
        Label(if(id==null) "ALTA DE CLIENTE" else "EDITAR CLIENTE",orange)
        Field("Nombre",name) { name=it }; Field("IP del cliente (opcional)",ip) { ip=it }
        data.plans.forEach { p -> Action((if(plan==p.id) "[X] " else "[ ] ")+p.name) { plan=p.id } }
        Action("GUARDAR CLIENTE") { if(!busy) perform { it.saveCustomer(id,name,ip,plan) } }
        Action("CERRAR FORMULARIO") { editing=false }
        Rule()
    }
    confirm?.let { c ->
        Label("CONFIRMAR / ${c.name}",orange)
        Label("Cambiará solo el estado simulado. No afecta al MikroTik ni al saldo.",muted)
        Action("CONFIRMAR ESTADO LOCAL") { if(!busy) { perform { it.suspend(c) };confirm=null } }
        Action("CANCELAR") { confirm=null }
    }
    val customers=data.customers.filter { it.name.contains(query,true)||it.ip.contains(query) }
    if(customers.isEmpty()) Label("Sin coincidencias",muted)
    customers.forEach { c ->
        Rule();Label(c.name,size=19)
        Label("${c.ip.ifBlank { "SIN IP" }} / ${data.plans.find { it.id==c.plan }?.name}",muted)
        Label(if(data.overdue(c.id)) "MOROSO" else "AL DÍA",if(data.overdue(c.id)) orange else green)
        Label(if(c.suspended) "SERVICIO SIMULADO: SUSPENDIDO" else "SERVICIO SIMULADO: ACTIVO",muted)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Action("EDITAR") { id=c.id;name=c.name;ip=c.ip;plan=c.plan;editing=true }
            Action(if(c.suspended) "REACTIVAR…" else "SUSPENDER…") { confirm=c }
        }
    }
}
@Composable private fun PlanScreen(data: Ledger,busy:Boolean,perform:((LocalStore)->Unit)->Unit) {
    var editing by remember { mutableStateOf(false) };var id by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") };var speed by remember { mutableStateOf("") };var price by remember { mutableStateOf("") };var profile by remember { mutableStateOf("") }
    LaunchedEffect(data) { editing=false }
    Label("PLANES",size=26)
    Label("Moneda de trabajo: CUP · perfiles sin sincronizar",muted)
    Action("NUEVO PLAN") { id=null;name="";speed="";price="";profile="";editing=true }
    if(editing) {
        Field("Nombre del plan",name){name=it};Field("Velocidad (bajada / subida)",speed){speed=it};Field("Precio CUP",price){price=it};Field("Profile RouterOS (referencia)",profile){profile=it}
        Action("GUARDAR PLAN") { if(!busy) perform { it.savePlan(id,name,speed,parseMoney(price),profile) } }
        Action("CERRAR FORMULARIO") { editing=false }
    }
    data.plans.forEach { p ->
        Rule();Label(p.name,size=20);Label(p.speed);Label(money(p.cents),orange,22);Label("PROFILE / ${p.profile.ifBlank { "SIN ASIGNAR" }}",muted)
        Action("EDITAR ${p.name}") { id=p.id;name=p.name;speed=p.speed;price=java.math.BigDecimal.valueOf(p.cents,2).toPlainString();profile=p.profile;editing=true }
    }
}
@Composable private fun PaymentScreen(data: Ledger,busy:Boolean,perform:((LocalStore)->Unit)->Unit) {
    val context=LocalContext.current
    var selected by remember { mutableStateOf<String?>(null) };var amount by remember { mutableStateOf("") };var method by remember { mutableStateOf("Efectivo") };var reference by remember { mutableStateOf("") }
    var confirmPay by remember { mutableStateOf(false) };var reversal by remember { mutableStateOf<Payment?>(null) }
    var customer by remember { mutableStateOf<String?>(null) };var due by remember { mutableStateOf(LocalDate.now().plusDays(30).toString()) };var charge by remember { mutableStateOf("") }
    var newBill by remember { mutableStateOf(false) }
    LaunchedEffect(data) { selected=null;newBill=false;confirmPay=false }
    Label("PAGOS",size=26)
    Label("PENDIENTE / ${money(data.bills.sumOf { data.balance(it) })}",orange,18)
    Label("Cobros manuales · entorno con datos de ejemplo",muted)
    Action("GENERAR CARGO") { newBill=!newBill }
    if(newBill) {
        data.customers.forEach { c -> Action((if(customer==c.id) "[X] " else "[ ] ")+c.name) { customer=c.id; charge=java.math.BigDecimal.valueOf(data.plans.single { it.id==c.plan }.cents,2).toPlainString() } }
        Field("Importe del cargo CUP",charge){charge=it};Field("Vencimiento AAAA-MM-DD",due){due=it}
        Action("CONFIRMAR CARGO") { if(!busy) perform { it.charge(requireNotNull(customer) { "Selecciona un cliente." },parseMoney(charge),due) } }
    }
    selected?.let { billId ->
        Label("REGISTRAR COBRO",orange)
        Field("Importe recibido CUP",amount){amount=it;confirmPay=false}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("Efectivo","Transferencia").forEach { m -> Action((if(method==m) "[X] " else "")+m) { method=m;confirmPay=false } } }
        Field("Referencia",reference){reference=it;confirmPay=false}
        if(confirmPay) {
            Label("Confirmar $amount CUP · $method",orange)
            Action("CONFIRMAR COBRO") { if(!busy) { perform { it.pay(billId,parseMoney(amount),method,reference) };confirmPay=false } }
        } else Action("REVISAR COBRO") { confirmPay=true }
        Action("CERRAR COBRO") { selected=null }
    }
    data.bills.filter { data.balance(it)>0 }.forEach { b ->
        Rule();Label(data.customers.single { it.id==b.customer }.name,size=18)
        Label("Vence ${b.due} / ${money(data.balance(b))}",orange)
        Action("COBRAR ${b.id.take(8)}") { selected=b.id;amount=java.math.BigDecimal.valueOf(data.balance(b),2).toPlainString();reference="";confirmPay=false }
    }
    Rule();Label("HISTORIAL DE COBROS",size=18)
    reversal?.let { p -> Label("¿Anular ${money(p.cents)}? El saldo se restaurará.",orange);Action("CONFIRMAR ANULACIÓN") { if(!busy) {perform { it.reverse(p) };reversal=null} };Action("CANCELAR ANULACIÓN"){reversal=null} }
    data.payments.forEach { p ->
        val b=data.bills.single { it.id==p.bill };val c=data.customers.single { it.id==b.customer }
        Rule();Label(c.name);Label("${money(p.cents)} / ${p.method}",if(p.reversed) muted else green)
        Label(if(p.reversed) "ANULADO" else p.at,muted,11)
        Action("COMPARTIR RECIBO ${p.id.take(8)}") {
            val receipt="JsusOrg · NETPILOT\nRECIBO / DATOS DE EJEMPLO\nID: ${p.id}\nCliente: ${c.name}\nCargo: ${b.id}\nImporte: ${money(p.cents)}\nMétodo: ${p.method}\nReferencia: ${p.reference}\nFecha UTC: ${p.at}\nEstado: ${if(p.reversed) "ANULADO" else "REGISTRADO"}\nRegistro manual, no verificación bancaria."
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,receipt) },"Compartir recibo"))
        }
        if(!p.reversed) Action("ANULAR ${p.id.take(8)}") { reversal=p }
    }
}
@Composable private fun ToolScreen(data: Ledger) {
    val context=LocalContext.current; val scope=rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri!=null) scope.launch {
            status=withContext(Dispatchers.IO) {
                runCatching {
                    val root=org.json.JSONObject().put("format","netpilot-local-export-v1").put("exampleData",true).put("currency","CUP")
                    root.put("customers",org.json.JSONArray().apply { data.customers.forEach { put(org.json.JSONObject().put("id",it.id).put("name",it.name).put("ip",it.ip).put("plan",it.plan).put("suspended",it.suspended)) } })
                    root.put("plans",org.json.JSONArray().apply { data.plans.forEach { put(org.json.JSONObject().put("id",it.id).put("name",it.name).put("speed",it.speed).put("cents",it.cents).put("profile",it.profile)) } })
                    root.put("bills",org.json.JSONArray().apply { data.bills.forEach { put(org.json.JSONObject().put("id",it.id).put("customer",it.customer).put("cents",it.cents).put("due",it.due)) } })
                    root.put("payments",org.json.JSONArray().apply { data.payments.forEach { put(org.json.JSONObject().put("id",it.id).put("bill",it.bill).put("cents",it.cents).put("method",it.method).put("reference",it.reference).put("at",it.at).put("reversed",it.reversed)) } })
                    requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(root.toString(2)) }
                    "Exportación guardada. Contiene datos comerciales; guárdala en un lugar privado."
                }.getOrElse { "No se pudo exportar: ${it.message}" }
            }
        }
    }
    Label("HERRAMIENTAS",size=24)
    Label("OPERACIÓN LOCAL",orange)
    Label("${data.customers.size} clientes / ${data.plans.size} planes / ${data.payments.size} cobros",muted)
    Action("EXPORTAR DATOS LOCALES") { export.launch("netpilot-${LocalDate.now()}.json") }
    if(status.isNotEmpty()) Label(status)
    Rule();Label("MIKROTIK / SIN INTEGRAR",orange)
    Label("Escaneo LAN, logs y respaldo del router requieren la integración real. No se solicitan credenciales en esta versión.",muted)
    Rule();Label("DIAGNÓSTICO LOCAL")
    Label("SQLite: disponible\nAcceso de red: deshabilitado\nSincronización VPS: deshabilitada\nMarca: JsusOrg",muted)
}
