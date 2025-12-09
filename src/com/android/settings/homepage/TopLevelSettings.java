/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.homepage;

import static com.android.settings.search.actionbar.SearchMenuController.NEED_SEARCH_ICON_IN_ACTION_BAR;
import static com.android.settingslib.search.SearchIndexable.MOBILE;

import android.app.ActivityManager;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.recyclerview.widget.RecyclerView;
import androidx.window.embedding.ActivityEmbeddingController;

import com.android.settings.R;
import com.android.settings.Utils;
import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.core.SubSettingLauncher;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.support.SupportPreferenceController;
import com.android.settings.widget.HomepagePreference;
import com.android.settings.widget.HomepagePreferenceLayoutHelper.HomepagePreferenceLayout;
import com.android.settingslib.core.instrumentation.Instrumentable;
import com.android.settingslib.drawer.Tile;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable(forTarget = MOBILE)
public class TopLevelSettings extends DashboardFragment implements SplitLayoutListener,
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private static final String TAG = "TopLevelSettings";
    private static final String SAVED_HIGHLIGHT_MIXIN = "highlight_mixin";
    private static final String PREF_KEY_SUPPORT = "top_level_support";

    private boolean mIsEmbeddingActivityEnabled;
    private TopLevelHighlightMixin mHighlightMixin;
    private int mPaddingHorizontal;
    private boolean mScrollNeeded = true;
    private boolean mFirstStarted = true;
    private ActivityEmbeddingController mActivityEmbeddingController;
    private boolean gAppsExists;

    public TopLevelSettings() {
        final Bundle args = new Bundle();
        args.putBoolean(NEED_SEARCH_ICON_IN_ACTION_BAR, false);
        setArguments(args);
    }

    @VisibleForTesting
    public TopLevelSettings(TopLevelHighlightMixin highlightMixin) {
        this();
        mHighlightMixin = highlightMixin;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.top_level_settings;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DASHBOARD_SUMMARY;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        gAppsExists = checkIfGoogleAppsExist(context);
        HighlightableMenu.fromXml(context, getPreferenceScreenResId());
        use(SupportPreferenceController.class).setActivity(getActivity());
    }

    @Override
    public int getHelpResource() {
        return 0;
    }

    @Override
    public Fragment getCallbackFragment() {
        return this;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (isDuplicateClick(preference)) {
            return true;
        }

        ActivityEmbeddingRulesController.registerSubSettingsPairRule(getContext(),
                true /* clearTop */);

        setHighlightPreferenceKey(preference.getKey());
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        new SubSettingLauncher(getActivity())
                .setDestination(pref.getFragment())
                .setArguments(pref.getExtras())
                .setSourceMetricsCategory(caller instanceof Instrumentable
                        ? ((Instrumentable) caller).getMetricsCategory()
                        : Instrumentable.METRICS_CATEGORY_UNKNOWN)
                .setTitleRes(-1)
                .setIsSecondLayerPage(true)
                .launch();
        return true;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mIsEmbeddingActivityEnabled =
                ActivityEmbeddingUtils.isEmbeddingActivityEnabled(getContext());
        if (!mIsEmbeddingActivityEnabled) {
            return;
        }

        boolean activityEmbedded = isActivityEmbedded();
        if (icicle != null) {
            mHighlightMixin = icicle.getParcelable(SAVED_HIGHLIGHT_MIXIN);
            if (mHighlightMixin != null) {
                mScrollNeeded = !mHighlightMixin.isActivityEmbedded() && activityEmbedded;
                mHighlightMixin.setActivityEmbedded(activityEmbedded);
            }
        }
        if (mHighlightMixin == null) {
            mHighlightMixin = new TopLevelHighlightMixin(activityEmbedded);
        }
    }

    @VisibleForTesting
    public boolean isActivityEmbedded() {
        if (mActivityEmbeddingController == null) {
            mActivityEmbeddingController = ActivityEmbeddingController.getInstance(getActivity());
        }
        return mActivityEmbeddingController.isActivityEmbedded(getActivity());
    }

    @Override
    public void onStart() {
        if (mFirstStarted) {
            mFirstStarted = false;
            FeatureFactory.getFactory(getContext()).getSearchFeatureProvider().sendPreIndexIntent(
                    getContext());
        } else if (mIsEmbeddingActivityEnabled && isOnlyOneActivityInTask()
                && !isActivityEmbedded()) {
            Log.i(TAG, "Set default menu key");
            setHighlightMenuKey(getString(SettingsHomepageActivity.DEFAULT_HIGHLIGHT_MENU_KEY),
                    /* scrollNeeded= */ false);
        }
        super.onStart();
    }

    private boolean isOnlyOneActivityInTask() {
        final ActivityManager.RunningTaskInfo taskInfo = getSystemService(ActivityManager.class)
                .getRunningTasks(1).get(0);
        return taskInfo.numActivities == 1;
    }

    private boolean checkIfGoogleAppsExist(Context context) {
        PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getPackageInfo("com.google.android.gsf", 0);
            return true; 
        } catch (PackageManager.NameNotFoundException e) {
            return false; 
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mHighlightMixin != null) {
            outState.putParcelable(SAVED_HIGHLIGHT_MIXIN, mHighlightMixin);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        int tintColor = Utils.getHomepageIconColor(getContext());
        iteratePreferences(preference -> {
            Drawable icon = preference.getIcon();
            if (icon != null) {
                icon.setTint(tintColor);
            }
        });
        onSetPrefCard();
    }

    private void onSetPrefCard() {
        final PreferenceScreen screen = getPreferenceScreen();
        final int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            final Preference preference = screen.getPreference(i);

            String key = preference.getKey();
            
            if ("top_level_connected_devices".equals(key)) {
                preference.setLayoutResource(R.layout.superioros_dashboard_preference_bluetooth);
            } else if (key.equals("top_level_network")
                || key.equals("top_level_apps")
                || key.equals("top_level_accessibility")
                || key.equals("top_level_emergency")
                || key.equals("top_level_system")){
                preference.setLayoutResource(R.layout.superioros_dashboard_preference_top);
            } else if (key.equals("top_level_tequila_settings")){
                preference.setLayoutResource(R.layout.superioros_dashboard_preference_single);
            } else if (key.equals("top_level_battery")
                || key.equals("top_level_display")
                || key.equals("top_level_security")
                || key.equals("top_level_privacy")
                || key.equals("top_level_safety_center")
                || key.equals("top_level_storage")
                || key.equals("top_level_wellbeing")
                || key.equals("top_level_notifications")
                || key.equals("top_level_sound")){
                preference.setLayoutResource(R.layout.superioros_dashboard_preference_middle);
            } else if ("top_level_google".equals(key)){
                preference.setLayoutResource(R.layout.superioros_dashboard_preference_bottom);
            } else if (key.equals("top_level_accounts") && gAppsExists){
                preference.setLayoutResource(R.layout.superioros_dashboard_preference_middle);
            } else {
                preference.setLayoutResource(R.layout.superioros_dashboard_preference_bottom);
            }
       }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        highlightPreferenceIfNeeded();
    }

    @Override
    public void onSplitLayoutChanged(boolean isRegularLayout) {
        iteratePreferences(preference -> {
            if (preference instanceof HomepagePreferenceLayout) {
                ((HomepagePreferenceLayout) preference).getHelper().setIconVisible(isRegularLayout);
            }
        });
    }

    @Override
    public void highlightPreferenceIfNeeded() {
        if (mHighlightMixin != null) {
            mHighlightMixin.highlightPreferenceIfNeeded();
        }
    }

    @Override
    public RecyclerView onCreateRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        RecyclerView recyclerView = super.onCreateRecyclerView(inflater, parent,
                savedInstanceState);
        recyclerView.setPadding(mPaddingHorizontal, 0, mPaddingHorizontal, 0);
        return recyclerView;
    }

    public void setPaddingHorizontal(int padding) {
        mPaddingHorizontal = padding;
        RecyclerView recyclerView = getListView();
        if (recyclerView != null) {
            recyclerView.setPadding(padding, 0, padding, 0);
        }
    }

    public void updatePreferencePadding(boolean isTwoPane) {
        iteratePreferences(new PreferenceJob() {
            private int mIconPaddingStart;
            private int mTextPaddingStart;

            @Override
            public void init() {
                mIconPaddingStart = getResources().getDimensionPixelSize(isTwoPane
                        ? R.dimen.homepage_preference_icon_padding_start_two_pane
                        : R.dimen.homepage_preference_icon_padding_start);
                mTextPaddingStart = getResources().getDimensionPixelSize(isTwoPane
                        ? R.dimen.homepage_preference_text_padding_start_two_pane
                        : R.dimen.homepage_preference_text_padding_start);
            }

            @Override
            public void doForEach(Preference preference) {
                if (preference instanceof HomepagePreferenceLayout) {
                    ((HomepagePreferenceLayout) preference).getHelper()
                            .setIconPaddingStart(mIconPaddingStart);
                    ((HomepagePreferenceLayout) preference).getHelper()
                            .setTextPaddingStart(mTextPaddingStart);
                }
            }
        });
    }

    public TopLevelHighlightMixin getHighlightMixin() {
        return mHighlightMixin;
    }

    public void setHighlightPreferenceKey(String prefKey) {
        if (mHighlightMixin != null && !TextUtils.equals(prefKey, PREF_KEY_SUPPORT)) {
            mHighlightMixin.setHighlightPreferenceKey(prefKey);
        }
    }

    public boolean isDuplicateClick(Preference pref) {
        return mHighlightMixin != null
                && TextUtils.equals(pref.getKey(), mHighlightMixin.getHighlightPreferenceKey())
                && isActivityEmbedded();
    }

    public void setMenuHighlightShowed(boolean show) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setMenuHighlightShowed(show);
        }
    }

    public void setHighlightMenuKey(String menuKey, boolean scrollNeeded) {
        if (mHighlightMixin != null) {
            mHighlightMixin.setHighlightMenuKey(menuKey, scrollNeeded);
        }
    }

    @Override
    protected boolean shouldForceRoundedIcon() {
        return getContext().getResources()
                .getBoolean(R.bool.config_force_rounded_icon_TopLevelSettings);
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        if (!mIsEmbeddingActivityEnabled || !(getActivity() instanceof SettingsHomepageActivity)) {
            return super.onCreateAdapter(preferenceScreen);
        }
        return mHighlightMixin.onCreateAdapter(this, preferenceScreen, mScrollNeeded);
    }

    @Override
    protected Preference createPreference(Tile tile) {
        return new HomepagePreference(getPrefContext());
    }

    void reloadHighlightMenuKey() {
        if (mHighlightMixin != null) {
            mHighlightMixin.reloadHighlightMenuKey(getArguments());
        }
    }

    private void iteratePreferences(PreferenceJob job) {
        if (job == null || getPreferenceManager() == null) {
            return;
        }
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        job.init();
        int count = screen.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference preference = screen.getPreference(i);
            if (preference == null) {
                break;
            }
            job.doForEach(preference);
        }
    }

    private interface PreferenceJob {
        default void init() {
        }

        void doForEach(Preference preference);
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.top_level_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return false;
                }
            };
}
