package com.jonkryl.binarygarden.core

import java.util.Random

data class Puzzle(val size: Int, val seed: Long, val clues: List<Int>, val solution: List<Int>) {
    init {
        require(size == 6 || size == 8)
        require(clues.size == size * size && solution.size == clues.size)
        require(clues.all { it in -1..1 } && solution.all { it in 0..1 })
        require(clues.indices.all { clues[it] == -1 || clues[it] == solution[it] })
        require(BinaryRules.valid(solution, size, complete = true))
    }
    val id: String get() = "$size:$seed"
}

object BinaryRules {
    fun lineValid(line: List<Int>, complete: Boolean = false): Boolean {
        if (line.size % 2 != 0 || line.any { it !in -1..1 }) return false
        if (line.count { it == 0 } > line.size / 2 || line.count { it == 1 } > line.size / 2) return false
        if ((0 until line.size - 2).any { line[it] >= 0 && line[it] == line[it+1] && line[it] == line[it+2] }) return false
        return !complete || -1 !in line
    }

    fun valid(board: List<Int>, n: Int, complete: Boolean = false): Boolean {
        if (n !in listOf(6, 8) || board.size != n * n || board.any { it !in -1..1 }) return false
        val rows = (0 until n).map { r -> board.subList(r*n, (r+1)*n) }
        val cols = (0 until n).map { c -> (0 until n).map { board[it*n+c] } }
        if ((rows+cols).any { !lineValid(it, complete) }) return false
        return listOf(rows, cols).all { lines ->
            val finished = lines.filter { -1 !in it }
            finished.toSet().size == finished.size
        }
    }

    fun patterns(n: Int): List<List<Int>> = (0 until (1 shl n)).map { mask ->
        (0 until n).map { (mask shr it) and 1 }
    }.filter { lineValid(it, true) }
}

data class LogicalStep(val index: Int, val value: Int, val row: Boolean, val line: Int)

/** Intersects every legal completion of one row/column. No speculative search or hidden solution. */
object Logic {
    fun next(board: List<Int>, n: Int): LogicalStep? {
        if (!BinaryRules.valid(board, n)) return null
        val patterns = BinaryRules.patterns(n)
        for (row in listOf(true, false)) {
            val lines = (0 until n).map { line -> (0 until n).map { k -> board[if(row) line*n+k else k*n+line] } }
            val completed = lines.filter { -1 !in it }.toSet()
            for (line in 0 until n) {
                if (-1 !in lines[line]) continue
                val possible = patterns.filter { pattern ->
                    pattern !in completed && (0 until n).all { lines[line][it] == -1 || lines[line][it] == pattern[it] }
                }
                if (possible.isEmpty()) return null
                for (k in 0 until n) if(lines[line][k] == -1 && possible.all { it[k] == possible.first()[k] }) {
                    return LogicalStep(if(row) line*n+k else k*n+line, possible.first()[k], row, line)
                }
            }
        }
        return null
    }

    fun solves(clues: List<Int>, n: Int): Boolean {
        val b = clues.toMutableList()
        repeat(n*n) {
            if (-1 !in b) return BinaryRules.valid(b,n,true)
            val step = next(b,n) ?: return false
            b[step.index] = step.value
        }
        return BinaryRules.valid(b,n,true)
    }
}

object PuzzleGenerator {
    fun generate(n: Int, seed: Long): Puzzle {
        require(n == 6 || n == 8)
        val random = Random(seed)
        val patterns = BinaryRules.patterns(n).shuffled(random)
        val b = MutableList(n*n) { -1 }
        var visited = 0
        fun placeRow(r: Int): Boolean {
            if (++visited > 250_000) throw IllegalStateException("Board generation budget exhausted")
            if(r == n) return BinaryRules.valid(b,n,true)
            for(pattern in patterns) {
                for(c in 0 until n) b[r*n+c] = pattern[c]
                if(BinaryRules.valid(b,n) && placeRow(r+1)) return true
            }
            for(c in 0 until n) b[r*n+c] = -1
            return false
        }
        check(placeRow(0)) { "No valid board" }
        val solution = b.toList()
        for(index in (0 until n*n).shuffled(random)) {
            val value = b[index]; b[index] = -1
            if(!Logic.solves(b,n)) b[index] = value
        }
        check(Logic.solves(b,n))
        return Puzzle(n,seed,b.toList(),solution)
    }
}

/** Independent bounded cell search used by CI, never for the in-game logical hints. */
object SolutionCounter {
    data class Result(val count: Int, val budgetExceeded: Boolean)
    fun count(clues: List<Int>, n: Int, limit: Int = 2, budget: Int = 500_000): Result {
        val b = clues.toMutableList(); var visits = 0; var total = 0; var exhausted = false
        fun search() {
            if(total >= limit || exhausted || !BinaryRules.valid(b,n)) return
            if(++visits > budget) { exhausted = true; return }
            if(-1 !in b) { total++; return }
            var index = -1; var candidates = emptyList<Int>()
            for(i in b.indices) if(b[i] == -1) {
                val possible = (0..1).filter { value -> b[i]=value; val ok=BinaryRules.valid(b,n); b[i]=-1; ok }
                if(possible.isEmpty()) return
                if(index == -1 || possible.size < candidates.size) {index=i;candidates=possible}
                if(possible.size == 1) break
            }
            for(value in candidates) { b[index]=value;search();b[index]=-1 }
        }
        search(); return Result(total,exhausted)
    }
}
