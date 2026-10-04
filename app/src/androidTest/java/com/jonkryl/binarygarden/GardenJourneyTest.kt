package com.jonkryl.binarygarden

import android.content.Context
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.jonkryl.binarygarden.core.GameState
import org.junit.Assert.*
import org.junit.Test

class GardenJourneyTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun ready() {
        val deadline=System.currentTimeMillis()+30000
        while(System.currentTimeMillis()<deadline) {
            val text=context.getSharedPreferences("garden",Context.MODE_PRIVATE).getString("game_normal",null)
            if(text!=null && GameState.decode(text)!=null)return
            Thread.sleep(100)
        }
        error("No generated puzzle saved")
    }
    @Test fun editableCellCyclesAndUndoRestoresEveryChange() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ready()
            val before=GameState.decode(context.getSharedPreferences("garden",0).getString("game_normal",null)!!)!!
            val index=before.puzzle.clues.indexOf(-1);val id=context.resources.getIdentifier("cell_$index","id",context.packageName)
            onView(withId(id)).perform(click())
            var state=GameState.decode(context.getSharedPreferences("garden",0).getString("game_normal",null)!!)!!;assertEquals(0,state.cells[index])
            onView(withId(id)).perform(click());state=GameState.decode(context.getSharedPreferences("garden",0).getString("game_normal",null)!!)!!;assertEquals(1,state.cells[index])
            onView(withId(R.id.undo)).perform(scrollTo(),click());state=GameState.decode(context.getSharedPreferences("garden",0).getString("game_normal",null)!!)!!;assertEquals(0,state.cells[index])
            // Leave one real persisted move for a separate instrumentation process after force-stop.
            context.getSharedPreferences("journey_receipt",0).edit().putString("saved",state.encode()).commit()
            scenario.onActivity { a -> assertEquals("0",a.findViewById<Button>(id).text.toString()) }
        }
    }
    @Test fun rulesAndBothPrivacyChoicesKeepAdvertisingContainer() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            ready();onView(withId(R.id.rules)).perform(scrollTo(),click())
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(android.R.string.ok)).perform(click())
            for(choice in listOf(true,false)) {
                onView(withId(R.id.more)).perform(scrollTo(),click())
                onView(androidx.test.espresso.matcher.ViewMatchers.withText(context.getString(R.string.ad_privacy_title))).perform(click())
                onView(androidx.test.espresso.matcher.ViewMatchers.withText(context.getString(if(choice)R.string.ad_allow_personalization else R.string.ad_contextual))).perform(click())
                assertEquals(choice,context.getSharedPreferences("ad_privacy",0).getBoolean("personalized",!choice))
            }
            scenario.onActivity {a -> assertEquals(android.view.View.VISIBLE,a.findViewById<android.view.View>(R.id.ad_host).visibility)}
        }
    }
}
