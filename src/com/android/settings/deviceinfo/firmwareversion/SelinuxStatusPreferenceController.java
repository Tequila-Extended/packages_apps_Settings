package com.android.settings.deviceinfo.firmwareversion;
import android.content.Context;
import android.os.SELinux;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
public class SelinuxStatusPreferenceController extends BasePreferenceController {
    private static final String TAG = "SelinuxStatusCtrl";
    private String mStatus;
    public SelinuxStatusPreferenceController(Context context, String key) {
        super(context, key);
    }
    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_show_selinux_status)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
    @Override
    public CharSequence getSummary() {
        int stringId = R.string.selinux_status_disabled;
        if (SELinux.isSELinuxEnabled()) {
            stringId = SELinux.isSELinuxEnforced()
                    ? R.string.selinux_status_enforcing
                    : R.string.selinux_status_permissive;
        }
        return mContext.getString(stringId);
    }
}