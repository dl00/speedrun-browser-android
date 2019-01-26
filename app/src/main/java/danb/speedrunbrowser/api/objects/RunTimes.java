package danb.speedrunbrowser.api.objects;

public class RunTimes {
    public String primary;
    public int primaryT;
    public String realtime;
    public int realtimeT;
    public String realtimeNoloads;
    public int realtimeNoloadsT;
    public String ingame;
    public int ingameT;

    public String formatTime() {
        return format(primaryT);
    }

    public String formatRealtimeRuntime() {
        return format(realtimeT);
    }

    public String formatRealtimeNoloadsRuntime() {
        return format(realtimeNoloadsT);
    }

    public String formatIngameRuntime() {
        return format(ingameT);
    }

    private static String format(int t) {
        return (t / 60) + "m " + (t % 60) + "s";
    }
}
