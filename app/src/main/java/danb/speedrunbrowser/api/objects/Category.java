package danb.speedrunbrowser.api.objects;

import java.io.Serializable;
import java.util.List;

public class Category implements Serializable {
    public String id;
    public String name;
    public String weblink;
    public String type;
    public String rules;

    public List<Variable> variables;

    public boolean miscellaneous;
}
