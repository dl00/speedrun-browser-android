package danb.speedrunbrowser.utils;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import danb.speedrunbrowser.LeaderboardFragment;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Leaderboard;
import danb.speedrunbrowser.api.objects.Level;
import io.reactivex.annotations.NonNull;

public class LeaderboardPagerAdapter extends FragmentPagerAdapter {

    private final Game game;

    private final List<Category> perGameCategories;
    private final List<Category> perLevelCategories;
    private final List<Level> levels;

    public LeaderboardPagerAdapter(FragmentManager fm, Game game) {
        super(fm);

        this.game = game;

        perGameCategories = new ArrayList<>();
        perLevelCategories = new ArrayList<>();

        if(game.categories != null) {
            for(Category c : game.categories) {
                if(c.type.equals("per-game"))
                    perGameCategories.add(c);
                else if(c.type.equals("per-level"))
                    perLevelCategories.add(c);

                // TODO: Log or something if per-game or per-level are not the str
            }
        }

        if(!perLevelCategories.isEmpty()) {
            this.levels = game.levels;
        }
        else {
            this.levels = new ArrayList<>(0);
        }

        assert perLevelCategories.isEmpty() || !levels.isEmpty();
    }

    public Category getCategoryOfIndex(int position) {
        if(position < perGameCategories.size()) {
            return perGameCategories.get(position);
        }

        int mpos = position - perGameCategories.size();
        return perLevelCategories.get(mpos / levels.size());
    }

    public Level getLevelOfIndex(int position) {
        if(position >= perGameCategories.size()) {
            int mpos = position - perGameCategories.size();
            levels.get(mpos % levels.size());
        }

        return null;
    }

    public int indexOf(Category category, Level level) {
        int index;
        if((index = perGameCategories.indexOf(category)) != -1)
            return index;

        index = levels.indexOf(level);
        if(index < 0)
            return -1;

        index += levels.size() * perLevelCategories.indexOf(category);
        if(index < 0)
            return -1;

        return index;
    }

    @Override
    public Fragment getItem(int position) {
        Fragment frag = new LeaderboardFragment();
        Bundle args = new Bundle();

        args.putSerializable(LeaderboardFragment.ARG_GAME, game);

        args.putSerializable(LeaderboardFragment.ARG_CATEGORY, getCategoryOfIndex(position));
        args.putSerializable(LeaderboardFragment.ARG_LEVEL, getLevelOfIndex(position));
        frag.setArguments(args);

        return frag;
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        if(position < perGameCategories.size())
            return perGameCategories.get(position).name;
        else {
            int mPos = position - perGameCategories.size();

            return perLevelCategories.get(mPos / levels.size()).name + '(' + levels.get(mPos % levels.size()).name + ')';
        }
    }

    @Override
    public int getCount() {
        return perGameCategories.size() + (perLevelCategories.size() * levels.size());
    }
}
