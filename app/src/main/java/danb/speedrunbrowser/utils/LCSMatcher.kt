package danb.speedrunbrowser.utils

class LCSMatcher(private val needle: String, private val haystack: String, private val threshold: Int) {

    private val haystackSubstringRemaining = IntArray(haystack.length)

    var maxMatchLength: Int = 0
        private set

    init {
        calculateLCS()
    }

    private fun calculateLCS() {
        val mat = Array(needle.length + 1) { IntArray(haystack.length + 1) }

        for (i in 1..needle.length) {
            for (j in 1..haystack.length) {
                if (needle[i - 1] == haystack[j - 1]) {
                    mat[i][j] = mat[i - 1][j - 1] + 1
                    haystackSubstringRemaining[j - mat[i][j]] = Math.max(haystackSubstringRemaining[j - mat[i][j]], mat[i][j])

                    maxMatchLength = Math.max(maxMatchLength, haystackSubstringRemaining[j - mat[i][j]])
                }
            }
        }
    }

    fun getSubstringRemaining(pos: Int): Int {
        return haystackSubstringRemaining[pos]
    }
}
