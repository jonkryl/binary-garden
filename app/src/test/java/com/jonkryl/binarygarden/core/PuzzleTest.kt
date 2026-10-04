package com.jonkryl.binarygarden.core

import org.junit.Assert.*
import org.junit.Test

class PuzzleTest {
    @Test fun generatedBoardsHaveExactlyOneSolutionAndLogicCanFinish() {
        for(n in listOf(6,8)) for(seed in 0L until 40L) {
            val p=PuzzleGenerator.generate(n,seed)
            assertTrue(p.clues.count {it==-1} >= n*n/3)
            assertTrue(Logic.solves(p.clues,n))
            val independent=SolutionCounter.count(p.clues,n)
            assertFalse("n=$n seed=$seed budget",independent.budgetExceeded)
            assertEquals("n=$n seed=$seed count",1,independent.count)
            // Separate arithmetic/triple/uniqueness assertions, rather than trusting generator validation.
            val rows=p.solution.chunked(n)
            val cols=(0 until n).map {c -> (0 until n).map{r->p.solution[r*n+c]}}
            for(lines in listOf(rows,cols)) {
                assertEquals(n,lines.toSet().size)
                for(line in lines) { assertEquals(n/2,line.count{it==0});assertEquals(n/2,line.count{it==1});
                    for(k in 0 until n-2) assertFalse(line[k]==line[k+1] && line[k]==line[k+2]) }
            }
        }
    }
    @Test fun logicalHintsNeverNeedTheStoredSolution() {
        val p=PuzzleGenerator.generate(8,123456)
        val b=p.clues.toMutableList()
        while(-1 in b) {val step=Logic.next(b,8)?:error("No logical step");assertEquals(p.solution[step.index],step.value);b[step.index]=step.value}
        assertEquals(p.solution,b)
    }
    @Test fun seedsAreStableAndDistinct() {
        assertEquals(PuzzleGenerator.generate(6,44),PuzzleGenerator.generate(6,44))
        assertTrue((0L until 20L).map{PuzzleGenerator.generate(6,it).clues}.toSet().size>=15)
    }
    @Test fun threeIdenticalValuesAndDuplicateCompletedLinesAreRejected() {
        assertFalse(BinaryRules.lineValid(listOf(0,0,0,-1,-1,-1)))
        val p=PuzzleGenerator.generate(6,41);val b=p.solution.toMutableList()
        for(i in 0 until 6)b[6+i]=b[i]
        assertFalse(BinaryRules.valid(b,6,true))
    }
    @Test fun partialEmptyBoardIsValidButNotComplete() {
        assertTrue(BinaryRules.valid(List(36){-1},6))
        assertFalse(BinaryRules.valid(List(36){-1},6,true))
        assertFalse(Logic.solves(List(36){-1},6))
    }
    @Test fun boundedSearchDoesNotPretendItVerifiedUniqueness() {
        val result=SolutionCounter.count(List(36){-1},6,budget=1)
        assertTrue(result.budgetExceeded)
    }
    @Test fun everyMoveAndUndoSurviveStorage() {
        val g=GameState(PuzzleGenerator.generate(6,8));val i=g.puzzle.clues.indexOf(-1)
        assertTrue(g.cycle(i));assertEquals(0,g.cells[i]);assertTrue(g.cycle(i));assertEquals(1,g.cells[i])
        val restored=GameState.decode(g.encode())!!;assertEquals(g.encode(),restored.encode())
        assertTrue(restored.undo());assertEquals(0,restored.cells[i]);assertTrue(restored.undo());assertEquals(-1,restored.cells[i])
    }
    @Test fun clueCellsStayImmutableAndWinCanBeUndone() {
        val g=GameState(PuzzleGenerator.generate(6,3));val clue=g.puzzle.clues.indexOfFirst{it>=0}
        assertFalse(g.cycle(clue));assertTrue(g.history.isEmpty())
        for(i in g.cells.indices)if(g.puzzle.clues[i]==-1)g.set(i,g.puzzle.solution[i])
        assertTrue(g.solved());g.won=true;assertFalse(g.cycle(g.puzzle.clues.indexOf(-1)))
        assertTrue(g.undo());assertFalse(g.solved());assertFalse(g.won)
    }
    @Test fun corruptOrOversizedStorageIsRejected() {
        assertNull(GameState.decode("not json"));assertNull(GameState.decode("x".repeat(100001)))
        val g=GameState(PuzzleGenerator.generate(6,17));val i=g.puzzle.clues.indexOf(-1);g.cycle(i)
        assertNull(GameState.decode(g.encode().replace("\"v\":1","\"v\":2")))
        val j=org.json.JSONObject(g.encode());j.getJSONArray("cells").put(i,9);assertNull(GameState.decode(j.toString()))
    }
    @Test fun dailyMetadataAndStatisticsRoundTrip() {
        val g=GameState(PuzzleGenerator.generate(8,20261004),true,"2026-10-04");g.hints=2;g.moves=4
        val r=GameState.decode(g.encode())!!;assertTrue(r.daily);assertEquals(g.day,r.day);assertEquals(2,r.hints);assertEquals(4,r.moves)
    }
}
