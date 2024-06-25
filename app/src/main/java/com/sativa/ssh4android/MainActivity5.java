package com.sativa.ssh4android;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity5 extends AppCompatActivity {

    private AutoCompleteTextView inputAutoComplete;
    private Button enterButton;
    private Button button6;
    private Button button;
    private Button cancelButton; // New cancel button
    private TextView outputTextView;
    private TextView textView2;
    private CheckBox savePasswordCheckbox;
    private ScrollView outputScrollView;
    private String serverAddress;
    private String username;
    private String password;
    private String command;
    private String port;
    private Set<String> inputHistory;
    private int currentQuestionIndex;
    private List<String> questions;
    private static final String INPUT_HISTORY_KEY = "inputHistory";
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private Thread stdOutThread;
    private Thread stdErrThread;
    private ChannelExec channelExec;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main5);

        button6 = findViewById(R.id.button6);
        button = findViewById(R.id.button);
        cancelButton = findViewById(R.id.cancelButton); // Initialize the cancel button
        inputAutoComplete = findViewById(R.id.inputAutoComplete);
        enterButton = findViewById(R.id.enterButton);
        savePasswordCheckbox = findViewById(R.id.savePasswordCheckbox);
        outputTextView = findViewById(R.id.outputTextView);
        textView2 = findViewById(R.id.textView2);
        outputScrollView = findViewById(R.id.outputScrollView);

        getWindow().setBackgroundDrawableResource(R.drawable.panther);

        inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);

        inputAutoComplete.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        runOnUiThread(() -> button6.setVisibility(View.GONE));
        runOnUiThread(() -> button.setVisibility(View.GONE));
        runOnUiThread(() -> textView2.setVisibility(View.GONE));

        button.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
            button.startAnimation(myAnim);
            cancelCommand();
            Intent i = new Intent(MainActivity5.this, MainActivity.class);
            startActivity(i);
        });

        inputAutoComplete.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                enterButton.performClick();
                return true;
            }
            return false;
        });

        SharedPreferences sharedPreferences = getSharedPreferences("InputHistory", MODE_PRIVATE);
        inputHistory = new HashSet<>(sharedPreferences.getStringSet(INPUT_HISTORY_KEY, new HashSet<>()));

        if (currentQuestionIndex != 3) {
            Set<String> inputHistory = loadInputHistory();
            ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(inputHistory));
            inputAutoComplete.setAdapter(autoCompleteAdapter);
        } else {
            inputAutoComplete.setAdapter(null);
        }

        questions = new ArrayList<>();
        questions.add("SSH server address?");
        questions.add("Username?");
        questions.add("Password?");
        questions.add("Port?");
        questions.add("Command?");
        currentQuestionIndex = 0;
        setNextQuestion();

        saveInputHistory(new ArrayList<>(inputHistory));

        enterButton.setOnClickListener(view -> {
            try {
                handleInput();
            } catch (IOException e) {
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException:\n" + e.getMessage()));
            }
        });
        final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
        enterButton.startAnimation(myAnim);

        checkAndRequestPermission();
    }

    private void checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
        } else {
            loadInputHistory();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE)
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadInputHistory();
            } else {
                loadInputHistory();
            }
    }

    private Set<String> loadInputHistory() {
        return getSharedPreferences("InputHistory", MODE_PRIVATE)
                .getStringSet(INPUT_HISTORY_KEY, new HashSet<>());
    }

    private void saveInputHistory(List<String> inputValues) {
        Set<String> history = new HashSet<>(inputValues);
        getSharedPreferences("InputHistory", MODE_PRIVATE)
                .edit()
                .putStringSet(INPUT_HISTORY_KEY, history)
                .apply();
    }

    private void setNextQuestion() {
        inputAutoComplete.setHint(questions.get(currentQuestionIndex));

        // Set default port to 22 if the current question is about the port
        if (currentQuestionIndex == 3) {
            inputAutoComplete.setText(R.string._22);
        } else {
            inputAutoComplete.setText("");
        }
        currentQuestionIndex++;

        // Save the new password for the current server address and username
        Credential credential = new Credential(serverAddress, username, password);
        credential.saveCredentials(getApplicationContext());

        // Retrieve saved credentials
        Credential savedCredentials = Credential.getSavedCredentials(getApplicationContext());

        if (savedCredentials != null && currentQuestionIndex == 3
                && savedCredentials.serverAddress().equals(serverAddress)
                && savedCredentials.username().equals(username)) {
            // Fill the password only if the saved server address and username match the current ones
            String savedPassword = getPassword(serverAddress, username);
            if (savedPassword != null) {
                inputAutoComplete.setText(savedPassword);
            }
        }

        // Set up AutoCompleteTextView with input history for non-password inputs
        if (currentQuestionIndex != 3) {
            Set<String> inputHistory = loadInputHistory();
            ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(inputHistory));
            inputAutoComplete.setAdapter(autoCompleteAdapter);
        } else {
            // Remove the password from the adapter during the password entry phase
            inputAutoComplete.setAdapter(null);
        }
    }

    private void updateInputHistory(String newInput) {
        inputHistory.add(newInput);
        saveInputHistory(new ArrayList<>(inputHistory));
    }

    private void handleInput() throws IOException {
        String input = inputAutoComplete.getText().toString();
        updateInputHistory(input);
        Set<String> inputHistory = loadInputHistory();
        inputHistory.add(input);
        saveInputHistory(new ArrayList<>(inputHistory));

        AtomicBoolean savePassword = new AtomicBoolean(false);

        switch (currentQuestionIndex - 1) {
            case 0:
                serverAddress = input;
                break;
            case 1:
                username = input;
                savePasswordCheckbox.setVisibility(View.VISIBLE);
                inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                break;
            case 2:
                savePassword.set(savePasswordCheckbox.isChecked());
                password = input;
                if (savePassword.get()) {
                    savePassword();
                }
                savePasswordCheckbox.setVisibility(View.GONE);
                inputAutoComplete.setText("");
                inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);
                break;
            case 3:
                port = input.isEmpty() ? "22" : input;  // Use default port 22 if input is empty
                break;
            case 4:
                command = input;
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(inputAutoComplete.getWindowToken(), 0);
                }
                break;
        }

        if (currentQuestionIndex < questions.size()) {
            setNextQuestion();
        } else {
            inputAutoComplete.setText("");
            inputAutoComplete.setVisibility(View.GONE);
            enterButton.setVisibility(View.GONE);

            connectAndExecuteCommand2();
        }
    }

    private void savePassword() {
        SharedPreferences sharedPreferences = getSharedPreferences("SavedCredentials", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        Map<String, String> passwordsMap = getPasswordsMap();
        passwordsMap.put(serverAddress + "_" + username, password);
        savePasswordsMap(passwordsMap);

        editor.putString("savedServerAddress", serverAddress);
        editor.putString("savedUsername", username);
        editor.apply();
    }

    private Map<String, String> getPasswordsMap() {
        SharedPreferences sharedPreferences = getSharedPreferences("SavedCredentials", MODE_PRIVATE);
        String passwordsJson = sharedPreferences.getString("passwordsMap", "{}");
        return new Gson().fromJson(passwordsJson, new TypeToken<Map<String, String>>() {
        }.getType());
    }

    private void savePasswordsMap(Map<String, String> passwordsMap) {
        SharedPreferences sharedPreferences = getSharedPreferences("SavedCredentials", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String passwordsJson = new Gson().toJson(passwordsMap);
        editor.putString("passwordsMap", passwordsJson);
        editor.apply();
    }

    private String getPassword(String serverAddress, String username) {
        Map<String, String> passwordsMap = getPasswordsMap();
        return passwordsMap.get(serverAddress + "_" + username);
    }

    private void connectAndExecuteCommand2() {
        Executor executor = Executors.newSingleThreadExecutor();
        outputScrollView.setVisibility(View.VISIBLE);
        outputTextView.setVisibility(View.VISIBLE);
        outputTextView.setText("");
        cancelButton.setVisibility(View.VISIBLE); // Show the cancel button
        button.setVisibility(View.VISIBLE);
        textView2.setVisibility(View.VISIBLE);

        // Set the cancel button click listener before starting the threads
        cancelButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
            cancelButton.startAnimation(myAnim);
            cancelCommand();
        });

        executor.execute(() -> {
            Session session;
            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, serverAddress, Integer.parseInt(port));
                session.setConfig("StrictHostKeyChecking", "no"); // Disabled host key checking for now
                session.setConfig("PreferredAuthentications", "publickey,password");
                session.setPassword(password);
                session.connect();

                channelExec = (ChannelExec) session.openChannel("exec");
                channelExec.setCommand(command);
                InputStream in = channelExec.getInputStream();
                InputStream err = channelExec.getErrStream(); // For error stream
                channelExec.connect();

                isCancelled.set(false); // Reset cancellation flag

                stdOutThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                        String line;
                        while ((line = reader.readLine()) != null && !isCancelled.get()) {
                            final String outputLine = line + "\n";
                            runOnUiThread(() -> updateOutput(outputLine));
                        }
                    } catch (IOException e) {
                        if (!isCancelled.get()) {
                            runOnUiThread(() -> updateOutput("Error: " + e.getMessage()));
                        }
                        Log.e("SSH", "Error reading stdout: " + e.getMessage());
                    }
                });

                stdErrThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(err))) {
                        String line;
                        while ((line = reader.readLine()) != null && !isCancelled.get()) {
                            final String errorLine = line + "\n";
                            runOnUiThread(() -> updateOutput(errorLine));
                            Log.i("SSH", "Error: " + errorLine);
                        }
                    } catch (IOException e) {
                        if (!isCancelled.get()) {
                            runOnUiThread(() -> updateOutput("Error: " + e.getMessage()));
                        }
                        Log.e("SSH", "Error reading stderr: " + e.getMessage());
                    }
                });

                stdOutThread.start();
                stdErrThread.start();

                stdOutThread.join();
                stdErrThread.join();

                channelExec.disconnect();
                session.disconnect();

                runOnUiThread(this::resetUI);

            } catch (JSchException | IOException | InterruptedException e) {
                if (!isCancelled.get()) {
                    runOnUiThread(() -> updateOutput("Error: " + e.getMessage()));
                }
            }
        });
    }

    private void resetUI() {
        cancelButton.setVisibility(View.GONE); // Hide the cancel button
        outputScrollView.setVisibility(View.GONE);
        outputTextView.setVisibility(View.GONE);

        command = "";
        enterButton.setVisibility(View.VISIBLE);

        inputAutoComplete.setVisibility(View.VISIBLE);
        inputAutoComplete.requestFocus();

        runOnUiThread(() -> textView2.setVisibility(View.GONE));
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(inputAutoComplete, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void cancelCommand() {
        isCancelled.set(true);
        if (stdOutThread != null && stdOutThread.isAlive()) {
            stdOutThread.interrupt();
        }
        if (stdErrThread != null && stdErrThread.isAlive()) {
            stdErrThread.interrupt();
        }
        if (channelExec != null && channelExec.isConnected()) {
            channelExec.disconnect();
        }
        resetUI();
    }

    private void updateOutput(String chunk) {
        outputTextView.append(chunk);
        outputScrollView.post(() -> outputScrollView.fullScroll(View.FOCUS_DOWN));
    }
}
