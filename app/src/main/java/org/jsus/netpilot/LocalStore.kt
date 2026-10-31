package org.jsus.netpilot

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class Plan(val id: String, val name: String, val speed: String, val cents: Long, val profile: String)
data class Customer(val id: String, val name: String, val ip: String, val plan: String, val suspended: Boolean)
data class Bill(val id: String, val customer: String, val cents: Long, val due: String)
data class Payment(val id: String, val bill: String, val cents: Long, val method: String, val reference: String, val at: String, val reversed: Boolean)
data class Ledger(val plans: List<Plan> = emptyList(), val customers: List<Customer> = emptyList(), val bills: List<Bill> = emptyList(), val payments: List<Payment> = emptyList()) {
    fun balance(bill: Bill): Long = bill.cents - payments.filter { it.bill == bill.id && !it.reversed }.sumOf { it.cents }
    fun overdue(customer: String): Boolean = bills.any { it.customer == customer && it.due < LocalDate.now().toString() && balance(it) > 0 }
}
fun parseMoney(text: String): Long {
    val value = text.trim().replace(',', '.')
    require(Regex("[0-9]{1,9}(\\.[0-9]{1,2})?").matches(value)) { "Importe inválido: usa hasta dos decimales." }
    return BigDecimal(value).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact().also { require(it > 0) { "El importe debe ser positivo." } }
}
fun money(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString() + " CUP"

/** Business records only. Router credentials must never enter this database. */
class LocalStore(context: Context, name: String = "netpilot-examples.db") : SQLiteOpenHelper(context, name, null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE plans(id TEXT PRIMARY KEY,name TEXT NOT NULL,speed TEXT NOT NULL,cents INTEGER NOT NULL CHECK(cents>0),profile TEXT NOT NULL)")
        db.execSQL("CREATE TABLE customers(id TEXT PRIMARY KEY,name TEXT NOT NULL,ip TEXT NOT NULL,plan TEXT NOT NULL REFERENCES plans(id),suspended INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE bills(id TEXT PRIMARY KEY,customer TEXT NOT NULL REFERENCES customers(id),cents INTEGER NOT NULL CHECK(cents>0),due TEXT NOT NULL)")
        db.execSQL("CREATE TABLE payments(id TEXT PRIMARY KEY,bill TEXT NOT NULL REFERENCES bills(id),cents INTEGER NOT NULL CHECK(cents>0),method TEXT NOT NULL,reference TEXT NOT NULL,at TEXT NOT NULL,reversed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE outbox(id TEXT PRIMARY KEY,kind TEXT NOT NULL,entity TEXT NOT NULL,at TEXT NOT NULL)")
        db.execSQL("INSERT INTO plans VALUES('p1','Residencial','20 / 5 Mbps',150000,'residencial-20'),('p2','Negocio','50 / 10 Mbps',300000,'negocio-50')")
        db.execSQL("INSERT INTO customers VALUES('c1','Ana Pérez','10.10.0.12','p1',0),('c2','Luis Díaz','10.10.0.18','p1',0),('c3','Taller Norte','10.10.0.25','p2',0)")
        val due = LocalDate.now().minusDays(5).toString()
        db.execSQL("INSERT INTO bills VALUES('b1','c1',150000,?),('b2','c2',150000,?),('b3','c3',300000,?)", arrayOf(due,due,due))
        db.execSQL("INSERT INTO payments VALUES('sample-payment','b1',150000,'Efectivo','MUESTRA',?,0)", arrayOf(java.time.Instant.now().toString()))
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Migración no definida") }
    fun read(): Ledger {
        val db = readableDatabase
        val plans = db.rawQuery("SELECT * FROM plans ORDER BY name", null).use { c -> buildList { while(c.moveToNext()) add(Plan(c.getString(0),c.getString(1),c.getString(2),c.getLong(3),c.getString(4))) } }
        val customers = db.rawQuery("SELECT * FROM customers ORDER BY name", null).use { c -> buildList { while(c.moveToNext()) add(Customer(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)==1)) } }
        val bills = db.rawQuery("SELECT * FROM bills ORDER BY due", null).use { c -> buildList { while(c.moveToNext()) add(Bill(c.getString(0),c.getString(1),c.getLong(2),c.getString(3))) } }
        val payments = db.rawQuery("SELECT * FROM payments ORDER BY at DESC", null).use { c -> buildList { while(c.moveToNext()) add(Payment(c.getString(0),c.getString(1),c.getLong(2),c.getString(3),c.getString(4),c.getString(5),c.getInt(6)==1)) } }
        return Ledger(plans,customers,bills,payments)
    }
    private fun transaction(kind: String, id: String, action: (SQLiteDatabase) -> Unit) {
        val db=writableDatabase
        db.beginTransaction()
        try {
            action(db)
            db.execSQL("INSERT INTO outbox VALUES(?,?,?,?)", arrayOf(UUID.randomUUID().toString(),kind,id,java.time.Instant.now().toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun savePlan(id: String?, name: String, speed: String, cents: Long, profile: String) {
        require(name.isNotBlank() && speed.isNotBlank() && cents>0) { "Completa nombre, velocidad y precio." }
        val key=id?:UUID.randomUUID().toString()
        transaction("plan.saved",key) { db ->
            val v=ContentValues().apply { put("id",key);put("name",name.trim());put("speed",speed.trim());put("cents",cents);put("profile",profile.trim()) }
            if(id==null) db.insertOrThrow("plans",null,v) else require(db.update("plans",v,"id=?",arrayOf(id))==1)
        }
    }
    fun saveCustomer(id: String?, name: String, ip: String, plan: String) {
        require(name.isNotBlank()) { "El nombre es obligatorio." }
        require(ip.isBlank() || (ip.split('.').size==4 && ip.split('.').all { it.toIntOrNull() in 0..255 })) { "IP IPv4 inválida." }
        val key=id?:UUID.randomUUID().toString()
        transaction("customer.saved",key) { db ->
            val v=ContentValues().apply { put("name",name.trim());put("ip",ip.trim());put("plan",plan) }
            if(id==null) { v.put("id",key); db.insertOrThrow("customers",null,v) } else require(db.update("customers",v,"id=?",arrayOf(id))==1)
        }
    }
    fun suspend(customer: Customer) = transaction("service.simulated",customer.id) { db -> db.execSQL("UPDATE customers SET suspended=? WHERE id=?", arrayOf(if(customer.suspended) 0 else 1, customer.id)) }
    fun charge(customer: String, cents: Long, due: String) {
        require(cents>0); LocalDate.parse(due)
        val id=UUID.randomUUID().toString()
        transaction("bill.created",id) { it.execSQL("INSERT INTO bills VALUES(?,?,?,?)",arrayOf(id,customer,cents,due)) }
    }
    fun pay(bill: String, cents: Long, method: String, reference: String) {
        require(cents>0 && method in listOf("Efectivo","Transferencia"))
        require(method!="Transferencia" || reference.isNotBlank()) { "Introduce la referencia de transferencia." }
        val id=UUID.randomUUID().toString()
        transaction("payment.created",id) { db ->
            val ledger=read(); val invoice=ledger.bills.single { it.id==bill }
            require(cents<=ledger.balance(invoice)) { "El importe supera el saldo pendiente." }
            db.execSQL("INSERT INTO payments VALUES(?,?,?,?,?,?,0)",arrayOf(id,bill,cents,method,reference.trim(),java.time.Instant.now().toString()))
        }
    }
    fun reverse(payment: Payment) = transaction("payment.reversed",payment.id) { db -> require(db.update("payments",ContentValues().apply { put("reversed",1) },"id=? AND reversed=0",arrayOf(payment.id))==1) { "Pago ya anulado." } }
}
