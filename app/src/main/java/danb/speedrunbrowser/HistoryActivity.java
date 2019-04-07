package danb.speedrunbrowser;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import danb.speedrunbrowser.api.SpeedrunMiddlewareAPI;
import danb.speedrunbrowser.api.objects.LeaderboardRunEntry;
import danb.speedrunbrowser.holders.WatchRunViewHolder;
import danb.speedrunbrowser.utils.AppDatabase;
import danb.speedrunbrowser.utils.ConnectionErrorConsumer;
import danb.speedrunbrowser.views.ProgressSpinnerView;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class HistoryActivity extends AppCompatActivity {

    RecyclerView mHistoryList;
    TextView mNoHistoryText;
    ProgressSpinnerView mProgressSpinner;

    List<LeaderboardRunEntry> mRunEntries;

    CompositeDisposable mDisposables = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        mProgressSpinner = findViewById(R.id.spinner);
        mHistoryList = findViewById(R.id.lstWatchHistory);
        mNoHistoryText = findViewById(R.id.txtWatchHistoryMsg);

        mRunEntries = new ArrayList<>(0);

        mHistoryList.setAdapter(new WatchHistoryAdapter());

        loadRunData(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mDisposables.dispose();
    }

    public void loadRunData(int offset) {
        AppDatabase db = AppDatabase.make(this);

        mDisposables.add(db.watchHistoryDao().getMany(offset)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<List<AppDatabase.WatchHistoryEntry>>() {
                    @Override
                    public void accept(List<AppDatabase.WatchHistoryEntry> watchHistoryEntries) throws Exception {

                        if(watchHistoryEntries.isEmpty()) {
                            setViewData();
                            return;
                        }

                        final List<String> runIds = new ArrayList<>(watchHistoryEntries.size());

                        for(AppDatabase.WatchHistoryEntry whe : watchHistoryEntries) {
                            runIds.add(whe.runId);
                        }

                        String runs = TextUtils.join(",", runIds);

                        mDisposables.add(SpeedrunMiddlewareAPI.make().listRuns(runs)
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Consumer<SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry>>() {
                                    @Override
                                    public void accept(SpeedrunMiddlewareAPI.APIResponse<LeaderboardRunEntry> runAPIResponse) throws Exception {
                                        mRunEntries = runAPIResponse.data;

                                        setViewData();
                                    }
                                }, new ConnectionErrorConsumer(HistoryActivity.this)));
                    }
                }));
    }

    private void setViewData() {
        if(mRunEntries.isEmpty()) {
            mNoHistoryText.setVisibility(View.VISIBLE);
        }
        else {
            mNoHistoryText.setVisibility(View.GONE);
            Objects.requireNonNull(mHistoryList.getAdapter()).notifyDataSetChanged();
        }

        mProgressSpinner.setVisibility(View.GONE);
    }

    private void showRun(LeaderboardRunEntry run) {
        Intent intent = new Intent(this, RunDetailActivity.class);

        intent.putExtra(RunDetailActivity.EXTRA_RUN, run.run);
        intent.putExtra(RunDetailActivity.EXTRA_GAME, run.run.game);
        intent.putExtra(RunDetailActivity.EXTRA_CATEGORY, run.run.category);
        intent.putExtra(RunDetailActivity.EXTRA_LEVEL, run.run.level);

        startActivity(intent);
    }

    private class WatchHistoryAdapter extends RecyclerView.Adapter<WatchRunViewHolder> {
        private final LayoutInflater inflater;

        public WatchHistoryAdapter() {
            inflater = (LayoutInflater) HistoryActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @NonNull
        @Override
        public WatchRunViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = inflater.inflate(R.layout.content_watch_list, parent, false);
            return new WatchRunViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull WatchRunViewHolder holder, int position) {
            final LeaderboardRunEntry run = mRunEntries.get(position);

            holder.apply(HistoryActivity.this, run.run.game, run);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showRun(run);
                }
            });
            holder.itemView.setTag(run);
        }

        @Override
        public int getItemCount() {
            return mRunEntries != null ? mRunEntries.size() : 0;
        }
    }
}
