package danb.speedrunbrowser.api.objects;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RunTimes {
    public String primary;
    public String realtime;
    public String realtimeNoloads;
    public String ingame;

    public float readTime(String ts) {
        float t = 0;

        Pattern p = Pattern.compile("PT(([0-9.]+)H)?(([0-9.]+)M)?(([0-9.]+)S)?");
        Matcher m = p.matcher(ts);

        if(m.find()) {

            // hours
            if(m.group(2) != null)
                t += 3600 * Float.parseFloat(m.group(2));
            // minutes
            if(m.group(4) != null)
                t += 60 * Float.parseFloat(m.group(4));
            // seconds
            if(m.group(6) != null)
                t += Float.parseFloat(m.group(6));
        }
        else {
            // could not parse time?
            t = 999999999;
        }


        return t;
    }

    public String formatTime() {
        System.out.println(primary);
        return format(readTime(primary));
    }

    public String formatRealtimeRuntime() {
        return format(readTime(realtime));
    }

    public String formatRealtimeNoloadsRuntime() {
        return format(readTime(realtimeNoloads));
    }

    public String formatIngameRuntime() {
        return format(readTime(ingame));
    }

    private static String format(float t) {
        return ((int)t / 60) + "m " + (t % 60) + "s";
    }
}
