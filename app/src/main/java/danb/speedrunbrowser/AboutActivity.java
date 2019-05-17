package danb.speedrunbrowser;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import danb.speedrunbrowser.utils.Util;

public class AboutActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = LeaderboardFragment.class.getSimpleName();

    private static final Uri SPEEDRUN_COM_ABOUT = Uri.parse("https://www.speedrun.com/about");

    private static final Uri TERMS_AND_CONDITIONS = Uri.parse("https://speedrun-browser-4cc82.firebaseapp.com/terms.html");
    private static final Uri PRIVACY_POLICY = Uri.parse("https://speedrun-browser-4cc82.firebaseapp.com/privacy-policy.html");

    private static final String OPENSOURCE_LICENSES_FILE = "/assets/licenses.txt";

    ImageView mLinkSpeedrunComTrophy;
    ImageView mLinkSpeedrunComLogo;

    TextView mAppTitle;

    TextView mLinkTermsAndConditions;
    TextView mLinkPrivacyPolicy;
    TextView mLinkOpenSourceLicenses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        mAppTitle = findViewById(R.id.txtAppTitle);
        mLinkSpeedrunComTrophy = findViewById(R.id.linkSpeedrunComTrophy);
        mLinkSpeedrunComLogo = findViewById(R.id.linkSpeedrunComWebsite);
        mLinkPrivacyPolicy = findViewById(R.id.linkPrivacyPolicy);
        mLinkTermsAndConditions = findViewById(R.id.linkTermsAndConditions);
        mLinkOpenSourceLicenses = findViewById(R.id.linkOpenSourceLicenses);

        mLinkSpeedrunComTrophy.setOnClickListener(this);
        mLinkSpeedrunComLogo.setOnClickListener(this);
        mLinkTermsAndConditions.setOnClickListener(this);
        mLinkPrivacyPolicy.setOnClickListener(this);
        mLinkOpenSourceLicenses.setOnClickListener(this);

        mAppTitle.setText(mAppTitle.getText() + " v" + BuildConfig.VERSION_NAME);
    }

    private void openLink(Uri link) {
        Intent intent = new Intent(Intent.ACTION_VIEW, link);
        startActivity(intent);
    }

    private void viewOpenSourceLicenses() {
        String licenseText;
        try {
            licenseText = Util.INSTANCE.readToString(getClass().getResourceAsStream(OPENSOURCE_LICENSES_FILE));
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
            openLink(SPEEDRUN_COM_ABOUT);
        else if(v == mLinkTermsAndConditions)
            openLink(TERMS_AND_CONDITIONS);
        else if(v == mLinkPrivacyPolicy)
            openLink(PRIVACY_POLICY);
        else if(v == mLinkOpenSourceLicenses)
            viewOpenSourceLicenses();
    }
}
