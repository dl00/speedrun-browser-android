package danb.speedrunbrowser.views;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import danb.speedrunbrowser.R;

public class SimpleTabStrip extends LinearLayout implements ViewPager.OnPageChangeListener {

    private HorizontalScrollView mHsv;
    private LinearLayout mLayout;

    private ViewPager mPager;

    private int mHighlightTab = 0;

    public SimpleTabStrip(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOrientation(VERTICAL);

        mHsv = new HorizontalScrollView(context);
        mHsv.setHorizontalScrollBarEnabled(false);

        mLayout= new LinearLayout(context);
        mLayout.setOrientation(HORIZONTAL);

        mHsv.addView(mLayout);

        addView(mHsv);
    }

    public void setup(ViewPager vp) {
        mPager = vp;
        mPager.setOnPageChangeListener(this);

        applyTabs();

        onPageSelected(0);
    }

    private void styleTab(TextView tv) {
        tv.setTypeface(tv.getTypeface(), Typeface.BOLD);
        tv.setAllCaps(true);

        tv.setHeight(getResources().getDimensionPixelSize(R.dimen.tab_height));
        tv.setGravity(Gravity.CENTER_VERTICAL);

        tv.setPadding(getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0, getResources().getDimensionPixelSize(R.dimen.half_fab_margin), 0);

        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);
        lp.rightMargin = getResources().getDimensionPixelSize(R.dimen.half_fab_margin);
        tv.setLayoutParams(lp);
    }

    private void applyTabs() {
        mLayout.removeAllViews();

        PagerAdapter adapter = mPager.getAdapter();

        for(int i = 0;i < adapter.getCount();i++) {
            TextView tv = new TextView(getContext());

            tv.setText(adapter.getPageTitle(i));
            styleTab(tv);

            final int pos = i;

            tv.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    mPager.setCurrentItem(pos);
                }
            });

            if(adapter instanceof IconPagerAdapter) {
                Drawable icon = ((IconPagerAdapter)adapter).getPageIcon(i);

                ImageView iv = new ImageView(getContext());
                iv.setImageDrawable(icon);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        getContext().getResources().getDimensionPixelSize(R.dimen.tab_icon_width),
                        getContext().getResources().getDimensionPixelSize(R.dimen.tab_icon_height)
                );

                iv.setLayoutParams(lp);

                LinearLayout verticalLayout = new LinearLayout(getContext());
                verticalLayout.setOrientation(VERTICAL);
                verticalLayout.setGravity(Gravity.CENTER);

                verticalLayout.addView(iv);
                verticalLayout.addView(tv);

                mLayout.addView(verticalLayout);
            }
            else {
                mLayout.addView(tv);
            }
        }
    }

    private int getCenterScrollPosition(HorizontalScrollView hsv, int pos) {
        View child = hsv.getChildAt(pos);

        return child.getLeft() + child.getWidth() / 2 - hsv.getWidth() / 2;
    }

    private void setScroll(int pos, float offset) {
        // we want the tab to be as center aligned as possible.
        int x1 = getCenterScrollPosition(mHsv, pos);

        int x2 = x1;
        if(mLayout.getChildCount() > pos + 1 && (pos == -1 || pos + 1 >= mLayout.getChildCount()))
            x2 = getCenterScrollPosition(mHsv, pos);

        mHsv.scrollTo(x1 + (int)Math.floor((float)(x2 - x1) * offset), 0);
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        setScroll(position, positionOffset);
    }

    @Override
    public void onPageSelected(int position) {
        mLayout.getChildAt(mHighlightTab).setBackground(new ColorDrawable(getResources().getColor(android.R.color.transparent)));
        mHighlightTab = position;
        mLayout.getChildAt(mHighlightTab).setBackground(new ColorDrawable(getResources().getColor(R.color.colorAccent)));
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    public abstract class IconPagerAdapter extends PagerAdapter {
        public abstract Drawable getPageIcon(int position);
    }
}
