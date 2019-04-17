package danb.speedrunbrowser.utils;

public class LCSMatcher {
    private String needle;
    private String haystack;
    private int threshold;

    private int[] haystackSubstringRemaining;

    private int maxSubstring;


    public LCSMatcher(String needle, String haystack, int threshold) {
        this.needle = needle;
        this.haystack = haystack;
        this.threshold = threshold;

        haystackSubstringRemaining = new int[haystack.length()];

        calculateLCS();
    }

    private void calculateLCS() {
        int[][] mat = new int[needle.length() + 1][haystack.length() + 1];

        for(int i = 1;i <= needle.length();i++) {
            for(int j = 1;j <= haystack.length();j++) {
                if(needle.charAt(i - 1) == haystack.charAt(j - 1)) {
                    mat[i][j] = mat[i - 1][j - 1] + 1;
                    haystackSubstringRemaining[j - mat[i][j]] = Math.max(haystackSubstringRemaining[j - mat[i][j]], mat[i][j]);

                    maxSubstring = Math.max(maxSubstring, haystackSubstringRemaining[j - mat[i][j]]);
                }
            }
        }
    }

    public int getSubstringRemaining(int pos) {
        return haystackSubstringRemaining[pos];
    }

    public int getMaxMatchLength() {
        return maxSubstring;
    }
}
