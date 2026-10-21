package com.kamron.pogoiv.utils.fractions;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import static android.content.Context.WINDOW_SERVICE;


public class FractionManager {

    // Themed layout to be used to inflate Fraction layouts
    private Context themedContext;
    // System window manager
    private WindowManager windowManager;
    // The root view attached to the window manager
    private View floatingView;
    // LayoutParams to position the floating view inside the WindowManager
    private WindowManager.LayoutParams layoutParams;
    // ViewGroup where the Fraction will be attached
    private ViewGroup containerView;
    // Current Fraction instance
    private Fraction currentFraction;


    public FractionManager(@NonNull Context context,
                           @StyleRes int themeResId,
                           @NonNull WindowManager.LayoutParams layoutParams,
                           @NonNull View floatingView,
                           @NonNull ViewGroup containerView) {
        themedContext = new ContextThemeWrapper(context, themeResId);
        windowManager = (WindowManager) context.getSystemService(WINDOW_SERVICE);
        this.layoutParams = layoutParams;
        this.floatingView = floatingView;
        this.containerView = containerView;
    }

    public void show(Fraction fraction) {
        remove();

        View fractionRootView = addFractionView(fraction);
        fraction.setFractionManager(this);
        fraction.onCreate(fractionRootView);
        currentFraction = fraction;

        layoutParams.gravity = currentFraction.getAnchor().getGravity();
        int offset = currentFraction.getVerticalOffset(themedContext.getResources().getDisplayMetrics());
        updateFloatingViewVerticalOffset(offset);
    }

    public void remove() {
        if (hasFractionViewAttached()) {
            removeFractionView();
        }
        if (currentFraction != null) {
            currentFraction.onDestroy();
            currentFraction = null;
        }
    }

    private boolean hasFractionViewAttached() {
        return containerView.getChildCount() > 0;
    }

    public boolean currentFractionIsInstanceOf(@NonNull Class<? extends Fraction> fractionClass) {
        return currentFraction != null && currentFraction.getClass().equals(fractionClass);
    }

    private void removeFractionView() {
        containerView.removeView(containerView.getChildAt(0));
    }

    private View addFractionView(@NonNull Fraction fraction) {
        // Inflate WITHOUT attaching, then attach manually and return the inflated
        // view itself. The two-arg inflate() returns the CONTAINER (a FrameLayout),
        // and the fractions hand that root to generated ViewBinding classes, which
        // cast it to the layout's root type — passing the container crashed every
        // fraction screen with a ClassCastException once the ButterKnife loose
        // binding was replaced by ViewBinding.
        View fractionView = LayoutInflater.from(themedContext)
                .inflate(fraction.getLayoutResId(), containerView, false);
        containerView.addView(fractionView);
        return fractionView;
    }

    void updateFloatingViewVerticalOffset(int offsetPx) {
        layoutParams.y = offsetPx;
        windowManager.updateViewLayout(floatingView, layoutParams);
    }

    int getCurrentFloatingViewVerticalOffset() {
        return layoutParams.y;
    }

}
