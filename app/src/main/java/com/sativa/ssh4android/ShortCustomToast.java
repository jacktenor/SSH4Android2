package com.sativa.ssh4android;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class ShortCustomToast {
    public static void showCustomToast(Context context, String message) {
        // Inflate the custom layout for the toast
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.green_custom_toast, (ViewGroup) null);

        // Set the text for the toast
        TextView text = layout.findViewById(R.id.green_toast_text);
        text.setText(message);

        // Create and show the custom toast
        Toast toast = new Toast(context);
        toast.setGravity(Gravity.TOP, 0, 250);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.show();
    }
}