package com.sativa.ssh4android;

import static android.view.View.VISIBLE;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity2 extends Activity {
    private AutoCompleteTextView inputAutoComplete;
    private Button enterButton;
    private ListView fileListView;
    private List<String> questions;
    private int currentQuestionIndex;
    private static final String INPUT_HISTORY_KEY = "input_history";
    private String username;
    private String serverAddress;
    private String password;
    private String command;
    private List<String> fileList;
    private AlertDialog alertDialog;
    private String currentRemoteDirectory = "/";
    private Set<String> inputHistory;
    private static final String GO_UP = "PARENT DIRECTORY\n";
    private ProgressBar progressBar;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private CheckBox savePasswordCheckbox;
    private View button;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        getWindow().setBackgroundDrawableResource(R.drawable.panther);

        button = findViewById(R.id.button);
        inputAutoComplete = findViewById(R.id.inputAutoComplete);
        enterButton = findViewById(R.id.enterButton);
        fileListView = findViewById(R.id.fileListView);
        progressBar = findViewById(R.id.progressBar);
        savePasswordCheckbox = findViewById(R.id.savePasswordCheckbox);

        inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);

        button.setOnClickListener(view -> {
            Intent i = new Intent(MainActivity2.this, MainActivity.class);
            final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
            button.startAnimation(myAnim);
            startActivity(i);
        });


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
        questions.add("Command?");

        currentQuestionIndex = 0;
        setNextQuestion();

        saveInputHistory(new ArrayList<>(inputHistory));

        enterButton.setOnClickListener(view -> handleInput());

        fileList = new ArrayList<>();

        fileListView.setOnItemClickListener((parent, view, position, id) -> downloadFile(fileList.get(position)));

        // Check and request permission before initiating any file operations
        checkAndRequestPermission();
    }

    private void checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_WRITE_EXTERNAL_STORAGE);
        } else {
            // Permission is already granted, proceed with file operation
            // For example, call connectAndListDirectory();
            loadInputHistory();
        }
    }

    // Override onRequestPermissionsResult to handle the result of the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted, proceed with file operation
                // For example, call connectAndListDirectory();
                loadInputHistory();
            } else {
                // Permission denied, show a message or take appropriate action
                loadInputHistory(); //TODO
            }
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
        inputAutoComplete.setText("");
        currentQuestionIndex++;


        // Autofill the password if available for the corresponding username and server address
        SharedPreferences sharedPreferences = getSharedPreferences("SavedCredentials", MODE_PRIVATE);
        String savedUsername = sharedPreferences.getString("savedUsername", null);
        String savedServerAddress = sharedPreferences.getString("savedServerAddress", null);

        if (savedUsername != null && savedServerAddress != null) {
            String savedPassword = getPassword(savedServerAddress, savedUsername);
            if (savedPassword != null && currentQuestionIndex == 3) {
                inputAutoComplete.setText(savedPassword);
            }

            Log.d("MainActivity2", "savedPassword1: " + savedPassword);
            Log.d("MainActivity2", "serverAddress Status: " + serverAddress);
            Log.d("MainActivity2", "username Status: " + username);
            Log.d("MainActivity2", "savedPassword Status: " + inputAutoComplete.getText().toString());
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

        boolean savePassword = savePasswordCheckbox.isChecked();

        switch (currentQuestionIndex - 1) {
            case 0:
                serverAddress = input;
                break;
            case 1:
                username = input;
                savePasswordCheckbox.setVisibility(VISIBLE);
                inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                break;
            case 2:
                password = input;
                savePasswordCheckbox.setVisibility(View.GONE);
                inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);
                break;
            case 3:
                command = input;
                if (savePassword) {
                    savePassword();
                }
                inputAutoComplete.setText("");
                Log.d("MainActivity2", "savePassword2: " + savePassword);
                break;
        }

        if (currentQuestionIndex < questions.size()) {
            // Set next question
            setNextQuestion();
        } else {
            connectAndExecuteCommand();
        }
    }

    // Add a method to save the password to SharedPreferences
    private void savePassword() {
        Log.d("MainActivity2", "Saving password...");

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

        getPassword(serverAddress, username);
    }

    private String getPassword(String serverAddress, String username) {
        // Retrieve passwords map
        Map<String, String> passwordsMap = getPasswordsMap();

        // Get the password for the given server address and username
        String password = passwordsMap.get(serverAddress + "_" + username);

        // Log the retrieved password
        Log.d("MainActivity2", "Retrieved password: " + password);
        Log.d("MainActivity2", "Retrieved passwordsMap: " + passwordsMap);
        return password;
    }


    private void connectAndExecuteCommand() {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            Session session = null;
            String hostKey = null;

            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "ask");
                session.setConfig("PreferredAuthentications", "password");
                session.setPassword(password);
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
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity2.this, R.anim.bounce);
            acceptButton.startAnimation(myAnim);
            // Handle host key acceptance
            // You can continue with the remote file transfer here
            alertDialog.dismiss(); // Dismiss the dialog
            connectAndExecuteCommand2();
        });

        denyButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity2.this, R.anim.bounce);
            denyButton.startAnimation(myAnim);
            // Handle host key denial
            // Show a message or take appropriate action
            alertDialog.dismiss(); // Dismiss the dialog

            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Host key denied."));
        });

        // Create and show the AlertDialog with the custom layout
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity2.this);
        builder.setView(dialogView);
        alertDialog = builder.create();
        alertDialog.show();
    }

    private void connectAndExecuteCommand2() {
        // Use the activityReference field
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            WeakReference<MainActivity2> activityReference = new WeakReference<>(MainActivity2.this);
            StringBuilder output = new StringBuilder();
            boolean success = false;

            MainActivity2 activity = activityReference.get();
            if (activity == null || activity.isFinishing()) {
                // The activity is no longer available, exit the task
                return;
            }

            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(activity.username, activity.serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setPassword(activity.password);
                session.connect();

                ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
                channelExec.setCommand(activity.command);

                InputStream in = channelExec.getInputStream();
                channelExec.connect();

                byte[] tmp = new byte[1024];
                while (true) {
                    while (in.available() > 0) {
                        int i = in.read(tmp, 0, 1024);
                        if (i < 0) break;
                        output.append(new String(tmp, 0, i));
                    }
                    if (channelExec.isClosed()) {
                        if (in.available() > 0) continue;
                        break;
                    }
                }

                channelExec.disconnect();
                session.disconnect();

                success = true;
            } catch (JSchException | IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Exception: " + e.getMessage()));
            }

            boolean finalSuccess = success;
            runOnUiThread(() -> {
                MainActivity2 finalActivity = activityReference.get();
                if (finalActivity != null && !finalActivity.isFinishing()) {
                    if (finalSuccess) {
                        finalActivity.displayOutput(output.toString());
                    } else {
                        CustomToast.showCustomToast(getApplicationContext(), "Connection failed");
                    }
                }
            });
        });

        fileListView.setVisibility(VISIBLE);
        inputAutoComplete.setText("");
        inputAutoComplete.setEnabled(false);
        enterButton.setEnabled(false);
        inputAutoComplete.setVisibility(View.GONE);
        enterButton.setVisibility(View.GONE);
    }

    private void displayOutput(String output) {
        fileList.clear();  // Clear the list before adding new files
        String[] entries = output.split("\\s+");

        Collections.addAll(fileList, entries);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fileList);
        fileListView.setAdapter(adapter);
    }

    private void downloadFile(final String filePath) {
        // Use the activityReference field
        Executor executor = Executors.newSingleThreadExecutor();
        progressBar.setVisibility(VISIBLE);

        executor.execute(() -> {
            WeakReference<MainActivity2> activityReference = new WeakReference<>(MainActivity2.this);
            List<String> directoryContents = null;
            long fileSize;
            boolean success = false;

            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setPassword(password);
                session.connect();

                ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                SftpATTRS attrs = channelSftp.lstat(filePath);

                // Check if the clicked item is a directory
                if (attrs.isDir()) {

                    // Update the fileListView with the contents of the clicked directory
                    channelSftp.cd(filePath);
                    currentRemoteDirectory = channelSftp.pwd();
                    Vector<LsEntry> rawList = channelSftp.ls("*");
                    List<LsEntry> list = new ArrayList<>(rawList);

                    directoryContents = new ArrayList<>();
                    for (LsEntry entry : list) {
                        String entryName = entry.getFilename();
                        if (!entryName.equals(".") && !entryName.equals("..")) {
                            directoryContents.add(entryName);
                        }
                    }
                } else {
                    // The clicked item is a file, proceed with downloading

                    // Get the file size for progress calculation
                    fileSize = attrs.getSize();

                    // Set up a buffer for reading the file
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    long downloadedSize = 0;

                    // Set the local download path based on the file name
                    String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
                    String localDownloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + fileName;

                    // Open an OutputStream to write the file locally
                    try (OutputStream outputStream = Files.newOutputStream(Paths.get(localDownloadPath))) {
                        // Open an InputStream to read the file remotely
                        InputStream inputStream = channelSftp.get(filePath);

                        while ((bytesRead = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, bytesRead);

                            // Calculate and publish the download progress
                            downloadedSize += bytesRead;
                            int progress = (int) ((downloadedSize * 100) / fileSize);
                            runOnUiThread(() -> progressBar.setProgress(progress));
                        }
                        // Close the streams
                        inputStream.close();

                        success = true;
                    } catch (IOException | SftpException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Error during file download: " + e.getMessage()));
                    }
                }

                // Disconnect the channel and session
                channelSftp.disconnect();
                session.disconnect();
            } catch (JSchException | SftpException e) {
                e.printStackTrace();
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Error during file download: " + e.getMessage()));
            }

            boolean finalSuccess = success;
            List<String> finalDirectoryContents = directoryContents;

            runOnUiThread(() -> {
                MainActivity2 activity = activityReference.get();
                if (activity != null && !activity.isFinishing()) {
                    if (finalSuccess) {
                        GreenCustomToast.showCustomToast(activity.getApplicationContext(), "File downloaded: " + filePath);
                    } else if (finalDirectoryContents != null) {
                        // If the clicked item was a directory, update the fileListView with its contents
                        activity.updateFileListView(finalDirectoryContents);
                    } else {
                        CustomToast.showCustomToast(activity.getApplicationContext(), "Download or directory traversal failed.");
                    }

                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.GONE);
                }
            });
        });
    }

    private void updateFileListView(List<String> directoryContents) {
        fileList.clear();  // Clear the list before adding new files

        // Use a case-insensitive comparator for sorting
        directoryContents.sort(String.CASE_INSENSITIVE_ORDER);

        // Add the "go up" item at the top
        fileList.add(GO_UP);

        // Add new files and directories
        fileList.addAll(directoryContents);

        CustomAdapter adapter = new CustomAdapter(MainActivity2.this, android.R.layout.simple_list_item_1, fileList, GO_UP);
        fileListView.setAdapter(adapter);

        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedFile = fileList.get(position);

            // Check if the selected item is the "go up" item
            if (selectedFile.equals(GO_UP)) {
                // Go up one level in the directory structure
                goUpOneLevel();
            } else {
                String fullFilePath = currentRemoteDirectory + "/" + selectedFile;

                // Check if the selected item is a directory or a file
                if (directoryContents.contains(selectedFile)) {
                    // Selected item is a directory, update the fileListView with its contents
                    downloadFile(fullFilePath);
                } else {
                    // Selected item is a file, download it
                    downloadFile(selectedFile);
                }
            }
        });
    }

    private void goUpOneLevel() {
        // Split the current directory path into components
        String[] pathComponents = currentRemoteDirectory.split("/");

        // Remove the last component to go up one level
        StringBuilder newPath = new StringBuilder();
        for (int i = 0; i < pathComponents.length - 1; i++) {
            newPath.append(pathComponents[i]);
            if (i < pathComponents.length - 2) {
                newPath.append("/");
            }
        }

        // Update the current directory and initiate listing
        currentRemoteDirectory = newPath.toString();
        connectAndListDirectory();
    }

    private void connectAndListDirectory() {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            List<String> directoryContents = new ArrayList<>();

            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setPassword(password);
                session.connect();

                ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                // Change the current directory on the remote server
                channelSftp.cd(currentRemoteDirectory);

                // List the contents of the current directory
                Vector<LsEntry> list = channelSftp.ls("*");

                for (LsEntry entry : list) {
                    String entryName = entry.getFilename();
                    if (!entryName.equals(".") && !entryName.equals("..")) {
                        directoryContents.add(entryName);
                    }
                }

                // Disconnect the channel and session
                channelSftp.disconnect();
                session.disconnect();
            } catch (JSchException | SftpException e) {
                e.printStackTrace();
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Error during directory listing: " + e.getMessage()));
            }

            runOnUiThread(() -> updateFileListView(directoryContents));
        });
    }
}