package com.android.settings.deviceinfo.firmwareversion;
import android.content.Context;
import android.os.SystemProperties;
import android.text.TextUtils;
import androidx.preference.Preference;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
public class TequilaMaintainerPreferenceController extends BasePreferenceController {
    private static final String TAG = "TequilaMaintainerPreferenceController";
    private static final String ROM_PROPERTY = "ro.tequila.maintainer";
    public TequilaMaintainerPreferenceController(Context context, String key) {
        super(context, key);
    }
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
    public CharSequence getSummary() {
        String rom = SystemProperties.get(ROM_PROPERTY,
                this.mContext.getString(R.string.device_info_default));
        return rom;
    }
}