package danb.speedrunbrowser.utils;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import danb.speedrunbrowser.ItemListFragment;
import danb.speedrunbrowser.LeaderboardFragment;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.api.objects.Variable;

public class LeaderboardPagerAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener {

    private final Game game;

    private final List<Category> perGameCategories;
    private final List<Category> perLevelCategories;
    private final List<Level> levels;

    private Variable.VariableSelections filterSelections;

    private final LeaderboardFragment[] existingFragments;

    private int selectedFragment = 0;

    public LeaderboardPagerAdapter(FragmentManager fm, Game game, Variable.VariableSelections selections, ViewPager vp) {
        super(fm);

        this.game = game;
        filterSelections = selections;

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

        existingFragments = new LeaderboardFragment[getCount()];

        vp.addOnPageChangeListener(this);
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
            return levels.get(mpos % levels.size());
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

        return getPerGameCategorySize() + index;
    }

    @Override
    public Fragment getItem(int position) {
        LeaderboardFragment frag = new LeaderboardFragment();
        Bundle args = new Bundle();

        args.putSerializable(LeaderboardFragment.ARG_GAME, game);

        args.putSerializable(LeaderboardFragment.ARG_CATEGORY, getCategoryOfIndex(position));
        args.putSerializable(LeaderboardFragment.ARG_LEVEL, getLevelOfIndex(position));

        frag.setArguments(args);

        if(filterSelections != null)
            frag.setFilter(filterSelections);

        existingFragments[position] = frag;

        return frag;
    }

    @Override
    public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        if(object instanceof LeaderboardFragment && ((LeaderboardFragment)object).getFilter() == null) {
            ((LeaderboardFragment)object).setFilter(filterSelections);
            super.setPrimaryItem(container, position, object);
        }
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

    public int getPerGameCategorySize() {
        return perGameCategories.size();
    }

    public List<Category> getSortedCategories() {
        List<Category> sortedCategories = new ArrayList<>(perGameCategories);
        sortedCategories.addAll(perLevelCategories);

        return sortedCategories;
    }

    public void notifyFilterChanged() {
        if(existingFragments[selectedFragment] != null)
            existingFragments[selectedFragment].notifyFilterChanged();
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) { }

    @Override
    public void onPageSelected(int position) {
        selectedFragment = position;
        notifyFilterChanged();
    }

    @Override
    public void onPageScrollStateChanged(int state) {}
}
