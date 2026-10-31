package org.jsus.netpilot

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class LedgerTest {
    @Test fun newInstallIsEmptyAndPaymentsPersist(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="ledger-"+UUID.randomUUID()+".db"
        val store=LocalStore(context,name)
        try{
            assertTrue(store.read().customers.isEmpty())
            assertTrue(store.read().bills.isEmpty())
            store.savePlan(null,"Profesional","30 / 10 Mbps",150000,"ppp-30")
            val plan=store.read().plans.single()
            store.saveCustomer(null,"Cliente real","192.168.50.10",plan.id)
            val customer=store.read().customers.single()
            store.charge(customer.id,150000,LocalDate.now().minusDays(2).toString())
            val bill=store.read().bills.single()
            assertTrue(store.read().overdue(customer.id))
            store.pay(bill.id,50000,"Efectivo","")
            assertEquals(100000L,store.read().balance(bill))
            assertThrows(IllegalArgumentException::class.java){
                store.pay(bill.id,100001,"Efectivo","")
            }
            store.close()
            LocalStore(context,name).use{reopened->
                assertEquals(100000L,reopened.read().balance(bill))
                val pay=reopened.read().payments.single()
                reopened.reverse(pay)
                assertEquals(150000L,reopened.read().balance(bill))
            }
            assertEquals(12345L,parseMoney("123,45"))
        }finally{store.close();context.deleteDatabase(name)}
    }
}
