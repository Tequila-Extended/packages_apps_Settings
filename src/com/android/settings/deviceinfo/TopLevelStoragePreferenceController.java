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

package com.android.settings.deviceinfo;

import android.content.Context;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.deviceinfo.storage.StorageCacheHelper;
import com.android.settingslib.deviceinfo.PrivateStorageInfo;
import com.android.settingslib.deviceinfo.StorageManagerVolumeProvider;
import com.android.settingslib.utils.ThreadUtils;

import java.text.NumberFormat;
import java.util.concurrent.Future;

public class TopLevelStoragePreferenceController extends BasePreferenceController {

    private final StorageManager mStorageManager;
    private final StorageManagerVolumeProvider mStorageManagerVolumeProvider;

    public TopLevelStoragePreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mStorageManager = mContext.getSystemService(StorageManager.class);
        mStorageManagerVolumeProvider = new StorageManagerVolumeProvider(mStorageManager);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    protected void refreshSummary(Preference preference) {
        if (preference == null) {
            return;
        }

        refreshSummaryThread(preference);
    }

    @VisibleForTesting
    protected Future refreshSummaryThread(Preference preference) {
        int userId = Utils.getCurrentUserId(mContext.getSystemService(UserManager.class), false);
        final StorageCacheHelper storageCacheHelper = new StorageCacheHelper(mContext, userId);
        long cachedUsedSize = storageCacheHelper.retrieveUsedSize();
        long cachedTotalSize = storageCacheHelper.retrieveCachedSize().totalSize;
        if (cachedUsedSize != 0 && cachedTotalSize != 0) {
            preference.setSummary(getSummary(cachedUsedSize, cachedTotalSize));
        }

        return ThreadUtils.postOnBackgroundThread(() -> {
            final PrivateStorageInfo info = PrivateStorageInfo.getPrivateStorageInfo(
                    getStorageManagerVolumeProvider());

            long usedBytes = info.totalBytes - info.freeBytes;
            storageCacheHelper.cacheUsedSize(usedBytes);
            ThreadUtils.postOnMainThread(() -> {
                preference.setSummary(
                        getSummary(usedBytes, info.totalBytes));
            });
        });
    }

    @VisibleForTesting
    protected StorageManagerVolumeProvider getStorageManagerVolumeProvider() {
        return mStorageManagerVolumeProvider;
    }

    private CharSequence getSummary(long usedBytes, long totalBytes) {
        NumberFormat percentageFormat = NumberFormat.getPercentInstance();
        double usedPercentage = totalBytes == 0 ? 0 : ((double) usedBytes) / totalBytes;
        String percentageString = percentageFormat.format(usedPercentage);
        String freeSpaceString = Formatter.formatFileSize(mContext, totalBytes - usedBytes);

        int color;
        if (usedPercentage < 0.5) {
            color = ContextCompat.getColor(mContext, R.color.green_percentage);
        } else if (usedPercentage < 0.8) {
            color = ContextCompat.getColor(mContext, R.color.orange_percentage);
        } else {
            color = ContextCompat.getColor(mContext, R.color.red_percentage);
        }

        String summaryText = mContext.getString(R.string.storage_summary, percentageString, freeSpaceString);
        SpannableString spannableSummary = new SpannableString(summaryText);

        int start = summaryText.indexOf(percentageString);
        if (start >= 0) {
            spannableSummary.setSpan(new ForegroundColorSpan(color),
                    start,
                    start + percentageString.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return spannableSummary;
    }
}