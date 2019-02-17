package danb.speedrunbrowser.api.objects;


import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;

import java.io.Serializable;

class UserNameStyleColor implements Serializable {
    public String light;
    public String dark;
}

public class UserNameStyle implements Serializable {
    public String style; // "solid" or "gradient"
    public UserNameStyleColor color;
    public UserNameStyleColor colorFrom;
    public UserNameStyleColor colorTo;

    public Shader getTextShader(float size, boolean light) {

        int c1, c2;
        if(style.equals("solid")) {
            c1 = Color.parseColor(light ? color.light : color.dark);
            c2 = c1;
        }
        else if(style.equals("gradient")) {
            c1 = Color.parseColor(light ? colorFrom.light : colorFrom.dark);
            c2 = Color.parseColor(light ? colorTo.light : colorTo.dark);
        }
        else {
            c1 = Color.WHITE;
            c2 = Color.WHITE;
        }


        return new LinearGradient(0, 0, size, 0, c1, c2, Shader.TileMode.CLAMP);
    }
}
