package danb.speedrunbrowser

import org.junit.Test

import danb.speedrunbrowser.utils.LCSMatcher

import org.junit.Assert.assertEquals

class LCSMatcherTest {

    @Test
    fun maxLengthIsCorrect() {
        var matcher = LCSMatcher("test", "test", 0)
        assertEquals(4, matcher.maxMatchLength.toLong())

        matcher = LCSMatcher("test", "tesz", 0)
        assertEquals(3, matcher.maxMatchLength.toLong())

        matcher = LCSMatcher("tesz", "test", 0)
        assertEquals(3, matcher.maxMatchLength.toLong())

        matcher = LCSMatcher("songesg", "longer testing string is longest long", 0)
        assertEquals(5, matcher.maxMatchLength.toLong())
    }
}
