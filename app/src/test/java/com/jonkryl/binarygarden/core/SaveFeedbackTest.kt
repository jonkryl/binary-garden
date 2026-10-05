package com.jonkryl.binarygarden.core

import org.junit.Assert.*
import org.junit.Test

class SaveFeedbackTest {
    private class Storage(initial: String) {
        var bytes = initial
            private set
        var writable = false
        var attempts = 0
            private set
        fun commit(snapshot: String): Boolean {
            attempts++
            if (!writable) return false
            bytes = snapshot
            return true
        }
    }

    @Test fun failedMoveAndUndoKeepDurableSnapshotAndInMemoryGameplay() {
        val game = GameState(PuzzleGenerator.generate(6, 8))
        val original = game.encode()
        val storage = Storage(original)
        val feedback = SaveFeedback()
        val index = game.puzzle.clues.indexOf(-1)

        assertTrue(game.cycle(index))
        assertFalse(feedback.commit { storage.commit(game.encode()) })
        assertEquals(0, game.cells[index])
        assertEquals(1, game.history.size)
        assertEquals(original, storage.bytes)
        for (status in listOf("tap", "no errors", "incorrect cells", "fix before hint", "loading", "won"))
            assertEquals("save error", feedback.message(status, "save error"))

        assertTrue(game.undo())
        assertFalse(feedback.commit { storage.commit(game.encode()) })
        assertEquals(-1, game.cells[index])
        assertTrue(game.history.isEmpty())
        assertEquals(1, game.moves)
        assertEquals(original, storage.bytes)
        assertEquals("save error", feedback.message("tap", "save error"))
        assertEquals(2, storage.attempts)
    }

    @Test fun recoveryCommitsLatestSnapshotWithHintAndUndoHistory() {
        val game = GameState(PuzzleGenerator.generate(6, 8))
        val storage = Storage(game.encode())
        val feedback = SaveFeedback()
        val step = Logic.next(game.cells, game.puzzle.size)!!
        assertTrue(game.set(step.index, step.value))
        game.hints++
        assertFalse(feedback.commit { storage.commit(game.encode()) })
        val second = game.puzzle.clues.indices.first { game.cells[it] == -1 }
        assertTrue(game.cycle(second))
        val latest = game.encode()

        storage.writable = true
        assertTrue(feedback.commit { storage.commit(latest) })
        val restored = GameState.decode(storage.bytes)!!
        assertEquals(latest, restored.encode())
        assertEquals(1, restored.hints)
        assertEquals(2, restored.moves)
        assertEquals("tap", feedback.message("tap", "save error"))
        assertTrue(restored.undo())
        assertEquals(-1, restored.cells[second])
        assertTrue(restored.undo())
        assertEquals(-1, restored.cells[step.index])
    }

    @Test fun successAfterFailedWonAndReplacementSnapshotsClearsOnlyOnCommit() {
        val original = GameState(PuzzleGenerator.generate(6, 8))
        val storage = Storage(original.encode())
        val feedback = SaveFeedback()
        for (index in original.cells.indices) if (original.puzzle.clues[index] == -1)
            assertTrue(original.set(index, original.puzzle.solution[index]))
        original.won = true
        assertFalse(feedback.commit { storage.commit(original.encode()) })
        assertEquals("save error", feedback.message("won", "save error"))

        val replacement = GameState(PuzzleGenerator.generate(6, 9))
        assertFalse(feedback.commit { storage.commit(replacement.encode()) })
        assertEquals("save error", feedback.message("tap", "save error"))
        assertNotEquals(replacement.encode(), storage.bytes)
        storage.writable = true
        assertTrue(feedback.commit { storage.commit(replacement.encode()) })
        assertEquals(replacement.encode(), GameState.decode(storage.bytes)!!.encode())
        assertEquals("tap", feedback.message("tap", "save error"))

        storage.writable = false
        assertTrue(replacement.cycle(replacement.puzzle.clues.indexOf(-1)))
        assertFalse(feedback.commit { storage.commit(replacement.encode()) })
        assertEquals("save error", feedback.message("tap", "save error"))
    }
}
