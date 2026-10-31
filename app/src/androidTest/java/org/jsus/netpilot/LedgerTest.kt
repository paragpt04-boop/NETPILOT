package org.jsus.netpilot

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LedgerTest {
    @Test fun paymentsPersistAndReverseWithoutOverpayment() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="ledger-${java.util.UUID.randomUUID()}.db"
        val store=LocalStore(context,name)
        try {
            val before=store.read();val bill=before.bills.single { it.id=="b2" }
            assertEquals(150000L,before.balance(bill))
            store.pay(bill.id,50000,"Efectivo","")
            assertEquals(100000L,store.read().balance(bill))
            assertThrows(IllegalArgumentException::class.java) { store.pay(bill.id,100001,"Efectivo","") }
            assertThrows(IllegalArgumentException::class.java) { store.pay(bill.id,1,"Transferencia","") }
            store.close()
            LocalStore(context,name).use { reopened ->
                assertEquals(100000L,reopened.read().balance(bill))
                val payment=reopened.read().payments.first { it.bill==bill.id }
                reopened.reverse(payment)
                assertEquals(150000L,reopened.read().balance(bill))
                assertThrows(IllegalArgumentException::class.java) { reopened.reverse(payment) }
                assertTrue(reopened.read().overdue("c2"))
            }
            assertEquals(12345L,parseMoney("123,45"))
            assertThrows(IllegalArgumentException::class.java) { parseMoney("0.001") }
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
