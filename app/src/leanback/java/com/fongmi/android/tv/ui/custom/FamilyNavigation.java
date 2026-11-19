package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.databinding.FamilyNavigationBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class FamilyNavigation {

    private static final int DEFAULT_CATEGORY_COUNT = 5;

    public enum Destination { HOME, LIBRARY, CATEGORY, SEARCH, SCOPED_SEARCH, LIVE, SETTINGS }

    private final FamilyNavigationBinding binding;
    private final Context context;
    private final Map<String, View> categoryItems = new LinkedHashMap<>();
    private final TextView home;
    private final TextView library;
    private final TextView vod;
    private final TextView more;
    private final TextView search;
    private final TextView settings;
    private View selected;
    private boolean showAll;

    public FamilyNavigation(FamilyNavigationBinding binding, BiConsumer<Destination, View> action) {
        this.binding = binding;
        context = binding.getRoot().getContext();
        LinearLayout menu = binding.navigationItems;
        menu.removeView(binding.categoryNavigation);

        home = addNav(menu, context.getString(R.string.home_popular), R.drawable.ic_aetv_house, view -> action.accept(Destination.HOME, view));
        library = addNav(menu, context.getString(R.string.home_keep), R.drawable.ic_home_keep, view -> action.accept(Destination.LIBRARY, view));
        addNavDivider(menu);
        vod = addNav(menu, context.getString(R.string.home_vod), R.drawable.ic_home_vod, view -> {
            showAll = false;
            updateCategoryNavigation();
        });
        setNavIcons(vod, R.drawable.ic_home_vod, R.drawable.ic_aetv_chevron_right);
        menu.addView(binding.categoryNavigation);
        more = addNav(menu, context.getString(R.string.home_expand_categories), R.drawable.ic_aetv_chevron_down, view -> {
            showAll = !showAll;
            updateCategoryNavigation();
            vod.requestFocus();
        });
        more.setTextSize(15);
        addNavDivider(menu);
        search = addNav(menu, context.getString(R.string.home_search), R.drawable.ic_home_search, view -> action.accept(Destination.SEARCH, view));
        search.setOnLongClickListener(view -> {
            action.accept(Destination.SCOPED_SEARCH, view);
            return true;
        });
        addNav(menu, context.getString(R.string.home_live), R.drawable.ic_home_live, view -> action.accept(Destination.LIVE, view));
        settings = addNav(menu, context.getString(R.string.home_setting), R.drawable.ic_home_setting, view -> action.accept(Destination.SETTINGS, view));
        updateCategoryNavigation();
    }

    public View home() {
        return home;
    }

    public View library() {
        return library;
    }

    public View search() {
        return search;
    }

    public View settings() {
        return settings;
    }

    public View category(String id) {
        return categoryItems.get(id);
    }

    public View selected() {
        return selected;
    }

    public boolean hasFocus() {
        return binding.getRoot().hasFocus();
    }

    public void select(View item) {
        if (selected != null) selected.setActivated(false);
        selected = item;
        selected.setActivated(true);
    }

    public void focusSelected() {
        (selected != null && selected.isShown() ? selected : vod).requestFocus();
    }

    public void setTypes(List<Class> types, BiConsumer<Class, View> onClick) {
        LinearLayout categories = binding.categoryNavigation;
        categories.removeAllViews();
        categoryItems.clear();
        if (types.isEmpty()) showAll = false;
        for (Class type : types) {
            TextView item = addNav(categories, type.getTypeName(), 0, view -> onClick.accept(type, view));
            indentCategory(item);
            categoryItems.put(type.getTypeId(), item);
        }
        updateCategoryNavigation();
    }

    private TextView addNav(LinearLayout menu, CharSequence label, int icon, View.OnClickListener click) {
        TextView item = (TextView) LayoutInflater.from(context).inflate(R.layout.item_aetv_navigation, menu, false);
        item.setText(label);
        setNavIcons(item, icon, 0);
        item.setOnClickListener(click);
        menu.addView(item);
        return item;
    }

    private void setNavIcons(TextView item, int start, int end) {
        item.setCompoundDrawablesRelative(navIcon(start), null, navIcon(end), null);
        item.setCompoundDrawablePadding(ResUtil.dp2px(12));
    }

    private Drawable navIcon(int resource) {
        if (resource == 0) return null;
        Drawable icon = context.getDrawable(resource).mutate();
        int size = ResUtil.dp2px(20);
        icon.setBounds(0, 0, size, size);
        return icon;
    }

    private void indentCategory(TextView item) {
        item.setPaddingRelative(ResUtil.dp2px(44), item.getPaddingTop(), item.getPaddingEnd(), item.getPaddingBottom());
        item.setTextSize(15);
    }

    private void addNavDivider(LinearLayout menu) {
        View line = new View(context);
        line.setBackgroundColor(context.getColor(R.color.aetv_divider));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ResUtil.dp2px(1));
        params.setMargins(ResUtil.dp2px(12), ResUtil.dp2px(7), ResUtil.dp2px(12), ResUtil.dp2px(7));
        menu.addView(line, params);
    }

    private void updateCategoryNavigation() {
        LinearLayout categories = binding.categoryNavigation;
        int count = categories.getChildCount();
        for (int i = 0; i < count; i++) {
            categories.getChildAt(i).setVisibility(showAll || i < DEFAULT_CATEGORY_COUNT ? View.VISIBLE : View.GONE);
        }
        more.setVisibility(count > DEFAULT_CATEGORY_COUNT ? View.VISIBLE : View.GONE);
        more.setText(showAll ? R.string.home_retract_categories : R.string.home_expand_categories);
        setNavIcons(more, showAll ? R.drawable.ic_aetv_chevron_up : R.drawable.ic_aetv_chevron_down, 0);
    }

}
