package org.jsus.netpilot

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class DemoTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test fun navigationAndSafeActions() {
        ui.waitUntil(10000) { ui.onAllNodesWithText("CENTRO DE OPERACIONES").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("DATOS DE EJEMPLO / SIN CONEXIÓN", substring = true).assertExists()
        ui.onNodeWithText("Clientes").performClick()
        ui.onNodeWithText("Ana Pérez").assertExists()
        ui.onAllNodesWithText("SUSPENDER…")[0].performScrollTo().performClick()
        ui.onNodeWithText("CANCELAR").performScrollTo().performClick()
        ui.onNodeWithText("Planes").performClick()
        ui.onNodeWithText("NUEVO PLAN").assertExists()
        ui.onNodeWithText("Pagos").performClick()
        ui.onNodeWithText("GENERAR CARGO").assertExists()
        ui.onNodeWithText("Tools").performClick()
        ui.onNodeWithText("EXPORTAR DATOS LOCALES").assertExists()
        ui.onNodeWithText("Panel").performClick()
        ui.onNodeWithText("CENTRO DE OPERACIONES").assertExists()
    }

    @Test fun brandingOnReentry() {
        ui.waitUntil(10000) { ui.onAllNodesWithText("CENTRO DE OPERACIONES").fetchSemanticsNodes().isNotEmpty() }
        ui.mainClock.autoAdvance = false
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        ui.mainClock.advanceTimeByFrame()
        ui.onNodeWithText("JsusOrg").assertExists()
        ui.onNodeWithText("[ J / O ]").assertExists()
        ui.mainClock.advanceTimeBy(1500)
        ui.onNodeWithText("CENTRO DE OPERACIONES").assertExists()
    }
}
