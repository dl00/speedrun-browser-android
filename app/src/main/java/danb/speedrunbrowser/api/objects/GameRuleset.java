package danb.speedrunbrowser.api.objects;

import java.io.Serializable;

class GameRuleset implements Serializable {
    public boolean showMilliseconds;
    public boolean requireVerification;
    public boolean requireVideo;
    public String[] runTimes;
    public String defaultTime;
    public boolean emulatorsAllowed;
}
