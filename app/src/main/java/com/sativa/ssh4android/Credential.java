package com.sativa.ssh4android;

import android.content.Context;
import android.content.SharedPreferences;

public class Credential {
    private String serverAddress;
    private String username;
    private String password;

    public Credential(String serverAddress, String username, String password) {
        this.serverAddress = serverAddress;
        this.username = username;
        this.password = password;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

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