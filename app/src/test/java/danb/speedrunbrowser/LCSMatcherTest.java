package danb.speedrunbrowser;

import org.junit.Test;

import danb.speedrunbrowser.utils.LCSMatcher;

import static org.junit.Assert.assertEquals;

public class LCSMatcherTest {

    @Test
    public void maxLengthIsCorrect() {
        LCSMatcher matcher = new LCSMatcher("test", "test", 0);
        assertEquals(4, matcher.getMaxMatchLength());

        matcher = new LCSMatcher("test", "tesz", 0);
        assertEquals(3, matcher.getMaxMatchLength());

        matcher = new LCSMatcher("tesz", "test", 0);
        assertEquals(3, matcher.getMaxMatchLength());

        matcher = new LCSMatcher("songesg", "longer testing string is longest long", 0);
        assertEquals(5, matcher.getMaxMatchLength());
    }
}
