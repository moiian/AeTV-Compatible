package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityKeepHostBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.KeepFragment;

public class KeepActivity extends BaseActivity {

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, KeepActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return ActivityKeepHostBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.keepHost, new KeepFragment()).commit();
    }

    @Override
    protected void onBackInvoked() {
        if (getSupportFragmentManager().findFragmentById(R.id.keepHost) instanceof KeepFragment fragment
                && fragment.closeDeleteMode()) return;
        super.onBackInvoked();
    }
}
