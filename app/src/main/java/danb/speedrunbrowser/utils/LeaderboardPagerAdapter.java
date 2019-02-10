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

    @Override
    public Fragment getItem(int position) {
        Fragment frag = new LeaderboardFragment(game);
        Bundle args = new Bundle();

        if(position < perGameCategories.size()) {
            args.putString(LeaderboardFragment.ARG_CATEGORY_ID, perGameCategories.get(position).id);
        }
        else {
            int mpos = position - perGameCategories.size();
            args.putString(LeaderboardFragment.ARG_CATEGORY_ID, perLevelCategories.get(mpos / levels.size()).id);
            args.putString(LeaderboardFragment.ARG_CATEGORY_ID, levels.get(mpos % levels.size()).id);
        }

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
