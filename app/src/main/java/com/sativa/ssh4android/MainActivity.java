package com.sativa.ssh4android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;

public class MainActivity extends Activity {
    private boolean isSureButtonClicked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button button = findViewById(R.id.button);
        Button button2 = findViewById(R.id.button2);
        Button button3 = findViewById(R.id.button3);
        Button button4 = findViewById(R.id.button4);
        Button button5 = findViewById(R.id.button5);

        button.setBackgroundColor(Color.TRANSPARENT);
        button2.setBackgroundColor(Color.TRANSPARENT);
        button3.setBackgroundColor(Color.TRANSPARENT);
        button4.setBackgroundColor(Color.TRANSPARENT);
        button5.setBackgroundColor(Color.TRANSPARENT);


        button.setOnClickListener(view -> {
            Intent i = new Intent(MainActivity.this, MainActivity2.class);
            final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
            button.startAnimation(myAnim);
            startActivity(i);
        });

        button2.setOnClickListener(view -> {
            Intent i = new Intent(MainActivity.this, MainActivity3.class);
            final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
            button2.startAnimation(myAnim);
            startActivity(i);
        });

        button4.setOnClickListener(view -> {
            Intent i = new Intent(MainActivity.this, MainActivity4.class);
            final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
            button4.startAnimation(myAnim);
            startActivity(i);
        });

        button3.setOnClickListener(view -> {
            Intent i = new Intent(MainActivity.this, MainActivity5.class);
            final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
            button3.startAnimation(myAnim);
            startActivity(i);
        });

        button5.setOnClickListener(view -> {
            if (!isSureButtonClicked) {
                // First click, change the label to "ARE YOU SURE?"
                button5.setTextColor( Color.RED );
                button5.setText("ARE YOU\n SURE?");
                isSureButtonClicked = true;
            } else {
                // Second click, clear input history
                clearInputHistory();
                // Reset the label to the original text
                button5.setTextColor( Color.WHITE );
                button5.setText(" Clear History\nAnd Password?");
                isSureButtonClicked = false;
            }
        });
    }


    public void clearInputHistory() {

        SharedPreferences sharedPreferences = getSharedPreferences("SavedCredentials", MODE_PRIVATE);
        SharedPreferences sharedPreferences2 = getSharedPreferences("InputHistory", MODE_PRIVATE);

        sharedPreferences.edit().clear().apply();
        sharedPreferences2.edit().clear().apply();
        // Optionally, you can notify the user that the histories are cleared
        GreenCustomToast.showCustomToast(getApplicationContext(), "Histories and\n passwords cleared.");
    }
}