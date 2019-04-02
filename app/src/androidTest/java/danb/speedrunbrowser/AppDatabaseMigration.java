package danb.speedrunbrowser;

import android.database.Cursor;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import androidx.test.runner.AndroidJUnit4;

@RunWith(AndroidJUnit4.class)
public class AppDatabaseMigration {
    private static final String TEST_DB = "migration-test";

    public AppDatabaseMigration() {
    }

    @Test
    public void migrate1To2() throws IOException {
        // TODO: How??? the documented way of testing this does not work
    }
}
