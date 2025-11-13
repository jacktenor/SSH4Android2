package com.sativa.ssh4android;

import android.content.Context;
import android.content.SharedPreferences;

public record Credential(String serverAddress, String username, String password) {

    // Save credentials to SharedPreferences
    public void saveCredentials(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("SavedCredentials", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("savedServerAddress", serverAddress);
        editor.putString("savedUsername", username);
        editor.apply();
    }

    // Retrieve saved credentials from SharedPreferences
    public static Credential getSavedCredentials(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("SavedCredentials", Context.MODE_PRIVATE);
        String savedServerAddress = sharedPreferences.getString("savedServerAddress", null);
        String savedUsername = sharedPreferences.getString("savedUsername", null);

        if (savedServerAddress != null && savedUsername != null) {
            // If saved credentials exist, return a Credential object
            return new Credential(savedServerAddress, savedUsername, null);
        } else {
            return null;
        }
    }
}