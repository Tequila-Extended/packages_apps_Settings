package com.android.settings.homepage;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.widget.HomepagePreference;

public class BluetoothCardPreference extends HomepagePreference {
    
    private Drawable mDeviceIcon;
    
    public BluetoothCardPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    
    public BluetoothCardPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    
    public BluetoothCardPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public BluetoothCardPreference(Context context) {
        super(context);
    }
    
    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        
        ImageView iconView = (ImageView) holder.findViewById(R.id.bluetooth_connected_device_icon);
        if (iconView != null) {
            if (mDeviceIcon != null) {
                // Clear any existing tint/color filter
                iconView.setImageTintList(null);
                iconView.setImageTintMode(null);
                iconView.clearColorFilter();
                
                // Force the drawable to ignore theme (show original colors)
                Drawable drawable = mDeviceIcon.getConstantState().newDrawable().mutate();
                drawable.setAutoMirrored(false);
                
                // Set the drawable
                iconView.setImageDrawable(drawable);
                iconView.setVisibility(View.VISIBLE);
                
                // Set background color based on theme (light/dark mode)
                boolean isDarkMode = (getContext().getResources().getConfiguration().uiMode 
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                
                // Create rounded background drawable
                android.graphics.drawable.GradientDrawable background = 
                    new android.graphics.drawable.GradientDrawable();
                background.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                
                if (isDarkMode) {
                    // Dark mode: subtle dark background
                    background.setColor(android.graphics.Color.parseColor("#1A1A1A"));
                } else {
                    // Light mode: light/white background
                    background.setColor(android.graphics.Color.parseColor("#F5F5F5"));
                }
                
                iconView.setBackground(background);
                iconView.setPadding(6, 6, 6, 6);
                iconView.setClipToOutline(true);
            } else {
                iconView.setVisibility(View.GONE);
            }
        }
    }
    
    public void setBluetoothDeviceIcon(Drawable icon) {
        if (mDeviceIcon != icon) {
            mDeviceIcon = icon;
            notifyChanged();
        }
    }
}