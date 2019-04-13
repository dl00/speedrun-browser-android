package danb.speedrunbrowser.api.objects;

import android.widget.TextView;

public interface SearchResultItem {
    String getName();
    String getTypeName();

    void applyTextView(TextView tv);
}
