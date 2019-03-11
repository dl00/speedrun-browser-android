package danb.speedrunbrowser;

import androidx.appcompat.app.AppCompatActivity;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.api.objects.User;
import danb.speedrunbrowser.utils.DownloadImageTask;

import android.annotation.SuppressLint;
import android.app.Person;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PlayerDetailActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = PlayerDetailActivity.class.getSimpleName();

    private static final String AVATAR_IMG_LOCATION = "https://www.speedrun.com/themes/user/%s/image.png";

    private User mPlayer;

    private ImageView mPlayerIcon;
    private TextView mPlayerName;

    private ImageView mIconTwitch;
    private ImageView mIconTwitter;
    private ImageView mIconYoutube;
    private ImageView mIconZSR;

    private LinearLayout mBestsFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player_detail);

        mPlayerIcon = findViewById(R.id.imgAvatar);
        mPlayerName = findViewById(R.id.txtPlayerName);
        mIconTwitch = findViewById(R.id.iconTwitch);
        mIconTwitter = findViewById(R.id.iconTwitter);
        mIconYoutube = findViewById(R.id.iconYoutube);
        mIconZSR = findViewById(R.id.iconZSR);
        mBestsFrame = findViewById(R.id.bestsLayout);

        mIconTwitch.setOnClickListener(this);
        mIconTwitter.setOnClickListener(this);
        mIconYoutube.setOnClickListener(this);
        mIconZSR.setOnClickListener(this);
    }

    private void setViewData() {

        mPlayer.applyTextView(mPlayerName);

        mIconTwitch.setVisibility(mPlayer.twitch != null ? View.VISIBLE : View.GONE);
        mIconTwitter.setVisibility(mPlayer.twitter != null ? View.VISIBLE : View.GONE);
        mIconYoutube.setVisibility(mPlayer.youtube != null ? View.VISIBLE : View.GONE);
        mIconZSR.setVisibility(mPlayer.speedrunslive != null ? View.VISIBLE : View.GONE);


        if(!mPlayer.isGuest()) {
            try {
                mBestsFrame.setVisibility(View.VISIBLE);
                new DownloadImageTask(this, mBestsFrame).clear(true).execute(new URL(String.format(AVATAR_IMG_LOCATION, mPlayer.getName())));
            } catch (MalformedURLException e) {
                mBestsFrame.setVisibility(View.GONE);
            }
        }
        else
            mBestsFrame.setVisibility(View.GONE);

        populateBestsFrame();
    }

    @SuppressLint("SetTextI18n")
    private void populateBestsFrame() {
        if(mPlayer.bests == null)
            return;

        for(User.UserGameBests gameBests : mPlayer.bests.values()) {
            View gameLayout = getLayoutInflater().inflate(R.layout.content_game_personal_bests, null);

            ((TextView)gameLayout.findViewById(R.id.txtGameName)).setText(gameBests.names.get("international"));

            if(gameBests.assets.coverLarge != null)
                new DownloadImageTask(this, gameLayout.findViewById(R.id.imgCover)).clear(true).execute(gameBests.assets.coverLarge.uri);

            List<PersonalBestRunRow> runsToAdd = new ArrayList<>();

            for(User.UserCategoryBest categoryBest : gameBests.categories.values()) {

                if(categoryBest.levels != null && !categoryBest.levels.isEmpty()) {
                    for(User.UserLevelBest levelBest : categoryBest.levels.values()) {
                        PersonalBestRunRow rr = new PersonalBestRunRow(categoryBest.name, levelBest.name, levelBest.run);
                        runsToAdd.add(rr);
                    }
                }
                else {
                    PersonalBestRunRow rr = new PersonalBestRunRow(categoryBest.name, null, categoryBest.run);
                    runsToAdd.add(rr);
                }
            }

            // sort these runs by date, descending
            Collections.sort(runsToAdd, new Comparator<PersonalBestRunRow>() {
                @Override
                public int compare(PersonalBestRunRow o1, PersonalBestRunRow o2) {
                    return -o1.re.run.date.compareTo(o2.re.run.date);
                }
            });

            TableLayout bestTable = gameLayout.findViewById(R.id.tablePersonalBests);

            for(PersonalBestRunRow row : runsToAdd) {
                TableRow rowPersonalBest = (TableRow)getLayoutInflater().inflate(R.layout.content_row_personal_best, null);
                ((TextView)rowPersonalBest.getChildAt(0)).setText(row.label);

                LinearLayout placeLayout = (LinearLayout)rowPersonalBest.getChildAt(1);
                if(row.re.place == 1 && gameBests.assets.trophy1st != null) {
                    new DownloadImageTask(this, placeLayout.getChildAt(0)).execute(gameBests.assets.trophy1st.uri);
                }
                if(row.re.place == 2 && gameBests.assets.trophy2nd != null) {
                    new DownloadImageTask(this, placeLayout.getChildAt(0)).execute(gameBests.assets.trophy2nd.uri);
                }
                if(row.re.place == 3 && gameBests.assets.trophy3rd != null) {
                    new DownloadImageTask(this, placeLayout.getChildAt(0)).execute(gameBests.assets.trophy3rd.uri);
                }
                if(row.re.place == 4 && gameBests.assets.trophy4th != null) {
                    new DownloadImageTask(this, placeLayout.getChildAt(0)).execute(gameBests.assets.trophy4th.uri);
                }
                else
                    ((ImageView)placeLayout.getChildAt(0)).setImageDrawable(new ColorDrawable(Color.TRANSPARENT));

                ((TextView)placeLayout.getChildAt(1)).setText(row.re.getPlaceName());

                ((TextView)rowPersonalBest.getChildAt(2)).setText(row.re.run.times.formatTime());
                ((TextView)rowPersonalBest.getChildAt(3)).setText(row.re.run.date);

                bestTable.addView(rowPersonalBest);
            }

            mBestsFrame.addView(gameLayout);
        }
    }

    @Override
    public void onClick(View v) {

        URL selectedLink = null;

        if(v == mIconTwitch)
            selectedLink = mPlayer.twitch.uri;
        if(v == mIconTwitter)
            selectedLink = mPlayer.twitter.uri;
        if(v == mIconYoutube)
            selectedLink = mPlayer.youtube.uri;
        if(v == mIconZSR)
            selectedLink = mPlayer.speedrunslive.uri;

        if(selectedLink != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(selectedLink.toString()));
            startActivity(intent);
        }
    }

    private static class PersonalBestRunRow {

        public String label;
        public LeaderboardRunEntry re;

        public PersonalBestRunRow(String categoryName, String levelName, LeaderboardRunEntry re) {

            if(levelName != null)
                label = categoryName + " - " + levelName;
            else
                label = categoryName;

            this.re = re;
        }
    }
}
