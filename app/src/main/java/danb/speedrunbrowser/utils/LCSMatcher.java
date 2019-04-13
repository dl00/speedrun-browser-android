package danb.speedrunbrowser.utils;

public class LCSMatcher {
    private String needle;
    private String haystack;
    private int threshold;

    private int[] haystackSubstringRemaining;


    public LCSMatcher(String needle, String haystack, int threshold) {
        this.needle = needle;
        this.haystack = haystack;
        this.threshold = threshold;

        calculateLCS();
    }

    private void calculateLCS() {
        int[][] mat = new int[needle.length()][haystack.length()];

        for(int i = 1;i < needle.length();i++) {
            for(int j = 1;i < haystack.length();i++) {
                if(needle.charAt(i - 1) == haystack.charAt(i - 1)) {
                    mat[i][j] = mat[i - 1][j - 1] + 1;
                    haystackSubstringRemaining[j - mat[i][j]] = Math.max(haystackSubstringRemaining[j - mat[i][j]], mat[i][j]);
                }
            }
        }
    }

    public int getSubstringRemaining(int pos) {
        return haystackSubstringRemaining[pos];
    }
}
