package com.sativa.ssh4android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity3 extends Activity {
    private static final int FILE_PICKER_REQUEST_CODE = 123;
    private AutoCompleteTextView inputAutoComplete;
    private Button enterButton;
    private List<String> questions;
    private int currentQuestionIndex;
    private static final String INPUT_HISTORY_KEY = "input_history";
    private String username;
    private String serverAddress;
    private String password;
    private AlertDialog alertDialog;
    private Set<String> inputHistory;
    private ProgressBar progressBar;
    protected String remoteFileDestination;
    private CheckBox savePasswordCheckbox;

    private final AtomicInteger lastProgress = new AtomicInteger(-1);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        getWindow().setBackgroundDrawableResource(R.drawable.panther);

        inputAutoComplete = findViewById(R.id.inputAutoComplete);
        enterButton = findViewById(R.id.enterButton);
        progressBar = findViewById(R.id.progressBar);
        savePasswordCheckbox = findViewById(R.id.savePasswordCheckbox);

        inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);

        inputAutoComplete.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                enterButton.performClick();
                return true;
            }
            return false;
        });

        inputAutoComplete.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);


        SharedPreferences sharedPreferences = getSharedPreferences("InputHistory", MODE_PRIVATE);
        inputHistory = new HashSet<>(sharedPreferences.getStringSet(INPUT_HISTORY_KEY, new HashSet<>()));

        // Set up AutoCompleteTextView with input history
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(inputHistory));
        inputAutoComplete.setAdapter(autoCompleteAdapter);


        questions = new ArrayList<>();
        questions.add("SSH server address?");
        questions.add("Username?");
        questions.add("Password?");


        currentQuestionIndex = 0;
        setNextQuestion();

        saveInputHistory(new ArrayList<>(inputHistory));

        progressBar.setVisibility(View.GONE); // Set initial visibility to GONE

        enterButton.setOnClickListener(view -> handleInput());


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
        inputAutoComplete.setText("");
        currentQuestionIndex++;

        if (currentQuestionIndex == questions.size()) {
            // Autofill the password if available for the corresponding username and server address
            SharedPreferences sharedPreferences = getSharedPreferences("SavedCredentials", MODE_PRIVATE);
            String savedUsername = sharedPreferences.getString("savedUsername", null);
            String savedServerAddress = sharedPreferences.getString("savedServerAddress", null);

            if (savedUsername != null && savedServerAddress != null) {
                String savedPassword = getPassword(savedServerAddress, savedUsername);

                if (savedPassword != null) {
                    inputAutoComplete.setText(savedPassword);
                }
            }

            if (currentQuestionIndex >= questions.size()) {
                // All questions answered, initiate connection and command execution
                enterButton.setText(R.string.connect2);

                Log.d("MainActivity3", "serverAddress Status: " + serverAddress);
                Log.d("MainActivity3", "username Status: " + username);
                Log.d("MainActivity3", "savedPassword Status: " + inputAutoComplete.getText().toString());
            }
        }
    }

    private void updateInputHistory(String newInput) {
        inputHistory.add(newInput);
        saveInputHistory(new ArrayList<>(inputHistory));
    }

    private void handleInput() {
        String input = inputAutoComplete.getText().toString();

        // Update input history
        updateInputHistory(input);

        // Update input history
        Set<String> inputHistory = loadInputHistory();
        inputHistory.add(input);
        saveInputHistory(new ArrayList<>(inputHistory));

        boolean savePassword = false;

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
                savePassword = savePasswordCheckbox.isChecked();
                password = input;
                inputAutoComplete.setText("");
                savePasswordCheckbox.setVisibility(View.GONE);
                inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);
                break;
        }

        if (currentQuestionIndex < questions.size()) {
            // Set next question
            setNextQuestion();
        } else {
            // All questions answered, initiate connection and command execution
            if (savePassword) {
                savePassword();
            }
            connectAndExecuteCommand();
        }
        }
    // Add a method to save the password to SharedPreferences
    private void savePassword() {
        SharedPreferences sharedPreferences = getSharedPreferences("SavedCredentials", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // Retrieve existing passwords map
        Map<String, String> passwordsMap = getPasswordsMap();

        // Save the new password for the current server address and username
        passwordsMap.put(serverAddress + "_" + username, password);

        // Save the updated passwords map
        savePasswordsMap(passwordsMap);

        editor.putString("savedServerAddress", serverAddress);
        editor.putString("savedUsername", username);
        editor.apply();
    }

    private Map<String, String> getPasswordsMap() {
        SharedPreferences sharedPreferences = getSharedPreferences("SavedCredentials", MODE_PRIVATE);
        String passwordsJson = sharedPreferences.getString("passwordsMap", "{}");
        return new Gson().fromJson(passwordsJson, new TypeToken<Map<String, String>>() {}.getType());
    }

    private void savePasswordsMap(Map<String, String> passwordsMap) {
        SharedPreferences sharedPreferences = getSharedPreferences("SavedCredentials", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String passwordsJson = new Gson().toJson(passwordsMap);
        editor.putString("passwordsMap", passwordsJson);
        editor.apply();
    }

    private String getPassword(String serverAddress, String username) {
        // Retrieve passwords map
        Map<String, String> passwordsMap = getPasswordsMap();

        // Get the password for the given server address and username
        return passwordsMap.get(serverAddress + "_" + username);
    }


    private void connectAndExecuteCommand() {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            Session session = null;
            String hostKey = null;

            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, serverAddress, 22);

                if (password != null) {
                    session.setPassword(password);
                } else {
                    // Handle the case where the password is not available
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Saved password not found."));
                    return;
                }

                session.setConfig("StrictHostKeyChecking", "ask");
                session.setConfig("PreferredAuthentications", "password");
                session.connect();
            } catch (JSchException ex) {
                if (session != null) {
                    hostKey = session.getHostKey().getFingerPrint(null);
                }
            } finally {
                if (session != null) {
                    session.disconnect();
                }
            }

            final String finalHostKey = hostKey;
            runOnUiThread(() -> {
                if (finalHostKey != null) {
                    // Show the host key dialog for verification
                    showHostKeyDialog(finalHostKey);
                } else {
                    CustomToast.showCustomToast(getApplicationContext(), "Host key error.");
                }
            });
        });
    }

    private void showHostKeyDialog(String hostKey) {
        // Inflate the custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_host_key, null);

        // Find UI elements in the inflated layout
        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        Button acceptButton = dialogView.findViewById(R.id.button_accept);
        Button denyButton = dialogView.findViewById(R.id.button_deny);

        // Set content and behavior for the dialog elements
        titleTextView.setText(R.string.host_key_verification6);
        messageTextView.setText(String.format("%s%s%s", getString(R.string.host_key_fingerprint7), hostKey, getString(R.string.do_you_want_to_accept_it)));


        // Set click listeners for buttons
        acceptButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity3.this, R.anim.bounce);
            acceptButton.startAnimation(myAnim);
            // Handle host key acceptance
            // You can continue with the remote file transfer here
            alertDialog.dismiss(); // Dismiss the dialog
            openFilePicker();
        });

        denyButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity3.this, R.anim.bounce);
            denyButton.startAnimation(myAnim);
            // Handle host key denial
            // Show a message or take appropriate action
            alertDialog.dismiss(); // Dismiss the dialog
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Host key denied."));
            Intent intent = new Intent(MainActivity3.this, MainActivity.class);
            startActivity(intent);
            finish(); // Close the current activity (MainActivity3)
        });

        // Create and show the AlertDialog with the custom layout
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity3.this);
        builder.setView(dialogView);
        alertDialog = builder.create();
        alertDialog.show();

        inputAutoComplete.setText("");
        inputAutoComplete.setEnabled(false);
        enterButton.setEnabled(false);
        inputAutoComplete.setVisibility(View.GONE);
        enterButton.setVisibility(View.GONE);
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // Allow all file types
        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
    }

    private String getFilePathFromUri(Uri uri) {
        ContentResolver resolver = getContentResolver();

        // Get the display name, which is the original file name
        String fileName = null;
        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            fileName = cursor.getString(nameIndex);
            cursor.close();
        }

        if (fileName == null) {
            // If display name is not available, fallback to a temporary file name
            fileName = "temp_file";
        }

        File tempFile = new File(getCacheDir(), fileName);

        try {
            InputStream inputStream = resolver.openInputStream(uri);
            if (inputStream != null) {
                OutputStream outputStream = Files.newOutputStream(tempFile.toPath());
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                outputStream.close();
                return tempFile.getAbsolutePath();
            }
        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException: " + e.getMessage()));
        }

        return null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri selectedFileUri = data.getData();
            executeFileUpload(selectedFileUri);
        }
    }

    private void executeFileUpload(Uri selectedFileUri) {
        Executor executor = Executors.newSingleThreadExecutor();
        progressBar.setVisibility(View.VISIBLE);

        executor.execute(() -> {
            // Set the local file location from the selected file URI
            String localFileLocation = getFilePathFromUri(selectedFileUri);
            // Check if localFileLocation is not null before using it
            if (localFileLocation != null) {

                // Set the remote file destination with the correct name
                String remoteFileName = new File(localFileLocation).getName();
                remoteFileDestination = "/home/" + username + "/Downloads/" + remoteFileName; // Use correct name

                // Create a new session for file transfer
                JSch jsch = new JSch();
                Session transferSession = null;
                try {
                    transferSession = jsch.getSession(username, serverAddress, 22);
                } catch (JSchException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "JSchException: " + e.getMessage()));
                }
                assert transferSession != null;
                transferSession.setConfig("StrictHostKeyChecking", "no");
                transferSession.setConfig("PreferredAuthentications", "password");
                transferSession.setPassword(password);
                try {
                    transferSession.connect();
                } catch (JSchException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "JSchException: " + e.getMessage()));
                }

                Channel channel = null;
                try {
                    channel = transferSession.openChannel("sftp");
                } catch (JSchException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "JSchException: " + e.getMessage()));
                }

                try {
                    assert channel != null;
                    channel.connect();
                } catch (JSchException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "JSchException: " + e.getMessage()));
                }

                ChannelSftp sftpChannel = (ChannelSftp) channel;

                // Set up the progress monitor
                SftpProgressMonitor progressMonitor = new SftpProgressMonitor() {
                    private long max;
                    private long transferred;

                    @Override
                    public void init(int op, String src, String dest, long max) {
                        // Initialization, if needed
                        transferred = 0;
                        this.max = max; // Set the max value
                    }

                    @Override
                    public boolean count(long count) {
                        try {
                            transferred += count;

                            if (max > 0) {
                                int progress = (int) ((transferred * 100) / max);
                                int finalProgress = Math.min(progress, 100);

                                // Only update the progress if it has changed
                                if (finalProgress != lastProgress.get()) {
                                    lastProgress.set(finalProgress);
                                    runOnUiThread(() -> progressBar.setProgress(finalProgress));
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Exception: " + e.getMessage()));
                        }
                        return true;
                    }

                    @Override
                    public void end() {
                        // Cleanup, if needed
                    }
                };

                try {
                    // Set the progress monitor on the SFTP channel
                    sftpChannel.put(localFileLocation, remoteFileDestination, progressMonitor);

                    // Disconnect the transfer session
                    sftpChannel.exit();
                    transferSession.disconnect();
                } catch (SftpException e) {
                    // Handle exceptions
                    e.printStackTrace();
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "SftpException: " + e.getMessage()));                } finally {
                    if (transferSession.isConnected()) {
                        transferSession.disconnect();
                    }
                }
            }

            // Update UI on the main thread
            runOnUiThread(() -> {
                progressBar.setProgress(0);
                progressBar.setVisibility(View.GONE);
                GreenCustomToast.showCustomToast(getApplicationContext(), "File Uploaded.");

                showChooseDialog();
            });
        });
    }

    // Define showChooseDialog() outside of any other methods
    private void showChooseDialog() {
        // Inflate the custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.choose, null);

        // Find UI elements in the inflated layout
        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        Button filePickerButton = dialogView.findViewById(R.id.filePickerButton);
        Button finishButton = dialogView.findViewById(R.id.finishButton);

        // Set content and behavior for the dialog elements
        titleTextView.setText(R.string.another_upload);
        messageTextView.setText(R.string.or_continue_to_main_menu2);

        // Set click listeners for buttons
        filePickerButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity3.this, R.anim.bounce);
            filePickerButton.startAnimation(myAnim);
            // Handle file picker button click
            alertDialog.dismiss(); // Dismiss the dialog
            openFilePicker();
        });

        finishButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity3.this, R.anim.bounce);
            finishButton.startAnimation(myAnim);
            // Handle finish button click
            alertDialog.dismiss(); // Dismiss the dialog
            // Start MainActivity when file upload completes
            Intent intent = new Intent(MainActivity3.this, MainActivity.class);
            startActivity(intent);
            finish(); // Close the current activity (MainActivity3)
        });

        // Create and show the AlertDialog with the custom layout
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity3.this);
        builder.setView(dialogView);
        alertDialog = builder.create();
        alertDialog.show();
    }
}