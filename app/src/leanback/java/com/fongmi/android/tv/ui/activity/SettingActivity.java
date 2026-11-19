package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityTvPageBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.SettingFragment;

public class SettingActivity extends BaseActivity {

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return ActivityTvPageBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            SettingFragment page = new SettingFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.tvPage, page).commitNow();
            page.requestContentFocus();
        }
    }
}
