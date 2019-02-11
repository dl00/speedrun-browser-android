package danb.speedrunbrowser.api.objects;

import java.io.Serializable;

public class Category implements Serializable {
    public String id;
    public String name;
    public String weblink;
    public String type;
    public String rules;

    public boolean miscellaneous;
}
