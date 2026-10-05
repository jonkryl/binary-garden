package com.jonkryl.binarygarden

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.util.Xml
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.jonkryl.binarygarden.core.GameState
import com.jonkryl.binarygarden.core.Logic
import com.jonkryl.binarygarden.core.Puzzle
import com.jonkryl.binarygarden.core.PuzzleGenerator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Controlled commit failures, not physical disk-full or advertising SDK coverage. */
class SaveFailureJourneyTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("garden", Context.MODE_PRIVATE)
    private val diskFile get() = File(context.applicationInfo.dataDir, "shared_prefs/garden.xml")

    @Before fun requireOwnedFreshDemoEmulatorBeforeAnyFixture() {
        val arguments = InstrumentationRegistry.getArguments()
        assertEquals("true", arguments.getString("ownedBinarySaveWarning"))
        assertEquals("true", arguments.getString("ownedEmulatorFresh"))
        assertEquals("com.jonkryl.binarygarden", context.packageName)
        assertTrue(BuildConfig.DEBUG)
        assertEquals("demo-banner-yandex", BuildConfig.YANDEX_BANNER_ID)
        assertEquals(36, Build.VERSION.SDK_INT)
        assertTrue(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assertTrue(Build.FINGERPRINT.contains("emu") || Build.FINGERPRINT.contains("generic"))
    }

    private fun seed(game: GameState, daily: GameState? = null) {
        val editor = prefs.edit().clear().putBoolean("daily_selected", false)
            .putString("game_normal", game.encode())
        if (daily != null) editor.putString("game_daily", daily.encode())
        assertTrue(editor.commit())
        assertEquals(game.encode(), durableValue("game_normal"))
    }

    private fun state(activity: MainActivity): GameState = MainActivity::class.java.getDeclaredField("game")
        .apply { isAccessible = true }.get(activity) as GameState

    private fun controlCommits(activity: MainActivity): ControlledPreferences {
        val controlled = ControlledPreferences(prefs)
        // Test-only substitution of the existing preferences delegate; production has no failure hook.
        val field = MainActivity::class.java.getDeclaredField("prefs\$delegate").apply { isAccessible = true }
        field.set(activity, lazyOf(controlled))
        assertSame(controlled, (field.get(activity) as Lazy<*>).value)
        return controlled
    }

    private fun tapCell(activity: MainActivity, index: Int) {
        val id = context.resources.getIdentifier("cell_$index", "id", context.packageName)
        val cell = activity.findViewById<Button>(id)
        assertTrue(cell.isEnabled)
        assertTrue(cell.performClick())
    }

    private fun assertStatus(activity: MainActivity, message: Int) {
        assertEquals(activity.getString(message), activity.findViewById<TextView>(R.id.status).text.toString())
    }

    private fun durableValue(name: String): String? {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(diskFile.readBytes()), "UTF-8")
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.getAttributeValue(null, "name") == name)
                return if (parser.name == "string") parser.nextText() else parser.getAttributeValue(null, "value")
        }
        return null
    }

    @Test fun failedMoveSurvivesCheckHintAndUndoUntilSuccessfulSave() {
        val initial = GameState(PuzzleGenerator.generate(6, 8))
        val index = initial.puzzle.clues.indices.first { initial.cells[it] == -1 && initial.puzzle.solution[it] == 1 }
        seed(initial)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val diskBefore = diskFile.readBytes()
                val controlled = controlCommits(activity)
                tapCell(activity, index)
                assertEquals(0, state(activity).cells[index])
                assertEquals(1, state(activity).history.size)
                assertStatus(activity, R.string.save_error)
                activity.findViewById<Button>(R.id.check).performClick()
                assertStatus(activity, R.string.save_error)
                activity.findViewById<Button>(R.id.hint).performClick()
                assertStatus(activity, R.string.save_error)
                activity.findViewById<Button>(R.id.undo).performClick()
                assertEquals(-1, state(activity).cells[index])
                assertTrue(state(activity).history.isEmpty())
                assertStatus(activity, R.string.save_error)
                assertEquals(2, controlled.attempts.size)
                assertArrayEquals(diskBefore, diskFile.readBytes())
                assertEquals(initial.encode(), prefs.getString("game_normal", null))

                controlled.failCommits = false
                tapCell(activity, index)
                val latest = state(activity).encode()
                assertEquals(latest, durableValue("game_normal"))
                assertEquals(latest, prefs.getString("game_normal", null))
                assertEquals(2, GameState.decode(durableValue("game_normal")!!)!!.moves)
                assertStatus(activity, R.string.tap_help)
            }
        }
    }

    @Test fun failedHintKeepsAppliedStepAndDurableBytesUntilUndoRecovers() {
        val initial = GameState(PuzzleGenerator.generate(6, 8))
        val step = Logic.next(initial.cells, initial.puzzle.size)!!
        seed(initial)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var controlled: ControlledPreferences
            lateinit var diskBefore: ByteArray
            scenario.onActivity { activity ->
                diskBefore = diskFile.readBytes()
                controlled = controlCommits(activity)
                activity.findViewById<Button>(R.id.hint).performClick()
            }
            onView(withText(R.string.apply_hint)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                assertEquals(step.value, state(activity).cells[step.index])
                assertEquals(1, state(activity).hints)
                assertStatus(activity, R.string.save_error)
                assertArrayEquals(diskBefore, diskFile.readBytes())
                assertEquals(initial.encode(), durableValue("game_normal"))

                controlled.failCommits = false
                activity.findViewById<Button>(R.id.undo).performClick()
                val restored = GameState.decode(durableValue("game_normal")!!)!!
                assertEquals(-1, restored.cells[step.index])
                assertEquals(1, restored.hints)
                assertTrue(restored.history.isEmpty())
                assertEquals(state(activity).encode(), restored.encode())
                assertStatus(activity, R.string.tap_help)
            }
        }
    }

    @Test fun failedWinRecoversGameAndStatisticsOnceOnStop() {
        val solution = PuzzleGenerator.generate(6, 8).solution
        val index = solution.indexOf(0)
        val clues = solution.toMutableList().apply { this[index] = -1 }
        val initial = GameState(Puzzle(6, 908, clues, solution))
        seed(initial)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var controlled: ControlledPreferences
            scenario.onActivity { activity ->
                val diskBefore = diskFile.readBytes()
                controlled = controlCommits(activity)
                tapCell(activity, index)
                assertTrue(state(activity).won)
                assertTrue(state(activity).solved())
                assertStatus(activity, R.string.save_error)
                assertArrayEquals(diskBefore, diskFile.readBytes())
                assertEquals(2, controlled.attempts.size)
                val winWrite = controlled.attempts.last()
                assertFalse(winWrite.succeeded)
                assertTrue(GameState.decode(winWrite.values["game_normal"] as String)!!.won)
                assertEquals(true, winWrite.values["completed:${initial.puzzle.id}"])
                assertEquals(1, winWrite.values["solved"])
                assertEquals(1, winWrite.values["unassisted"])
            }
            onView(withText(R.string.continue_game)).inRoot(isDialog()).perform(click())
            scenario.onActivity { controlled.failCommits = false }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertTrue(GameState.decode(durableValue("game_normal")!!)!!.won)
                assertEquals("true", durableValue("completed:${initial.puzzle.id}"))
                assertEquals("1", durableValue("solved"))
                assertEquals("1", durableValue("unassisted"))
                assertStatus(activity, R.string.won)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals("1", durableValue("solved"))
            assertEquals("1", durableValue("unassisted"))
            assertFalse(controlled.attempts.last().values.containsKey("solved"))
        }
    }

    @Test fun failedReplacementAndModeSwitchKeepWarningAndOldDiskUntilDailyMoveRecovers() {
        val initial = GameState(PuzzleGenerator.generate(6, 8))
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val daily = GameState(PuzzleGenerator.generate(8, 20261005), true, today)
        seed(initial, daily)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var controlled: ControlledPreferences
            lateinit var diskBefore: ByteArray
            scenario.onActivity { activity ->
                diskBefore = diskFile.readBytes()
                controlled = controlCommits(activity)
                activity.findViewById<Button>(R.id.new_game).performClick()
            }
            onView(withText(R.string.size_6)).inRoot(isDialog()).perform(click())
            onView(withText(R.string.start)).inRoot(isDialog()).perform(click())
            val deadline = SystemClock.elapsedRealtime() + 30_000
            var generated = false
            while (!generated && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { activity ->
                    generated = state(activity).puzzle.seed != initial.puzzle.seed && activity.findViewById<Button>(R.id.daily).isEnabled
                }
                if (!generated) Thread.sleep(50)
            }
            assertTrue("Replacement was not generated", generated)
            scenario.onActivity { activity ->
                assertStatus(activity, R.string.save_error)
                assertArrayEquals(diskBefore, diskFile.readBytes())
                activity.findViewById<Button>(R.id.daily).performClick()
                assertTrue(state(activity).daily)
                assertEquals(daily.encode(), state(activity).encode())
                assertStatus(activity, R.string.save_error)
                assertArrayEquals(diskBefore, diskFile.readBytes())
                assertEquals("false", durableValue("daily_selected"))

                controlled.failCommits = false
                tapCell(activity, daily.puzzle.clues.indexOf(-1))
                assertEquals(state(activity).encode(), durableValue("game_daily"))
                assertEquals("true", durableValue("daily_selected"))
                assertEquals(initial.encode(), durableValue("game_normal"))
                assertStatus(activity, R.string.tap_help)
            }
        }
    }

    private data class Attempt(val values: Map<String, Any?>, val succeeded: Boolean)

    private class ControlledPreferences(private val real: SharedPreferences) : SharedPreferences by real {
        var failCommits = true
        val attempts = mutableListOf<Attempt>()
        override fun edit(): SharedPreferences.Editor = ControlledEditor(real.edit())

        private inner class ControlledEditor(private val realEditor: SharedPreferences.Editor) : SharedPreferences.Editor by realEditor {
            private val values = mutableMapOf<String, Any?>()
            override fun putString(key: String, value: String?): SharedPreferences.Editor { values[key] = value; realEditor.putString(key, value); return this }
            override fun putStringSet(key: String, value: Set<String>?): SharedPreferences.Editor { values[key] = value; realEditor.putStringSet(key, value); return this }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { values[key] = value; realEditor.putBoolean(key, value); return this }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor { values[key] = value; realEditor.putInt(key, value); return this }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor { values[key] = value; realEditor.putLong(key, value); return this }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor { values[key] = value; realEditor.putFloat(key, value); return this }
            override fun remove(key: String): SharedPreferences.Editor { values[key] = null; realEditor.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { values["<clear>"] = true; realEditor.clear(); return this }
            override fun apply() { error("The save journey requires a synchronous commit result") }
            override fun commit(): Boolean {
                // Failure deliberately never reaches the real editor, leaving cache and durable XML unchanged.
                val succeeded = !failCommits && realEditor.commit()
                attempts.add(Attempt(values.toMap(), succeeded))
                return succeeded
            }
        }
    }
}
