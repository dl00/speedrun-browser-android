package danb.speedrunbrowser;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import danb.speedrunbrowser.utils.Util;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

public class AboutActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = LeaderboardFragment.class.getSimpleName();

    private static final Uri SPEEDRUN_COM_ABOUT = Uri.parse("https://www.speedrun.com/about");

    private static final String OPENSOURCE_LICENSES_FILE = "/assets/licenses.txt";

    ImageView mLinkSpeedrunComTrophy;
    ImageView mLinkSpeedrunComLogo;

    TextView mLinkOpenSourceLicenses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        mLinkSpeedrunComTrophy = findViewById(R.id.linkSpeedrunComTrophy);
        mLinkSpeedrunComLogo = findViewById(R.id.linkSpeedrunComWebsite);
        mLinkOpenSourceLicenses = findViewById(R.id.linkOpenSourceLicenses);

        mLinkSpeedrunComTrophy.setOnClickListener(this);
        mLinkSpeedrunComLogo.setOnClickListener(this);
        mLinkOpenSourceLicenses.setOnClickListener(this);

    }

    private void openSpeedrunComAbout() {
        Intent intent = new Intent(Intent.ACTION_VIEW, SPEEDRUN_COM_ABOUT);
        startActivity(intent);
    }

    private void viewOpenSourceLicenses() {
        String licenseText;
        try {
            licenseText = Util.readToString(getClass().getResourceAsStream(OPENSOURCE_LICENSES_FILE));
        }
        catch(Exception e) {
            // this basically should not happen
            Log.e(TAG, "Could not load license information from JAR file:", e);
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setMessage(licenseText)
                .setNeutralButton(android.R.string.ok, null)
                .create();

        dialog.show();
    }

    @Override
    public void onClick(View v) {
        if(v == mLinkSpeedrunComTrophy || v == mLinkSpeedrunComLogo)
            openSpeedrunComAbout();
        else if(v == mLinkOpenSourceLicenses)
            viewOpenSourceLicenses();
    }
}
