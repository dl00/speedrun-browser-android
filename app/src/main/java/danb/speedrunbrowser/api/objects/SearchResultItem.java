package danb.speedrunbrowser.api.objects;

import android.widget.TextView;

import java.net.MalformedURLException;
import java.net.URL;

public interface SearchResultItem {
    String getName();
    String getTypeName();

    URL getIconUrl() throws MalformedURLException;

    void applyTextView(TextView tv);
}
