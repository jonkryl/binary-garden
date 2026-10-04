package com.jonkryl.binarygarden

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.junit.Assert.*
import org.junit.Test

class LargeFontTest {
    @Test fun realLargeFontKeepsActionsReachableAndGridDescriptionsPresent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity {a ->
                assertTrue(a.resources.configuration.fontScale>=1.9f)
                for(i in 0 until 36) {
                    val v=a.findViewById<android.view.View>(a.resources.getIdentifier("cell_$i","id",a.packageName))
                    assertNotNull(v);assertFalse(v.contentDescription.isNullOrBlank())
                }
            }
            onView(withId(R.id.rules)).perform(scrollTo(),click())
            onView(androidx.test.espresso.matcher.ViewMatchers.withText(android.R.string.ok)).perform(click())
            onView(withId(R.id.more)).perform(scrollTo(),click())
        }
    }
}
