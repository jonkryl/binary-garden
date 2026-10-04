package com.jonkryl.binarygarden.core

import org.json.JSONArray
import org.json.JSONObject

data class Move(val index: Int, val before: Int, val after: Int)
class GameState(val puzzle: Puzzle, val daily: Boolean = false, val day: String = "",
                val cells: MutableList<Int> = puzzle.clues.toMutableList(),
                val history: MutableList<Move> = mutableListOf(), var won: Boolean = false,
                var hints: Int = 0, var moves: Int = 0) {
    fun set(index: Int, value: Int): Boolean {
        if(index !in cells.indices || value !in -1..1 || puzzle.clues[index] >= 0 || won || cells[index] == value) return false
        history.add(Move(index,cells[index],value)); cells[index]=value; moves++
        if(history.size > 1000) history.removeAt(0)
        return true
    }
    fun cycle(index: Int): Boolean = index in cells.indices && set(index, when(cells[index]) { -1 -> 0;0 -> 1;else -> -1 })
    fun undo(): Boolean {
        if(history.isEmpty()) return false
        val last=history.removeAt(history.lastIndex);cells[last.index]=last.before;won=false;return true
    }
    fun solved(): Boolean = cells == puzzle.solution
    fun wrong(): List<Int> = cells.indices.filter { cells[it] != -1 && cells[it] != puzzle.solution[it] }
    fun encode(): String = JSONObject().put("v",1).put("n",puzzle.size).put("seed",puzzle.seed)
        .put("clues",JSONArray(puzzle.clues)).put("solution",JSONArray(puzzle.solution)).put("cells",JSONArray(cells))
        .put("history",JSONArray(history.map { JSONArray(listOf(it.index,it.before,it.after)) }))
        .put("won",won).put("hints",hints).put("moves",moves).put("daily",daily).put("day",day).toString()
    companion object {
        fun decode(text: String): GameState? = try {
            require(text.length <= 100_000)
            val j=JSONObject(text);require(j.getInt("v")==1)
            fun ints(key:String):List<Int> {val a=j.getJSONArray(key);return (0 until a.length()).map{a.getInt(it)}}
            val p=Puzzle(j.getInt("n"),j.getLong("seed"),ints("clues"),ints("solution"))
            val cells=ints("cells");require(cells.size==p.clues.size && cells.all { it in -1..1 })
            require(cells.indices.all {p.clues[it] == -1 || p.clues[it]==cells[it]})
            val history=j.getJSONArray("history");require(history.length()<=1000)
            val moves=(0 until history.length()).map { i ->
                val m=history.getJSONArray(i);val move=Move(m.getInt(0),m.getInt(1),m.getInt(2))
                require(m.length()==3 && move.index in cells.indices && p.clues[move.index]==-1 && move.before in -1..1 && move.after in -1..1);move
            }
            val state=GameState(p,j.getBoolean("daily"),j.getString("day"),cells.toMutableList(),moves.toMutableList(),j.getBoolean("won"),j.getInt("hints"),j.getInt("moves"))
            require(state.hints>=0 && state.moves>=0 && (!state.won || state.solved()))
            // Reject corrupted undo chains before they can change the visible board.
            val rewind=cells.toMutableList();for(m in moves.asReversed()){require(rewind[m.index]==m.after);rewind[m.index]=m.before}
            state
        } catch (_:Exception) {null}
    }
}
