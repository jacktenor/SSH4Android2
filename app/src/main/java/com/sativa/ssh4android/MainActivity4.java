package com.sativa.ssh4android;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;

public class MainActivity4 extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main4);

        Button button = findViewById(R.id.button);
        button.setBackgroundColor(Color.TRANSPARENT);

        // Set the OnClickListener for the button
        button.setOnClickListener(view -> {
            // Check if the clicked view is the button
            if (view.getId() == R.id.button) {
                // Create an intent to start MainActivity
                Intent intent = new Intent(MainActivity4.this, MainActivity.class);
                startActivity(intent);
                finish(); // Optional, depending on your use case
            }
        });
    }
}
