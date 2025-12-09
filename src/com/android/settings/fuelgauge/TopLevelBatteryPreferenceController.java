/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.fuelgauge;

import android.content.ComponentName;
import android.content.Context;
import android.os.BatteryManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.overlay.FeatureFactory;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settingslib.Utils;
import com.android.settingslib.utils.ThreadUtils;

public class TopLevelBatteryPreferenceController extends BasePreferenceController implements
        LifecycleObserver, OnStart, OnStop, BatteryPreferenceController {

    private static final String TAG = "TopLvBatteryPrefControl";

    @VisibleForTesting
    Preference mPreference;
    @VisibleForTesting
    protected boolean mIsBatteryPresent = true;

    private final BatteryBroadcastReceiver mBatteryBroadcastReceiver;

    private BatteryInfo mBatteryInfo;
    private BatteryStatusFeatureProvider mBatteryStatusFeatureProvider;
    private String mBatteryStatusLabel;

    public TopLevelBatteryPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mBatteryBroadcastReceiver = new BatteryBroadcastReceiver(mContext);
        mBatteryBroadcastReceiver.setBatteryChangedListener(type -> {
            Log.d(TAG, "onBatteryChanged: type=" + type);
            if (type == BatteryBroadcastReceiver.BatteryUpdateType.BATTERY_NOT_PRESENT) {
                mIsBatteryPresent = false;
            }
            BatteryInfo.getBatteryInfo(mContext, info -> {
                Log.d(TAG, "getBatteryInfo: " + info);
                mBatteryInfo = info;
                updateState(mPreference);
                setSummaryAsync(info);
            }, true);
        });

        mBatteryStatusFeatureProvider = FeatureFactory.getFactory(context)
                .getBatteryStatusFeatureProvider(context);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_show_top_level_battery)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
    }

    @Override
    public void onStart() {
        mBatteryBroadcastReceiver.register();
    }

    @Override
    public void onStop() {
        mBatteryBroadcastReceiver.unRegister();
    }

    @Override
    public CharSequence getSummary() {
        return getSummary(true);
    }

    private CharSequence getSummary(boolean batteryStatusUpdate) {
        if (!mIsBatteryPresent) {
            return mContext.getText(R.string.battery_missing_message);
        }
        return getDashboardLabel(mContext, mBatteryInfo, batteryStatusUpdate);
    }

    protected CharSequence getDashboardLabel(Context context, BatteryInfo info,
            boolean batteryStatusUpdate) {
        if (info == null || context == null) {
            return null;
        }
        Log.d(TAG, "getDashboardLabel: " + mBatteryStatusLabel + " batteryStatusUpdate="
                + batteryStatusUpdate);

        if (batteryStatusUpdate) {
            setSummaryAsync(info);
        }
        return mBatteryStatusLabel == null ? generateLabel(info) : mBatteryStatusLabel;
    }

    private void setSummaryAsync(BatteryInfo info) {
        ThreadUtils.postOnBackgroundThread(() -> {
            final boolean triggerBatteryStatusUpdate =
                    mBatteryStatusFeatureProvider.triggerBatteryStatusUpdate(this, info);
            ThreadUtils.postOnMainThread(() -> {
                if (!triggerBatteryStatusUpdate) {
                    mBatteryStatusLabel = null;
                }
                mPreference.setSummary(
                        mBatteryStatusLabel == null ? generateLabel(info) : mBatteryStatusLabel);
            });
        });
    }

    private CharSequence generateLabel(BatteryInfo info) {
        String batteryPercentString = info.batteryPercentString;
        int batteryPercentage = info.batteryLevel;

        int color;
        if (batteryPercentage >= 60) {
            color = ContextCompat.getColor(mContext, R.color.green_percentage);
        } else if (batteryPercentage >= 30) {
            color = ContextCompat.getColor(mContext, R.color.orange_percentage);
        } else {
            color = ContextCompat.getColor(mContext, R.color.red_percentage);
        }

        SpannableString percentSpan = new SpannableString(batteryPercentString);
        percentSpan.setSpan(
                new ForegroundColorSpan(color),
                0,
                batteryPercentString.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (Utils.containsIncompatibleChargers(mContext, TAG)) {
            return mContext.getString(R.string.battery_info_status_not_charging);
        }
        if (info.batteryStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING) {
            return info.statusLabel;
        } else if (!info.discharging && info.chargeLabel != null) {
            return info.chargeLabel;
        } else if (info.remainingLabel == null) {
            return percentSpan;
        } else {
            String remainingLabel = mContext.getString(R.string.power_remaining_settings_home_page,
                    batteryPercentString,
                    info.remainingLabel);

            SpannableString fullLabel = new SpannableString(remainingLabel);
            int percentIndex = remainingLabel.indexOf(batteryPercentString);

            if (percentIndex >= 0) {
                fullLabel.setSpan(
                        new ForegroundColorSpan(color),
                        percentIndex,
                        percentIndex + batteryPercentString.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return fullLabel;
        }
    }

    @Override
    public void updateBatteryStatus(String label, BatteryInfo info) {
        mBatteryStatusLabel = label;
        if (mPreference == null) {
            return;
        }
        final CharSequence summary = getSummary(false);
        if (summary != null) {
            mPreference.setSummary(summary);
        }
        Log.d(TAG, "updateBatteryStatus: " + label + " summary: " + summary);
    }

    @VisibleForTesting
    protected static ComponentName convertClassPathToComponentName(String classPath) {
        if (classPath == null || classPath.isEmpty()) {
            return null;
        }
        String[] split = classPath.split("\\.");
        int classNameIndex = split.length - 1;
        if (classNameIndex < 0) {
            return null;
        }
        int lastPkgIndex = classPath.length() - split[classNameIndex].length() - 1;
        String pkgName = lastPkgIndex > 0 ? classPath.substring(0, lastPkgIndex) : "";
        return new ComponentName(pkgName, split[classNameIndex]);
    }
}
