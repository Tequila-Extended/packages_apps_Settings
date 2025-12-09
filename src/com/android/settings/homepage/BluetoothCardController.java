package com.android.settings.homepage;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Pair;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.BluetoothUtils;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.util.Collection;

public class BluetoothCardController extends BasePreferenceController implements BluetoothCallback, LifecycleObserver, OnStart, OnStop {

    private LocalBluetoothManager mBluetoothManager;
    private BluetoothCardPreference mPreference;
    private final BluetoothAdapter mBluetoothAdapter;

    public BluetoothCardController(Context context, String preferenceKey) {
        super(context, preferenceKey);
        mBluetoothManager = com.android.settings.bluetooth.Utils.getLocalBtManager(context);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    public int getAvailabilityStatus() {
        return mBluetoothAdapter != null ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        Preference pref = screen.findPreference(getPreferenceKey());
        if (pref instanceof BluetoothCardPreference) {
            mPreference = (BluetoothCardPreference) pref;
        }
    }

    @Override
    public void onStart() {
        if (mBluetoothManager != null) {
            mBluetoothManager.getEventManager().registerCallback(this);
        }
        updateState(mPreference);
    }

    @Override
    public void onStop() {
        if (mBluetoothManager != null) {
            mBluetoothManager.getEventManager().unregisterCallback(this);
        }
    }

    @Override
    public void updateState(Preference preference) {
        if (mBluetoothManager == null || mPreference == null) {
            return;
        }

        CachedBluetoothDevice targetDevice = null;
        
        // Get all known devices
        Collection<CachedBluetoothDevice> cachedDevices = mBluetoothManager.getCachedDeviceManager().getCachedDevicesCopy();

        // 1. First pass: Look for the ACTIVE device (The one playing music or on a call)
        for (CachedBluetoothDevice cachedDevice : cachedDevices) {
            if (cachedDevice.isConnected()) {
                boolean isActive = cachedDevice.isActiveDevice(BluetoothProfile.HEADSET) 
                                || cachedDevice.isActiveDevice(BluetoothProfile.A2DP)
                                || cachedDevice.isActiveDevice(BluetoothProfile.HEARING_AID)
                                || cachedDevice.isActiveDevice(BluetoothProfile.LE_AUDIO);
                
                if (isActive) {
                    targetDevice = cachedDevice;
                    break;
                }
            }
        }

        // 2. Second pass: If no active device found, just take the first connected device
        if (targetDevice == null) {
            for (CachedBluetoothDevice cachedDevice : cachedDevices) {
                if (cachedDevice.isConnected()) {
                    targetDevice = cachedDevice;
                    break;
                }
            }
        }

        // 3. Fetch the device icon
        Drawable connectionIcon = null;
        if (targetDevice != null) {
            // Get the device icon using BluetoothUtils
            // This will show custom device images (like real earbud photos) if available,
            // or circular badge icons with device-specific symbols as fallback
            Pair<Drawable, String> pair = BluetoothUtils.getBtRainbowDrawableWithDescription(mContext, targetDevice);
            
            if (pair != null && pair.first != null) {
                connectionIcon = pair.first;
            }
        }

        mPreference.setBluetoothDeviceIcon(connectionIcon);
    }

    @Override
    public void onBluetoothStateChanged(int bluetoothState) {
        updateState(mPreference);
    }

    @Override
    public void onConnectionStateChanged(CachedBluetoothDevice cachedDevice, int state) {
        updateState(mPreference);
    }

    @Override
    public void onDeviceAdded(CachedBluetoothDevice cachedDevice) {
        updateState(mPreference);
    }

    @Override
    public void onDeviceDeleted(CachedBluetoothDevice cachedDevice) {
        updateState(mPreference);
    }

    @Override
    public void onScanningStateChanged(boolean started) {
    }

    @Override
    public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
        updateState(mPreference);
    }

    @Override
    public void onActiveDeviceChanged(CachedBluetoothDevice activeDevice, int bluetoothProfile) {
        updateState(mPreference);
    }

    @Override
    public void onAudioModeChanged() {
    }
}