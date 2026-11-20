package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.ActivityKeepBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.adapter.BaseDiffCallback;
import com.fongmi.android.tv.ui.adapter.KeepAdapter;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.web.WebHomeInlineVodStore;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class KeepFragment extends BaseFragment implements KeepAdapter.OnClickListener {

    private ActivityKeepBinding mBinding;
    private KeepAdapter mAdapter;
    private ArrayObjectAdapter mHistoryAdapter;
    private HistoryPresenter mHistoryPresenter;

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = ActivityKeepBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        setRecyclerView();
        setHistoryRow();
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setAdapter(mAdapter = new KeepAdapter(this, sidebarInset()));
        mBinding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), Product.getColumn()));
        mBinding.recycler.addItemDecoration(new SpaceItemDecoration(Product.getColumn(), 16));
    }

    private void setHistoryRow() {
        mHistoryPresenter = new HistoryPresenter(new HistoryPresenter.OnClickListener() {
            @Override
            public void onItemClick(History item) {
                openLocalItem(item.getCid(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), item.getWallPic());
            }

            @Override
            public void onItemDelete(History item) {
                item.deleteAndSync();
                getHistory();
            }

            @Override
            public boolean onLongClick() {
                if (mHistoryPresenter.isDelete()) {
                    for (int cid : AppDatabase.get().getHistoryDao().findConfigIds()) {
                        if (cid > 0) History.deleteAndSync(cid);
                        else History.delete(cid);
                    }
                    setHistoryDelete(false);
                    getHistory();
                } else {
                    setHistoryDelete(true);
                }
                return true;
            }
        }, sidebarInset());
        mHistoryAdapter = new ArrayObjectAdapter(mHistoryPresenter);
        mBinding.historyRow.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.historyRow.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.historyRow.setAdapter(new ItemBridgeAdapter(mHistoryAdapter));
    }

    private void getHistory() {
        mHistoryAdapter.setItems(History.getRecentLocal(), new BaseDiffCallback<History>());
        boolean empty = mHistoryAdapter.size() == 0;
        mBinding.historyRow.setVisibility(empty ? View.GONE : View.VISIBLE);
        mBinding.historyEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) setHistoryDelete(false);
    }

    private void setHistoryDelete(boolean delete) {
        mHistoryPresenter.setDelete(delete);
        mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    private void getKeep() {
        mAdapter.setItems(Keep.getVod());
    }

    private void openLocalItem(int cid, String site, String id, String title, String poster, String wallPic) {
        Activity activity = requireActivity();
        Runnable open = () -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            if (SiteApi.PUSH.equals(site) || WebHomeInlineVodStore.KEY.equals(site) || !VodConfig.get().getSite(site).isEmpty()) {
                VideoActivity.start(activity, site, id, title, poster, null, wallPic);
            } else {
                CollectActivity.start(activity, title);
            }
        };
        Config config = Config.find(cid);
        if (cid == VodConfig.getCid() || SiteApi.PUSH.equals(site) || WebHomeInlineVodStore.KEY.equals(site)) {
            open.run();
        } else if (config == null || config.isEmpty()) {
            CollectActivity.start(activity, title);
        } else {
            Config previous = VodConfig.get().getConfig();
            VodConfig.load(config, new Callback() {
                @Override
                public void start() {
                    Notify.progress(activity);
                }

                @Override
                public void success() {
                    Notify.dismiss();
                    open.run();
                }

                @Override
                public void error(String msg) {
                    VodConfig.load(previous, new Callback() {
                        @Override
                        public void success() {
                            fallback();
                        }

                        @Override
                        public void error(String error) {
                            fallback();
                        }

                        private void fallback() {
                            Notify.dismiss();
                            Notify.show(msg);
                            if (!activity.isFinishing() && !activity.isDestroyed()) CollectActivity.start(activity, title);
                        }
                    });
                }
            });
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.isVod()) mHistoryAdapter.notifyArrayItemRangeChanged(0, mHistoryAdapter.size());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.KEEP) getKeep();
        else if (event.getType() == RefreshEvent.Type.HISTORY) getHistory();
    }

    @Override
    public void onItemClick(Keep item) {
        openLocalItem(item.getCid(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), null);
    }

    @Override
    public void onItemDelete(Keep item) {
        mAdapter.remove(item.delete(), () -> {
            if (mAdapter.getItemCount() == 0) mAdapter.setDelete(false);
        });
    }

    @Override
    public boolean onLongClick() {
        mAdapter.setDelete(true);
        return true;
    }

    private int sidebarInset() {
        return getActivity() instanceof HomeActivity ? getResources().getDimensionPixelSize(R.dimen.home_nav_width) : 0;
    }

    public boolean requestContentFocus() {
        if (mBinding == null) return false;
        if (mHistoryAdapter.size() > 0 && mBinding.historyRow.requestFocus(View.FOCUS_FORWARD)) return true;
        return mAdapter.getItemCount() > 0 && mBinding.recycler.requestFocus(View.FOCUS_FORWARD);
    }

    public boolean closeDeleteMode() {
        if (mBinding == null) return false;
        if (mHistoryPresenter.isDelete()) setHistoryDelete(false);
        else if (mAdapter.isDelete()) mAdapter.setDelete(false);
        else return false;
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        getHistory();
        getKeep();
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        mBinding = null;
        super.onDestroyView();
    }
}
