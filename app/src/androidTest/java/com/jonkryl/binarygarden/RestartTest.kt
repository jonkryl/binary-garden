package com.jonkryl.binarygarden

import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jonkryl.binarygarden.core.GameState
import org.junit.Assert.*
import org.junit.Test

class RestartTest {
    @Test fun freshApplicationProcessReadsTheExactSavedGame() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val expected=c.getSharedPreferences("journey_receipt",0).getString("saved",null)?:error("Prior journey proof missing")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { a ->
                val actual=c.getSharedPreferences("garden",0).getString("game_normal",null)!!
                assertEquals(expected,actual)
                val state=GameState.decode(actual)!!
                for(i in state.cells.indices) {
                    val id=c.resources.getIdentifier("cell_$i","id",c.packageName)
                    assertEquals(if(state.cells[i]<0)"·"else state.cells[i].toString(),a.findViewById<Button>(id).text.toString())
                }
            }
        }
    }
}
