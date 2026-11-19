package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityTvPageBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.SearchFragment;

public class SearchActivity extends BaseActivity {

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SearchActivity.class));
    }

    public static void start(Activity activity, String keyword) {
        start(activity, keyword, null);
    }

    public static void start(Activity activity, String keyword, String siteKey) {
        start(activity, keyword, siteKey, null, null);
    }

    public static void start(Activity activity, String keyword, String siteKey, String pic, String wallPic) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.putExtra("keyword", keyword);
        intent.putExtra("siteKey", siteKey);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        activity.startActivity(intent);
    }

    public static void direct(Activity activity, String keyword) {
        direct(activity, keyword, null, null, null);
    }

    public static void direct(Activity activity, String keyword, String siteKey, String pic, String wallPic) {
        CollectActivity.start(activity, keyword, siteKey, pic, wallPic);
    }

    @Override
    protected ViewBinding getBinding() {
        return ActivityTvPageBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.tvPage, SearchFragment.newInstance(getIntent().getExtras())).commit();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (getSupportFragmentManager().findFragmentById(R.id.tvPage) instanceof SearchFragment search
                && search.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }
}
