package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.ViewHolderTask;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Cache;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.HotTvViewModel;
import com.fongmi.android.tv.model.HotTvViewModel.Chart;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.fragment.FolderFragment;
import com.fongmi.android.tv.ui.fragment.KeepFragment;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.ExitConfirmDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.presenter.ProgressPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.web.HomeWebController;
import com.fongmi.android.tv.web.WebHomeViewport;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, VodPresenter.OnClickListener, HomeWebController.Listener {

    private static final String TV_NORMAL = "tv-normal";
    private static final String TV_TOOLBAR_HIDDEN = "tv-toolbar-hidden";
    private static final String TV_OVERLAY = "tv-overlay";
    private static final String TV_FULL = "tv-full";

    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private SiteViewModel mViewModel;
    private static final int DEFAULT_CATEGORY_COUNT = 5;

    private View mHotNav;
    private TextView mVodNav;
    private TextView mMoreNav;
    private boolean mCategoriesOpen = true;
    private boolean mShowAllCategories;
    private KeepFragment mLibrary;
    private View mSelectedNav;
    private FolderFragment mCategory;
    private boolean mCategoryFilter;
    private HomeWebController mWeb;
    private WebView mHomeWeb;
    private Result mResult;
    private Result mHomeResult;
    private List<Vod> mHotTv;
    private HotTvViewModel mHotTvModel;
    private View mSelectedChart;
    private boolean mLoadingSite;
    private Clock mClock;
    private String webChromeMode = TV_NORMAL;
    private String webDefaultChromeMode = TV_FULL;
    private boolean webToolbarVisible = true;
    private boolean loadingHomeCategory;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_App);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        SpiderDebug.log("startup", "home initView start cost=%sms", System.currentTimeMillis() - App.time());
        mResult = Result.empty();
        mHomeResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        mBinding.progressLayout.showProgress();
        setRecyclerView();
        setNavigation();
        setViewModel();
        runAfterFirstFrame(this::initAfterFirstFrame);
        SpiderDebug.log("startup", "home initView end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void initAfterFirstFrame() {
        SpiderDebug.log("startup", "home first frame cost=%sms", System.currentTimeMillis() - App.time());
        App.post(this::initConfig, 80);
        App.post(() -> PermissionUtil.requestFile(this, allGranted -> PermissionUtil.requestNotify(this)), 1800);
        App.post(() -> DLNARendererService.start(this), 2500);
    }

    private void runAfterFirstFrame(Runnable runnable) {
        View root = mBinding.getRoot();
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(this);
                root.post(runnable);
                return true;
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        mBinding.toolbar.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            syncNativeContentInset();
            syncWebOverlayLayout();
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                updateToolbarVisibility(isTopRow(position));
            }
        });
    }

    private void updateToolbarVisibility(boolean visible) {
        boolean webHomeVisible = mBinding.webOverlay.getVisibility() == View.VISIBLE;
        mBinding.toolbar.setVisibility(visible && webToolbarVisible && webHomeVisible ? View.VISIBLE : View.GONE);
        syncNativeContentInset();
        syncWebOverlayLayout();
    }

    private void syncNativeContentInset() {
        int top = isToolbarVisible() ? toolbarHeight() : 0;
        if (mBinding.nativeContent.getPaddingTop() == top) return;
        mBinding.nativeContent.setPadding(mBinding.nativeContent.getPaddingLeft(), top, mBinding.nativeContent.getPaddingRight(), mBinding.nativeContent.getPaddingBottom());
    }

    private void syncWebOverlayLayout() {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mBinding.webOverlay.getLayoutParams();
        int top = constrainWebBelowToolbar() ? toolbarHeight() : 0;
        if (params.topMargin == top) return;
        params.topMargin = top;
        mBinding.webOverlay.setLayoutParams(params);
    }

    private boolean constrainWebBelowToolbar() {
        return (TV_NORMAL.equals(webChromeMode) || TV_OVERLAY.equals(webChromeMode)) && isToolbarVisible();
    }

    private boolean isToolbarVisible() {
        return mBinding.toolbar.getVisibility() == View.VISIBLE;
    }

    private int toolbarHeight() {
        int height = mBinding.toolbar.getHeight();
        if (height <= 0) height = mBinding.toolbar.getMeasuredHeight();
        return height > 0 ? height : ResUtil.dp2px(80);
    }

    private boolean isTopRow(int position) {
        return position <= 0;
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setWebView() {
        SpiderDebug.log("startup", "webview create start cost=%sms", System.currentTimeMillis() - App.time());
        mWeb = new HomeWebController(this, getHomeWeb(), this);
        mWeb.setViewport(tvViewport(webChromeMode));
        SpiderDebug.log("startup", "webview create end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void ensureWebView() {
        if (mWeb == null) setWebView();
    }

    private WebView getHomeWeb() {
        if (mHomeWeb != null) return mHomeWeb;
        mHomeWeb = new WebView(this);
        mHomeWeb.setFocusable(true);
        mHomeWeb.setFocusableInTouchMode(true);
        mHomeWeb.setVisibility(View.GONE);
        mBinding.webOverlay.addView(mHomeWeb, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        return mHomeWeb;
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            boolean categoryResult = isHomeCategoryResult(result);
            mLoadingSite = false;
            if (!categoryResult) {
                Cache.clear().put(result);
                setTypes(mHomeResult = result);
            }
            mResult = result;
            if (mHotTv != null && mHotTv.isEmpty()) {
                mAdapter.remove("progress");
                addVideo(result);
            }
        });
        mHotTvModel = new ViewModelProvider(this).get(HotTvViewModel.class);
        setChartTabs();
        mHotTvModel.getItems().observe(this, this::onHotTv);
        mHotTvModel.load(mHotTvModel.getChart());
    }

    private void setChartTabs() {
        for (Chart chart : Chart.values()) {
            TextView tab = (TextView) getLayoutInflater().inflate(R.layout.item_aetv_chart, mBinding.chartTabs, false);
            tab.setText(chart.label);
            tab.setOnClickListener(view -> {
                activateChart(view);
                mHotTvModel.load(chart);
            });
            mBinding.chartTabs.addView(tab);
            if (mHotTvModel.getChart() == chart) activateChart(tab);
        }
    }

    private void activateChart(View tab) {
        if (mSelectedChart != null) mSelectedChart.setActivated(false);
        mSelectedChart = tab;
        tab.setActivated(true);
    }

    private void onHotTv(List<Vod> items) {
        mHotTv = items;
        clearRecommendRows();
        if (items == null || items.isEmpty() && mLoadingSite) mAdapter.add("progress");
        else if (items.isEmpty()) addVideo(mResult);
        else addGrid(items, Style.rect());
    }

    private boolean isHomeCategoryResult(Result result) {
        return loadingHomeCategory && result.getTypes().isEmpty();
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
    }

    private void initConfig() {
        SpiderDebug.log("startup", "config load start cost=%sms", System.currentTimeMillis() - App.time());
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                SpiderDebug.log("startup", "config load success cost=%sms", System.currentTimeMillis() - App.time());
                showContent();
            }

            @Override
            public void error(String msg) {
                SpiderDebug.log("startup", "config load error cost=%sms msg=%s", System.currentTimeMillis() - App.time(), msg);
                Notify.show(msg);
                showContent();
            }
        };
    }

    private void showContent() {
        SpiderDebug.log("startup", "home showContent start cost=%sms", System.currentTimeMillis() - App.time());
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setTitle();
        setLogo();
        getVideo();
        setFocus();
        App.post(this::prewarmWebView, 1500);
        SpiderDebug.log("startup", "home showContent end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void prewarmWebView() {
        if (isFinishing() || mWeb != null) return;
        boolean hasWebHome = VodConfig.get().getSites().stream().anyMatch(Site::hasHomePage);
        if (!hasWebHome) return;
        SpiderDebug.log("startup", "webview prewarm start cost=%sms", System.currentTimeMillis() - App.time());
        ensureWebView();
        SpiderDebug.log("startup", "webview prewarm end cost=%sms", System.currentTimeMillis() - App.time());
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        if (!mBinding.homeNavigation.hasFocus() && !requestContentFocus()) mHotNav.requestFocus();
    }

    private void getVideo() {
        getVideo(true);
    }

    private void getVideo(boolean forceNative) {
        if (!forceNative && getHome().hasHomePage()) {
            ensureWebView();
        }
        if (!forceNative && mWeb != null && mWeb.load(getHome())) {
            mBinding.recycler.setVisibility(View.GONE);
            mBinding.progressLayout.showContent();
            showWebOverlay();
            return;
        }
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        applyTvChrome(TV_NORMAL);
        if (mLibrary == null) showHot();
        mBinding.categoryNavigation.removeAllViews();
        mShowAllCategories = false;
        updateCategoryNavigation();
        mBinding.recycler.setVisibility(View.VISIBLE);
        mResult = Result.empty();
        mHomeResult = Result.empty();
        loadingHomeCategory = false;
        mLoadingSite = true;
        if (mHotTv != null && mHotTv.isEmpty()) {
            clearRecommendRows();
            mAdapter.add("progress");
        }
        mViewModel.homeContent();
    }

    private void showWebOverlay() {
        mBinding.webOverlay.setVisibility(View.VISIBLE);
        updateToolbarVisibility(webToolbarVisible);
    }

    private void hideWebOverlay() {
        mBinding.webOverlay.setVisibility(View.GONE);
    }

    private void setNavigation() {
        LinearLayout menu = mBinding.navigationItems;
        LinearLayout categories = mBinding.categoryNavigation;
        menu.removeView(categories);

        mHotNav = addNav(menu, getString(R.string.home_popular), R.drawable.ic_aetv_house, view -> showHot());
        addNav(menu, getString(R.string.home_keep), R.drawable.ic_home_keep, this::showLibrary);
        addNavDivider(menu);
        mVodNav = addNav(menu, getString(R.string.home_vod), R.drawable.ic_home_vod, view -> {
            mCategoriesOpen = !mCategoriesOpen;
            updateCategoryNavigation();
        });
        setNavIcons(mVodNav, R.drawable.ic_home_vod, R.drawable.ic_aetv_chevron_right);
        menu.addView(categories);
        mMoreNav = addNav(menu, getString(R.string.home_expand_categories), R.drawable.ic_aetv_chevron_down, view -> {
            mShowAllCategories = !mShowAllCategories;
            updateCategoryNavigation();
        });
        mMoreNav.setTextSize(15);
        addNavDivider(menu);

        TextView search = addNav(menu, getString(R.string.home_search), R.drawable.ic_home_search, view -> SearchActivity.start(this));
        search.setOnLongClickListener(view -> {
            SearchActivity.start(this, "", getHome().getKey());
            return true;
        });
        addNav(menu, getString(R.string.home_live), R.drawable.ic_home_live, view -> LiveActivity.start(this));
        addNav(menu, getString(R.string.home_setting), R.drawable.ic_home_setting, view -> SettingActivity.start(this));

        updateCategoryNavigation();
        selectNavigation(mHotNav);
        mHotNav.requestFocus();
    }

    private TextView addNav(LinearLayout menu, CharSequence label, int icon, View.OnClickListener click) {
        TextView item = (TextView) getLayoutInflater().inflate(R.layout.item_aetv_navigation, menu, false);
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
        Drawable icon = getDrawable(resource).mutate();
        int size = ResUtil.dp2px(20);
        icon.setBounds(0, 0, size, size);
        return icon;
    }

    private void indentCategory(TextView item) {
        item.setPaddingRelative(ResUtil.dp2px(44), item.getPaddingTop(), item.getPaddingEnd(), item.getPaddingBottom());
        item.setTextSize(15);
    }

    private void addNavDivider(LinearLayout menu) {
        View line = new View(this);
        line.setBackgroundColor(0x385E7691);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ResUtil.dp2px(1));
        params.setMargins(ResUtil.dp2px(12), ResUtil.dp2px(7), ResUtil.dp2px(12), ResUtil.dp2px(7));
        menu.addView(line, params);
    }

    private void updateCategoryNavigation() {
        LinearLayout categories = mBinding.categoryNavigation;
        int count = categories.getChildCount();
        categories.setVisibility(mCategoriesOpen ? View.VISIBLE : View.GONE);
        for (int i = 0; i < count; i++) {
            categories.getChildAt(i).setVisibility(mShowAllCategories || i < DEFAULT_CATEGORY_COUNT ? View.VISIBLE : View.GONE);
        }
        mMoreNav.setVisibility(mCategoriesOpen && count > DEFAULT_CATEGORY_COUNT ? View.VISIBLE : View.GONE);
        mMoreNav.setText(mShowAllCategories ? R.string.home_retract_categories : R.string.home_expand_categories);
        setNavIcons(mMoreNav, mShowAllCategories ? R.drawable.ic_aetv_chevron_up : R.drawable.ic_aetv_chevron_down, 0);
    }

    private void setTypes(Result result) {
        LinearLayout categories = mBinding.categoryNavigation;
        categories.removeAllViews();
        for (Class type : result.getTypes()) {
            TextView nav = addNav(categories, type.getTypeName(), 0, view -> showCategory(type, view));
            indentCategory(nav);
            if (mCategory != null && type.getTypeId().equals(mCategory.getType().getTypeId())) selectNavigation(nav);
        }
        updateCategoryNavigation();
    }

    private void selectNavigation(View item) {
        if (mSelectedNav != null) mSelectedNav.setActivated(false);
        mSelectedNav = item;
        item.setActivated(true);
    }

    private void showCategory(Class type, View nav) {
        if (getSupportFragmentManager().isStateSaved()) return;
        if (mCategory != null && mSelectedNav == nav) return;
        mLibrary = null;
        mCategory = FolderFragment.newInstance(getHome().getKey(), type);
        mCategoryFilter = false;
        showPane(mCategory, nav);
    }

    private void showLibrary(View nav) {
        if (mLibrary != null || getSupportFragmentManager().isStateSaved()) return;
        mCategory = null;
        mCategoryFilter = false;
        mLibrary = new KeepFragment();
        showPane(mLibrary, nav);
    }

    private void showPane(Fragment pane, View nav) {
        selectNavigation(nav);
        mBinding.homeCategory.setVisibility(View.VISIBLE);
        mBinding.homePopular.setVisibility(View.GONE);
        getSupportFragmentManager().beginTransaction().replace(R.id.homeCategory, pane).commitNow();
    }

    private void showHot() {
        Fragment prior = getSupportFragmentManager().findFragmentById(R.id.homeCategory);
        // A site change can arrive while Settings is in front. The category pane is transient.
        if (prior != null) getSupportFragmentManager().beginTransaction().remove(prior).commitNowAllowingStateLoss();
        mLibrary = null;
        mCategory = null;
        mCategoryFilter = false;
        selectNavigation(mHotNav);
        mBinding.homeCategory.setVisibility(View.GONE);
        mBinding.homePopular.setVisibility(View.VISIBLE);
    }

    private void addVideo(Result result) {
        if (!loadingHomeCategory && result.getList().isEmpty() && !result.getTypes().isEmpty()) {
            Class type = result.getTypes().get(0);
            SpiderDebug.log("home", "home list empty, auto open first category key=%s tid=%s", getHome().getKey(), type.getTypeId());
            loadingHomeCategory = true;
            mAdapter.add("progress");
            mViewModel.categoryContent(getHome().getKey(), type.getTypeId(), "1", true, new java.util.HashMap<>());
            return;
        }
        loadingHomeCategory = false;
        Style style = result.getStyle(getHome().getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
    }

    private void clearRecommendRows() {
        mAdapter.clear();
    }

    private void addGrid(List<Vod> items, Style style) {
        List<ListRow> rows = new ArrayList<>();
        int inset = getResources().getDimensionPixelSize(R.dimen.home_nav_width);
        VodPresenter presenter = new VodPresenter(this, style, Product.getSpecWithInset(style, inset));
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
            adapter.addAll(0, part);
            rows.add(new ListRow(adapter));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private void setLogo() {
        ImgUtil.logo(mBinding.logo);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.home();
                setLogo();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                setTitle();
                SpiderDebug.log("site-dialog", "home refresh start key=%s homePage=%s", getHome().getKey(), getHome().hasHomePage());
                if (mWeb != null && mWeb.isVisible()) {
                    if (!mWeb.load(getHome(), true)) getVideo(true);
                } else {
                    getVideo();
                }
                SpiderDebug.log("site-dialog", "home refresh end key=%s", getHome().getKey());
                break;
            case SIZE:
                if (mHotTv != null && !mHotTv.isEmpty()) {
                    clearRecommendRows();
                    addGrid(mHotTv, Style.rect());
                } else {
                    getVideo();
                }
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) {
            VideoActivity.cast(this, event.history().save(VodConfig.getCid()));
        } else {
            VodConfig.load(event.config(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Vod item) {
        if (mHotTv != null && !mHotTv.isEmpty()) CollectActivity.start(this, item.getName());
        else if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void showDialog() {
        long start = System.currentTimeMillis();
        SpiderDebug.log("site-dialog", "open requested cost=%sms", System.currentTimeMillis() - App.time());
        SiteDialog.create().show(this);
        SpiderDebug.log("site-dialog", "show returned delay=%sms", System.currentTimeMillis() - start);
    }

    @Override
    public void onRefresh() {
        if (mWeb != null && mWeb.isVisible()) mWeb.reload();
        else getVideo();
    }

    @Override
    public void reloadConfig() {
        VodConfig.get().clear().config(getConfig()).load(new Callback() {
            @Override
            public void start() {
                mBinding.progressLayout.showProgress();
            }

            @Override
            public void success() {
                showContent();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                showContent();
            }
        });
    }

    @Override
    public void setSite(Site item) {
        SpiderDebug.log("site-dialog", "set site key=%s name=%s homePage=%s", item.getKey(), item.getName(), item.hasHomePage());
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) {
            if (mWeb != null && mWeb.isVisible()) showDialog();
            else if (mCategory != null) toggleCategoryFilter();
            else SettingActivity.start(this);
            return true;
        }
        if (mWeb != null && mWeb.isVisible()) {
            if (KeyUtil.isBackKey(event)) {
                if (KeyUtil.isActionUp(event)) onBackInvoked();
                return true;
            }
            if (mBinding.toolbar.hasFocus()) {
                if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event)) return requestWebFocus();
                return super.dispatchKeyEvent(event);
            }
            if (KeyUtil.isUpKey(event) && isToolbarVisible()) return super.dispatchKeyEvent(event);
            if (mWeb.dispatchKeyEvent(event)) return true;
            return super.dispatchKeyEvent(event);
        }
        if (KeyUtil.isActionDown(event)) {
            if (KeyUtil.isRightKey(event) && mBinding.homeNavigation.hasFocus()) {
                requestContentFocus();
                return true;
            }
            if (mBinding.homePopular.isShown() && mSelectedChart != null) {
                if (KeyUtil.isUpKey(event) && mBinding.recycler.hasFocus() && mBinding.recycler.getSelectedPosition() <= 0) {
                    return mSelectedChart.requestFocus();
                }
                if (KeyUtil.isDownKey(event) && mBinding.chartTabs.hasFocus()) {
                    requestVideoFocus();
                    return true;
                }
            }
            View focused = getCurrentFocus();
            if (KeyUtil.isLeftKey(event) && inContent(focused) && atContentLeftEdge(focused)) {
                View target = mSelectedNav.isShown() ? mSelectedNav : mVodNav;
                target.requestFocus();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean requestTitleFocus() {
        updateToolbarVisibility(true);
        mBinding.title.setFocusable(true);
        return mBinding.title.requestFocus();
    }

    private boolean inContent(View view) {
        for (View candidate = view; candidate != null; ) {
            if (candidate == mBinding.homeContentPane) return true;
            ViewParent parent = candidate.getParent();
            candidate = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean atContentLeftEdge(View view) {
        for (View item = view; item.getParent() instanceof View parent; item = parent) {
            if (parent == mBinding.homeContentPane) break;
            if (parent instanceof HorizontalGridView row) return row.getChildAdapterPosition(item) == 0;
        }
        return !inContent(view.focusSearch(View.FOCUS_LEFT));
    }

    private void toggleCategoryFilter() {
        mCategoryFilter = !mCategoryFilter;
        mCategory.toggleFilter(mCategoryFilter);
    }

    private boolean requestWebFocus() {
        return mWeb != null && mWeb.isVisible() && mWeb.requestFocus("toolbar-down");
    }

    private boolean requestContentFocus() {
        if (mLibrary != null) return mLibrary.requestContentFocus();
        if (mCategory != null) return mCategory.requestContentFocus();
        if (requestVideoFocus()) return true;
        return mSelectedChart != null && mSelectedChart.requestFocus();
    }

    private boolean requestVideoFocus() {
        if (mBinding.recycler.getVisibility() != View.VISIBLE) return false;
        int selected = mBinding.recycler.getSelectedPosition();
        if (isVideoRow(selected)) return focusVideoRow(selected);
        for (int i = 0; i < mAdapter.size(); i++) {
            if (isVideoRow(i)) return focusVideoRow(i);
        }
        return false;
    }

    private boolean isVideoRow(int position) {
        if (position < 0 || position >= mAdapter.size()) return false;
        Object item = mAdapter.get(position);
        return item instanceof ListRow || item instanceof Vod;
    }

    private boolean focusVideoRow(int position) {
        RecyclerView.ViewHolder attached = mBinding.recycler.findViewHolderForAdapterPosition(position);
        if (attached != null && focusVideoHolder(attached)) return true;
        mBinding.recycler.setSelectedPosition(position, new ViewHolderTask() {
            @Override
            public void run(RecyclerView.ViewHolder holder) {
                if (mSelectedNav == mHotNav && (mBinding.homeNavigation.hasFocus() || mBinding.chartTabs.hasFocus())) focusVideoHolder(holder);
            }
        });
        return true;
    }

    private boolean focusVideoHolder(RecyclerView.ViewHolder holder) {
        if (holder instanceof ItemBridgeAdapter.ViewHolder bridge
                && bridge.getViewHolder() instanceof ListRowPresenter.ViewHolder row) {
            HorizontalGridView cards = row.getGridView();
            RecyclerView.ViewHolder card = cards.findViewHolderForAdapterPosition(Math.max(0, cards.getSelectedPosition()));
            if (card != null && card.itemView.requestFocus()) return true;
            return cards.requestFocus(View.FOCUS_FORWARD);
        }
        return holder.itemView.requestFocus(View.FOCUS_FORWARD);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        if (mWeb != null) mWeb.onResume();
    }

    @Override
    protected void onPause() {
        if (mWeb != null) mWeb.onPause();
        super.onPause();
        mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (mWeb != null && mWeb.isVisible() && mWeb.handleBack()) {
            return;
        } else if (mWeb != null && mWeb.isVisible() && consumeTvFullscreenBack()) {
            return;
        } else if (mWeb != null && mWeb.isVisible()) {
            exitHome();
            return;
        } else if (mBinding.progressLayout.isProgress()) {
            showContent();
        } else if (mLibrary != null) {
            if (!mLibrary.closeDeleteMode()) {
                showHot();
                mHotNav.requestFocus();
            }
        } else if (mCategory != null) {
            if (mCategory.canBack()) mCategory.goBack();
            else if (mCategoryFilter) toggleCategoryFilter();
            else {
                showHot();
                mHotNav.requestFocus();
            }
        } else if (mBinding.recycler.getSelectedPosition() != 0) {
            mBinding.recycler.scrollToPosition(0);
        } else {
            exitHome();
        }
    }

    private boolean consumeTvFullscreenBack() {
        if (!TV_FULL.equals(webChromeMode) && !TV_TOOLBAR_HIDDEN.equals(webChromeMode)) return false;
        applyTvChrome(TV_NORMAL);
        requestTitleFocus();
        return true;
    }

    private void exitHome() {
        ExitConfirmDialog.create(this::confirmExitHome).show(this);
    }

    private void confirmExitHome() {
        if (PlaybackService.isRunning()) Util.moveToBackground(this);
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        if (mWeb != null) mWeb.destroy();
        DLNARendererService.stop(this);
        LiveConfig.get().clear();
        VodConfig.get().clear();
        AppDatabase.backup();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }

    @Override
    public void onWebLoading() {
        showWebOverlay();
        mBinding.progressLayout.showProgress();
    }

    @Override
    public void onWebReady() {
        showWebOverlay();
        mBinding.progressLayout.showContent();
        mBinding.recycler.setVisibility(View.GONE);
    }

    @Override
    public void onWebError() {
        applyTvChrome(TV_NORMAL);
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        mBinding.recycler.setVisibility(View.VISIBLE);
        getVideo(true);
    }

    @Override
    public void setToolbar(boolean visible) {
        if (!Setting.isWebHomeFullscreen()) {
            applyTvChrome(TV_NORMAL);
            return;
        }
        applyTvChrome(visible ? webDefaultChromeMode : TV_TOOLBAR_HIDDEN);
    }

    @Override
    public void applyDefaultChrome(Site site) {
        if (!Setting.isWebHomeFullscreen()) {
            webDefaultChromeMode = TV_NORMAL;
            applyTvChrome(TV_NORMAL);
            return;
        }
        webDefaultChromeMode = tvDefaultMode(site == null ? "" : site.getChromeMode());
        applyTvChrome(webDefaultChromeMode);
    }

    @Override
    public void setChrome(JsonObject payload) {
        if (!Setting.isWebHomeFullscreen()) {
            applyTvChrome(TV_NORMAL);
            return;
        }
        applyTvChrome(tvRuntimeMode(Json.safeString(payload, "mode")));
    }

    @Override
    public void restoreChrome() {
        if (!Setting.isWebHomeFullscreen()) {
            applyTvChrome(TV_NORMAL);
            return;
        }
        applyTvChrome(webDefaultChromeMode);
    }

    @Override
    public WebHomeViewport getViewport() {
        return tvViewport(webChromeMode);
    }

    @Override
    public void openVod() {
        applyTvChrome(TV_NORMAL);
        if (mWeb != null) mWeb.hide();
        hideWebOverlay();
        getVideo(true);
    }

    @Override
    public void openSetting() {
        SettingActivity.start(this);
    }

    private void applyTvChrome(String mode) {
        webChromeMode = mode;
        webToolbarVisible = TV_NORMAL.equals(mode) || TV_OVERLAY.equals(mode);
        updateToolbarVisibility(webToolbarVisible);
        syncWebOverlayLayout();
        if (mWeb != null) mWeb.setViewport(tvViewport(mode));
    }

    private String tvDefaultMode(String mode) {
        return tvMode(mode, TV_FULL);
    }

    private String tvRuntimeMode(String mode) {
        return tvMode(mode, webChromeMode);
    }

    private String tvMode(String mode, String fallback) {
        String value = TextUtils.isEmpty(mode) ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (TV_NORMAL.equals(value) || "normal".equals(value)) return TV_NORMAL;
        if (TV_TOOLBAR_HIDDEN.equals(value)) return TV_TOOLBAR_HIDDEN;
        if (TV_OVERLAY.equals(value)) return TV_OVERLAY;
        if (TV_FULL.equals(value) || "edge".equals(value) || "immersive".equals(value)) return TV_FULL;
        return fallback;
    }

    private WebHomeViewport tvViewport(String mode) {
        return WebHomeViewport.fixed(ResUtil.dp2px(28), ResUtil.dp2px(48), ResUtil.dp2px(28), ResUtil.dp2px(48), mode);
    }

}
