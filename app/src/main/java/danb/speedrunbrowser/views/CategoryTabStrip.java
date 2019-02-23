package danb.speedrunbrowser.views;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Typeface;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import danb.speedrunbrowser.BuildConfig;
import danb.speedrunbrowser.R;
import danb.speedrunbrowser.api.objects.Category;
import danb.speedrunbrowser.api.objects.Game;
import danb.speedrunbrowser.api.objects.Level;
import danb.speedrunbrowser.utils.LeaderboardPagerAdapter;

public class CategoryTabStrip extends LinearLayout implements ViewPager.OnPageChangeListener {

    private Game mGame;

    private HorizontalScrollView mHsvCategory;
    private LinearLayout mLayoutCategory;

    private HorizontalScrollView mHsvLevel;
    private LinearLayout mLayoutLevel;

    private ViewPager mPager;
    private LeaderboardPagerAdapter mPagerAdapter;

    public CategoryTabStrip(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOrientation(VERTICAL);

        mHsvCategory = new HorizontalScrollView(context);
        mHsvLevel = new HorizontalScrollView(context);

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

    public void setup(Game game, ViewPager vp, FragmentManager fm) {
        mPager = vp;
        mPager.setOnPageChangeListener(this);

        mGame = game;

        LeaderboardPagerAdapter leaderboardPagerAdapter = new LeaderboardPagerAdapter(fm, mGame);
        mPager.setAdapter(leaderboardPagerAdapter);

        applyTabs();
    }

    public void selectLeaderboard(Category category, Level level) {
        selectLeaderboardIndex(mPagerAdapter.indexOf(category, level));
    }

    private void selectLeaderboardIndex(int position) {

    }

    private void styleTab(TextView tv) {
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setAllCaps(true);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = getResources().getDimensionPixelSize(R.dimen.fab_margin);
        lp.rightMargin = getResources().getDimensionPixelSize(R.dimen.fab_margin);
        tv.setLayoutParams(lp);
    }

    private void applyTabs() {
        mLayoutCategory.removeAllViews();
        mLayoutLevel.removeAllViews();

        for(final Category category : mGame.categories) {
            TextView tv = new TextView(getContext());

            tv.setText(category.name);
            styleTab(tv);

            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    selectLeaderboard(category, null);
                }
            });

            mLayoutCategory.addView(tv);
        }


        for(final Level level : mGame.levels) {
            TextView tv = new TextView(getContext());

            tv.setText(level.name);
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

    private void setScroll(int categoryPos, float categoryOffset, int levelPos, float levelOffset) {
        // we want the tab to be as center aligned as possible.
        View categoryChild = mLayoutCategory.getChildAt(categoryPos);
        int categoryX = categoryChild.getLeft() - categoryChild.getWidth() / 2;

        mHsvCategory.scrollTo(categoryX, 0);

        if(levelPos != -1 && mLayoutLevel.getChildAt(levelPos) != null) {
            View levelChild = mLayoutLevel.getChildAt(levelPos);
            int levelX = levelChild.getLeft() - levelChild.getWidth() / 2;

            mHsvLevel.scrollTo(levelX, 0);
        }

    }

    private void hideLevelsStrip() {
        if(mHsvLevel.getVisibility() == GONE)
            return;

        // animate out
        mHsvLevel.animate()
                .translationY(0)
                .setListener(new Animator.AnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animator) {}

                    @Override
                    public void onAnimationEnd(Animator animator) {
                        mHsvLevel.setVisibility(GONE);
                    }

                    @Override
                    public void onAnimationCancel(Animator animator) {}

                    @Override
                    public void onAnimationRepeat(Animator animator) {}
                });
    }

    private void showLevelsStrip() {
        if(mHsvLevel.getVisibility() == VISIBLE)
            return;

        // animate in
        mHsvLevel.setTranslationY(-mHsvLevel.getHeight());
        mHsvLevel.setVisibility(VISIBLE);

        mHsvLevel.animate()
                .translationY(0)
                .setListener(null);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        setScroll(mPager.getCurrentItem(), positionOffset, mPager.getCurrentItem(), 0);
    }

    @Override
    public void onPageSelected(int position) {
        selectLeaderboardIndex(position);
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        setScroll(mPager.getCurrentItem(), 0, mPager.getCurrentItem(), 0);
    }
}
