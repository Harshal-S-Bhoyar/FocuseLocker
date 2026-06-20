package com.focuslock.domain.usecase

/**
 * Aho-Corasick automaton for O(n + m + z) multi-pattern matching.
 * n = text length, m = total pattern chars, z = number of matches.
 * 
 * Build once via [Builder], then call [searchFirst] on any text.
 * Thread-safe after build (immutable state arrays).
 */
class AhoCorasick private constructor(
    private val goto: Array<IntArray>,     // goto[state][charIndex] → next state
    private val fail: IntArray,            // failure links
    private val output: Array<List<String>> // output[state] → matched keywords
) {
    data class Match(val keyword: String, val position: Int)

    /**
     * Single-pass search. Returns first match found (short-circuit) or null.
     * For blocking we only need to know IF any keyword matches, not all of them.
     */
    fun searchFirst(text: String): Match? {
        var state = 0
        for (i in text.indices) {
            val c = charIndex(text[i])
            if (c < 0) { state = 0; continue }

            while (state != 0 && goto[state][c] == -1) {
                state = fail[state]
            }
            state = if (goto[state][c] != -1) goto[state][c] else 0

            // Check output at this state + all failure-linked states
            var tmp = state
            while (tmp != 0) {
                if (output[tmp].isNotEmpty()) {
                    return Match(output[tmp][0], i)
                }
                tmp = fail[tmp]
            }
        }
        return null
    }

    class Builder {
        private val gotoList = mutableListOf(IntArray(ALPHABET_SIZE) { -1 }) // state 0
        private val outputList = mutableListOf<MutableList<String>>(mutableListOf())
        private var stateCount = 1

        fun addPattern(pattern: String): Builder {
            var state = 0
            for (ch in pattern) {
                val c = charIndex(ch)
                if (c < 0) continue
                if (gotoList[state][c] == -1) {
                    gotoList[state][c] = stateCount++
                    gotoList.add(IntArray(ALPHABET_SIZE) { -1 })
                    outputList.add(mutableListOf())
                }
                state = gotoList[state][c]
            }
            outputList[state].add(pattern)
            return this
        }

        fun build(): AhoCorasick {
            val fail = IntArray(stateCount)
            val queue = ArrayDeque<Int>()

            // Depth-1 states: fail → 0
            for (c in 0 until ALPHABET_SIZE) {
                val s = gotoList[0][c]
                if (s > 0) { fail[s] = 0; queue.addLast(s) }
            }

            // BFS to build failure links
            while (queue.isNotEmpty()) {
                val r = queue.removeFirst()
                for (c in 0 until ALPHABET_SIZE) {
                    val s = gotoList[r][c]
                    if (s == -1) continue
                    queue.addLast(s)

                    var state = fail[r]
                    while (state != 0 && gotoList[state][c] == -1) {
                        state = fail[state]
                    }
                    fail[s] = if (gotoList[state][c] != -1 && gotoList[state][c] != s) {
                        gotoList[state][c]
                    } else {
                        0
                    }
                    outputList[s].addAll(outputList[fail[s]])
                }
            }

            return AhoCorasick(
                goto = gotoList.toTypedArray(),
                fail = fail,
                output = outputList.map { it.toList() }.toTypedArray()
            )
        }
    }

    companion object {
        // a-z + 0-9 + space = 37 chars. All others mapped to -1 (ignored).
        private const val ALPHABET_SIZE = 37

        private fun charIndex(c: Char): Int = when (c) {
            in 'a'..'z' -> c - 'a'           // 0-25
            in '0'..'9' -> 26 + (c - '0')    // 26-35
            ' ' -> 36
            else -> -1
        }
    }
}
