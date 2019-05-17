package danb.speedrunbrowser.views;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;
import danb.speedrunbrowser.GameListActivity;
import danb.speedrunbrowser.R;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.api.objects.Variable;
import danb.speedrunbrowser.utils.LeaderboardPagerAdapter;

public class CategoryTabStrip extends LinearLayout implements ViewPager.OnPageChangeListener {

    private Game mGame;

    private HorizontalScrollView mHsvCategory;
    private LinearLayout mLayoutCategory;

    private HorizontalScrollView mHsvLevel;
    private LinearLayout mLayoutLevel;

    private ViewPager mPager;
    private LeaderboardPagerAdapter mPagerAdapter;

    private int mHighlightCategory = 0;
    private int mHighlightLevel = 0;

    public CategoryTabStrip(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOrientation(VERTICAL);

        mHsvCategory = new HorizontalScrollView(context);
        mHsvLevel = new HorizontalScrollView(context);

        mHsvCategory.setHorizontalScrollBarEnabled(false);
        mHsvLevel.setHorizontalScrollBarEnabled(false);

        mLayoutCategory = new LinearLayout(context);
        mLayoutLevel = new LinearLayout(context);

        mLayoutCategory.setOrientation(HORIZONTAL);
        mLayoutCategory.setOrientation(HORIZONTAL);

        mHsvCategory.addView(mLayoutCategory);
        mHsvLevel.addView(mLayoutLevel);

        mHsvLevel.setVisibility(GONE);

        addView(mHsvCategory);
        addView(mHsvLevel);
    }

    public void setup(Game game, Variable.VariableSelections selections, ViewPager vp, FragmentManager fm) {
        mPager = vp;
        mPager.addOnPageChangeListener(this);

        mGame = game;

        mPagerAdapter = new LeaderboardPagerAdapter(fm, mGame, selections, mPager);
        mPager.setAdapter(mPagerAdapter);

        applyTabs();

        onPageSelected(0);
    }

    public void selectLeaderboard(Category category, Level level) {
        mPager.setCurrentItem(mPagerAdapter.indexOf(category, level));
    }

    private void styleTab(TextView tv) {
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setAllCaps(true);

        tv.setHeight(getResources().getDimensionPixelSize(R.dimen.tab_height));
        tv.setGravity(Gravity.CENTER_VERTICAL);

        tv.setPadding(getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0, getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);
        lp.rightMargin = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);
        tv.setLayoutParams(lp);
    }

    private void applyTabs() {
        mLayoutCategory.removeAllViews();
        mLayoutLevel.removeAllViews();

        for(final Category category : mPagerAdapter.getSortedCategories()) {
            TextView tv = new TextView(getContext());

            tv.setText(category.getName());
            styleTab(tv);

            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Level l = mPagerAdapter.getLevelOfIndex(mPager.getCurrentItem());

                    selectLeaderboard(category, l == null && mGame.getLevels() != null && !mGame.getLevels().isEmpty() ? mGame.getLevels().get(0) : l);
                }
            });

            mLayoutCategory.addView(tv);
        }

        if(mGame.getLevels() != null) {
            for(final Level level : mGame.getLevels()) {
                TextView tv = new TextView(getContext());

                tv.setText(level.getName());
                styleTab(tv);

                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        selectLeaderboard(mPagerAdapter.getCategoryOfIndex(mPager.getCurrentItem()), level);
                    }
                });

                mLayoutLevel.addView(tv);
            }
        }
    }

    private int getCenterScrollPosition(HorizontalScrollView hsv, View child) {
        return child.getLeft() + child.getWidth() / 2 - hsv.getWidth() / 2;
    }

    private void setScroll(int categoryPos, int levelPos, float offset) {
        // we want the tab to be as center aligned as possible.
        View categoryChild = mLayoutCategory.getChildAt(categoryPos);
        int categoryX1 = getCenterScrollPosition(mHsvCategory, categoryChild);

        int categoryX2 = categoryX1;
        if(mLayoutCategory.getChildCount() > categoryPos + 1 && (levelPos == -1 || levelPos + 1 >= mLayoutLevel.getChildCount()))
            categoryX2 = getCenterScrollPosition(mHsvCategory, mLayoutCategory.getChildAt(categoryPos + 1));

        mHsvCategory.scrollTo(categoryX1 + (int)Math.floor((float)(categoryX2 - categoryX1) * offset), 0);

        if(levelPos != -1 && mLayoutLevel.getChildAt(levelPos) != null) {
            View levelChild = mLayoutLevel.getChildAt(levelPos);
            int levelX1 = getCenterScrollPosition(mHsvLevel, levelChild);

            int levelX2 = getCenterScrollPosition(mHsvLevel, mLayoutLevel.getChildAt((levelPos + 1) % mLayoutLevel.getChildCount()));

            mHsvLevel.scrollTo(levelX1 + (int)Math.floor((float)(levelX2 - levelX1) * offset), 0);
        }

    }

    private void hideLevelsStrip() {
        if(mHsvLevel.getVisibility() == GONE)
            return;

        mHsvLevel.setVisibility(GONE);
    }

    private void showLevelsStrip() {
        if(mHsvLevel.getVisibility() == VISIBLE)
            return;

        mHsvLevel.setVisibility(VISIBLE);
    }

    public LeaderboardPagerAdapter getPagerAdapter() {
        return mPagerAdapter;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if(position < mPagerAdapter.getPerGameCategorySize()) {
            setScroll(position, -1, positionOffset);
        }
        else {
            setScroll(mPagerAdapter.getPerGameCategorySize() + (position - mPagerAdapter.getPerGameCategorySize()) / mGame.getLevels().size(),
                    (position - mPagerAdapter.getPerGameCategorySize()) % mGame.getLevels().size(), positionOffset);
        }
    }

    @Override
    public void onPageSelected(int position) {
        if(position < mPagerAdapter.getPerGameCategorySize()) {
            mLayoutCategory.getChildAt(mHighlightCategory).setBackground(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
            mHighlightCategory = position;
            mLayoutCategory.getChildAt(mHighlightCategory).setBackground(new ColorDrawable(getResources().getColor(R.color.colorAccent)));
            hideLevelsStrip();
        }
        else {
            mLayoutCategory.getChildAt(mHighlightCategory).setBackground(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
            mLayoutLevel.getChildAt(mHighlightLevel).setBackground(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
            mHighlightCategory = mPagerAdapter.getPerGameCategorySize() + (position - mPagerAdapter.getPerGameCategorySize()) / mGame.getLevels().size();
            mHighlightLevel = (position - mPagerAdapter.getPerGameCategorySize()) % mGame.getLevels().size();
            mLayoutCategory.getChildAt(mHighlightCategory).setBackground(new ColorDrawable(getResources().getColor(R.color.colorAccent)));
            mLayoutLevel.getChildAt(mHighlightLevel).setBackground(new ColorDrawable(getResources().getColor(R.color.colorAccent)));
            showLevelsStrip();
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }
}
