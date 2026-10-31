package org.jsus.netpilot

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()

    @Test fun navigationAndNoFabricatedRouterData(){
        ui.waitUntil(15000){
            ui.onAllNodesWithText("Centro de operaciones").fetchSemanticsNodes().isNotEmpty()
        }
        ui.onNodeWithText("Conecta tu primer MikroTik").assertExists()
        ui.onNodeWithText("Clientes").performClick()
        ui.onNodeWithText("Primero crea un plan").assertExists()
        ui.onNodeWithText("Planes").performClick()
        ui.onNodeWithText("Sin planes configurados").assertExists()
        ui.onNodeWithText("Pagos").performClick()
        ui.onNodeWithText("Sin cuentas pendientes").assertExists()
        ui.onNodeWithText("Herramientas").performClick()
        ui.onNodeWithText("CONEXIÓN MIKROTIK").assertExists()
    }
    @Test fun jsusOrgBrandOnReentry(){
        ui.waitUntil(15000){
            ui.onAllNodesWithText("Centro de operaciones").fetchSemanticsNodes().isNotEmpty()
        }
        ui.mainClock.autoAdvance=false
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText("JsusOrg").assertExists()
        ui.onNodeWithText("J/O").assertExists()
        ui.mainClock.advanceTimeBy(1300)
        ui.onNodeWithText("Centro de operaciones").assertExists()
    }
}
