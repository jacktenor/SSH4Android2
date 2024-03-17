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
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

public class MainActivity2 extends Activity {
    private AutoCompleteTextView inputAutoComplete;
    private Button enterButton;
    private ListView fileListView;
    private List<String> questions;
    private int currentQuestionIndex;
    private static final String INPUT_HISTORY_KEY = "input_history";
    private String username;
    private List<String> directoryContents;
    private String serverAddress;
    private String password;
    private List<String> fileList;
    private AlertDialog alertDialog;
    private String currentRemoteDirectory = ".";
    private Set<String> inputHistory;
    private static final String GO_UP = "PARENT DIRECTORY\n";
    private ProgressBar progressBar;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private CheckBox savePasswordCheckbox;
    private View button;
    private final String command = "ls";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        Executor executor = Executors.newSingleThreadExecutor();
        CompletableFuture.runAsync(this::performSSHOperations, executor);

        getWindow().setBackgroundDrawableResource(R.drawable.panther);

        checkAndRequestPermission();

        button = findViewById(R.id.button);
        inputAutoComplete = findViewById(R.id.inputAutoComplete);
        enterButton = findViewById(R.id.enterButton);
        fileListView = findViewById(R.id.fileListView);
        progressBar = findViewById(R.id.progressBar);
        savePasswordCheckbox = findViewById(R.id.savePasswordCheckbox);
        CheckBox showHiddenFilesCheckbox = findViewById(R.id.showHiddenFilesCheckbox);

        inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);

        button.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
            button.startAnimation(myAnim);
            Intent i = new Intent(MainActivity2.this, MainActivity.class);
            startActivity(i);
        });

        inputAutoComplete.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                enterButton.performClick();
                return true;
            }
            return false;
        });

        showHiddenFilesCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Call connectAndListDirectory based on checkbox state
            connectAndListDirectory(isChecked);
        });

        inputAutoComplete.requestFocus();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        showHiddenFilesCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Call connectAndListDirectory based on checkbox state
            connectAndListDirectory(isChecked);
        });


        SharedPreferences sharedPreferences = getSharedPreferences("InputHistory", MODE_PRIVATE);
        inputHistory = new HashSet<>(sharedPreferences.getStringSet(INPUT_HISTORY_KEY, new HashSet<>()));

        // Set up AutoCompleteTextView with input history for non-password inputs
        if (currentQuestionIndex != 3) {
            Set<String> inputHistory = loadInputHistory();
            ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(inputHistory));
            inputAutoComplete.setAdapter(autoCompleteAdapter);
        } else {
            // Remove the password from the adapter during the password entry phase
            inputAutoComplete.setAdapter(null);
        }

        questions = new ArrayList<>();
        questions.add("SSH server address?");
        questions.add("Username?");
        questions.add("Password?");

        currentQuestionIndex = 0;
        setNextQuestion();

        saveInputHistory(new ArrayList<>(inputHistory));
        fileList = new ArrayList<>();
        directoryContents = new ArrayList<>();  // Initialize as an empty list

        enterButton.setOnClickListener(view -> handleInput());
        final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
        enterButton.startAnimation(myAnim);

        fileListView.setOnItemClickListener((parent, view, position, id) -> downloadFile(fileList.get(position)));
        final Animation myAnim2 = AnimationUtils.loadAnimation(this, R.anim.bounce);
        fileListView.startAnimation(myAnim2);

        // Add this block for handling long press on directory
        fileListView.setOnItemLongClickListener((parent, view, position, id) -> {
            final Animation myAnim3 = AnimationUtils.loadAnimation(this, R.anim.bounce);
            fileListView.startAnimation(myAnim3);
            String selectedFile = fileList.get(position);
            String fullFilePath = currentRemoteDirectory + "/" + selectedFile;

            // Check if the selected item is a directory
            if (directoryContents != null) {
                // Selected item is a directory, show a dialog to confirm directory download
                showChooseDialog(fullFilePath);
            }
            // Return true to consume the long click event
            return true;
        });
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

    // Define showChooseDialog() outside of any other methods
    private void showChooseDialog(String fullFilePath) {
        // Inflate the custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.choose2, null);

        // Find UI elements in the inflated layout
        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        Button filePickerButton = dialogView.findViewById(R.id.filePickerButton);
        Button uncompressedButton = dialogView.findViewById(R.id.uncompressedButton);
        Button finishButton = dialogView.findViewById(R.id.finishButton);
        // Declare and initialize the remotePath variable

        // Set content and behavior for the dialog elements
        titleTextView.setText(String.format("%s%s", getString(R.string.download_directory2), fullFilePath));
        messageTextView.setText(R.string.or_cancel2);

        // Set click listeners for buttons
        filePickerButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity2.this, R.anim.bounce);
            filePickerButton.startAnimation(myAnim);
            // Handle file picker button click
            alertDialog.dismiss(); // Dismiss the dialog
            String remoteDirectory = "";
            downloadCompressedDirectory(fullFilePath, getLocalDownloadPath(remoteDirectory));
        });

        finishButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity2.this, R.anim.bounce);
            finishButton.startAnimation(myAnim);
            // Handle finish button click
            alertDialog.dismiss();
        });

        uncompressedButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity2.this, R.anim.bounce);
            uncompressedButton.startAnimation(myAnim);
            // Handle finish button click
            alertDialog.dismiss();
            String remoteDirectory = "";
            downloadDirectory(fullFilePath, getLocalDownloadPath(remoteDirectory));

        });

        // Create and show the AlertDialog with the custom layout
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity2.this);
        builder.setView(dialogView);
        alertDialog = builder.create();
        alertDialog.show();
    }

    private String getLocalDownloadPath(String remoteDirectory) {
        // Customize this method to generate the local download path based on your requirements
        // For example, you might want to use the remote directory name as the local folder name
        String remoteDirectoryName = remoteDirectory.substring(remoteDirectory.lastIndexOf("/") + 1);
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + remoteDirectoryName;
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

        // Save the new password for the current server address and username
        Credential credential = new Credential(serverAddress, username, password);
        credential.saveCredentials(getApplicationContext());

        // Retrieve saved credentials
        Credential savedCredentials = Credential.getSavedCredentials(getApplicationContext());

        if (savedCredentials != null && currentQuestionIndex == 3
                && savedCredentials.getServerAddress().equals(serverAddress)
                && savedCredentials.getUsername().equals(username)) {
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

    private void handleInput() {
        String input = inputAutoComplete.getText().toString();

        // Update input history
        updateInputHistory(input);

        // Update input history
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
                inputAutoComplete.setText("");
                savePasswordCheckbox.setVisibility(View.GONE);

                inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);
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
        // Retrieve passwords map
        Map<String, String> passwordsMap = getPasswordsMap();

        // Get the password for the given server address and username
        return passwordsMap.get(serverAddress + "_" + username);
    }


    private void connectAndExecuteCommand() {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {

            String keysDirectory = getApplicationContext().getFilesDir().getPath();
            String privateKeyPathAndroid = keysDirectory + "/ssh4android";

            Session session = null;
            String hostKey = null;

            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "yes");
                session.setConfig("PreferredAuthentications", "publickey,password");
                jsch.addIdentity(privateKeyPathAndroid);
                session.setPassword(password);
                session.connect();
            } catch (JSchException ex) {
                if (session != null) {
                    hostKey = session.getHostKey().getFingerPrint(null);
                } else {
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Host key error."));
                }
            }

            final String finalHostKey = hostKey;
            runOnUiThread(() -> {
                if (finalHostKey != null) {
                    // Show the host key dialog for verification
                    showHostKeyDialog(finalHostKey);
                } else {
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Host key error."));
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
            performSSHOperations();
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

    private void performSSHOperations() {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            String keysDirectory = getApplicationContext().getFilesDir().getPath();
            String privateKeyPathAndroid = keysDirectory + "/ssh4android";
            String publicKeyPathAndroid = keysDirectory + "/ssh4android.pub";
            String publicKeyPathServer = "/home/" + username + "/.ssh/authorized_keys";

            try {
                JSch jsch = new JSch();

                if (!Files.exists(Paths.get(privateKeyPathAndroid))) {
                    KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA);
                    keyPair.writePrivateKey(privateKeyPathAndroid);
                    Log.d("SSH", "Generating private key... : " + privateKeyPathAndroid);
                    Files.setPosixFilePermissions(Paths.get(privateKeyPathAndroid), PosixFilePermissions.fromString("rw-------"));

                    byte[] publicKeyBytes = keyPair.getPublicKeyBlob();
                    String publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes);

                    try (FileWriter writer = new FileWriter(publicKeyPathAndroid)) {
                        writer.write("ssh-rsa " + publicKeyString + " " + username);
                    } catch (IOException e) {
                        e.printStackTrace();
                        runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException: " + e.getMessage()));
                    }
                }

                Session session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("PreferredAuthentications", "publickey,password");
                jsch.addIdentity(privateKeyPathAndroid);
                session.setPassword(password);
                try {
                    session.connect();
                    Log.d("SSH", "Authentication successful");
                    uploadPublicKey(session, publicKeyPathAndroid, publicKeyPathServer);
                } catch (JSchException keyAuthException) {
                    keyAuthException.printStackTrace();
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "JSchException: " + keyAuthException.getMessage()));
                }
                connectAndExecuteCommand2();

                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (JSchException | IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void uploadPublicKey(Session session, String publicKeyPathAndroid, String publicKeyPathServer)
            throws JSchException, IOException {

        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.connect();

        try (InputStream publicKeyStream = Files.newInputStream(Paths.get(publicKeyPathAndroid))) {

            Log.d("SSH", "publicKeyPathAndroid(upload): " + publicKeyPathAndroid);
            Log.d("SSH", "publicKeyPathServer(upload): " + publicKeyPathServer);

            // Read the existing authorized_keys content
            String existingKeysContent = readExistingKeys(session, publicKeyPathServer);

            // Check if the key already exists
            if (!existingKeysContent.contains(new String(Files.readAllBytes(Paths.get(publicKeyPathAndroid))))) {
                // Append the new key with a newline character at the beginning
                String newKeyContent = "\n" + new String(Files.readAllBytes(Paths.get(publicKeyPathAndroid)));
                String updatedKeysContent = existingKeysContent + newKeyContent;

                // Write the updated content back to the authorized_keys file
                try (InputStream updatedKeysStream = new ByteArrayInputStream(updatedKeysContent.getBytes())) {
                    channelSftp.put(updatedKeysStream, publicKeyPathServer);
                } catch (IOException | SftpException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException | SftpException: " + e.getMessage()));
                }
            } else {
                Log.d("SSH", "Key already exists in authorized_keys file. Skipping upload.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException: " + e.getMessage()));
        } finally {
            channelSftp.disconnect();
        }
    }

    // Read existing keys from the authorized_keys file
    private String readExistingKeys(Session session, String publicKeyPathServer) throws JSchException, IOException {
        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.connect();

        try (InputStream existingKeysStream = channelSftp.get(publicKeyPathServer)) {
            return new String(readAllBytes(existingKeysStream));
        } catch (SftpException e) {
            // Handle the case where the authorized_keys file doesn't exist yet
            return "";
        } finally {
            channelSftp.disconnect();
        }
    }

    // Replace InputStream#readAllBytes with the alternative method
    private byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }

    private void connectAndExecuteCommand2() {
        // Use the activityReference field
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            String keysDirectory = getApplicationContext().getFilesDir().getPath();
            String privateKeyPathAndroid = keysDirectory + "/ssh4android";

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
                session.setConfig("PreferredAuthentications", "publickey,password");
                jsch.addIdentity(privateKeyPathAndroid);
                session.setPassword(password);
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
                        runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Connection failed"));
                    }
                }
            });
        });

        CheckBox showHiddenFilesCheckbox = findViewById(R.id.showHiddenFilesCheckbox); // Initialize the checkbox
        runOnUiThread(() -> showHiddenFilesCheckbox.setVisibility(View.VISIBLE));
        runOnUiThread(() -> inputAutoComplete.setText(""));
        runOnUiThread(() -> inputAutoComplete.setVisibility(View.GONE));
        runOnUiThread(() -> inputAutoComplete.setEnabled(false));
        runOnUiThread(() -> enterButton.setEnabled(false));
        runOnUiThread(() -> enterButton.setVisibility(View.GONE));
        runOnUiThread(() -> fileListView.setVisibility(VISIBLE));
    }

    private void displayOutput(String output) {
        fileList.clear();  // Clear the list before adding new files
        String[] entries = output.split("\\s+");

        Collections.addAll(fileList, entries);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, fileList);
        fileListView.setAdapter(adapter);
    }

    private void downloadFile(final String filepath) {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            String keysDirectory = getApplicationContext().getFilesDir().getPath();
            String privateKeyPathAndroid = keysDirectory + "/ssh4android";

            WeakReference<MainActivity2> activityReference = new WeakReference<>(MainActivity2.this);
            List<String> directoryContents = null;
            boolean success = false;

            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("PreferredAuthentications", "publickey,password");
                jsch.addIdentity(privateKeyPathAndroid);
                session.setPassword(password);
                session.connect();

                ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                SftpATTRS attrs = channelSftp.lstat(filepath);

                // Check if the clicked item is a directory
                if (attrs.isDir()) {
                    // Update the fileListView with the contents of the clicked directory
                    channelSftp.cd(filepath);
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
                    long fileSize = attrs.getSize();

                    // Set up a buffer for reading the file
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    long downloadedSize = 0;

                    // Set the local download path based on the file name
                    String fileName = filepath.substring(filepath.lastIndexOf("/") + 1);
                    String localDownloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/" + fileName;

                    // Open an OutputStream to write the file locally
                    try (OutputStream outputStream = Files.newOutputStream(Paths.get(localDownloadPath))) {
                        // Open an InputStream to read the file remotely
                        InputStream inputStream = channelSftp.get(filepath);

                        while ((bytesRead = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, bytesRead);
                            runOnUiThread(() -> progressBar.setVisibility(VISIBLE));

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
                        runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Directory download error: " + e.getMessage()));
                    }
                }

                // Disconnect the channel and session
                channelSftp.disconnect();
                session.disconnect();

                boolean finalSuccess = success;
                List<String> finalDirectoryContents = directoryContents;
                String fileName = filepath.substring(filepath.lastIndexOf("/") + 1);

                runOnUiThread(() -> {
                    MainActivity2 activity = activityReference.get();
                    if (activity != null && !activity.isFinishing()) {
                        if (finalSuccess) {
                            runOnUiThread(() -> GreenCustomToast.showCustomToast(activity.getApplicationContext(), "File downloaded: " + fileName));
                        } else if (finalDirectoryContents != null) {
                            // If the clicked item was a directory, update the fileListView with its contents
                            activity.updateFileListView(finalDirectoryContents);
                        } else {
                            runOnUiThread(() -> CustomToast.showCustomToast(activity.getApplicationContext(), "Download or directory traversal failed."));
                        }

                        runOnUiThread(() -> progressBar.setProgress(0));
                        runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                    }
                });
            } catch (JSchException | SftpException e) {
                e.printStackTrace();
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), ""));//todo
            }
        });
    }

    private void downloadDirectory(final String remotePath, final String localParentPath) {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            String keysDirectory = getApplicationContext().getFilesDir().getPath();
            String privateKeyPathAndroid = keysDirectory + "/ssh4android";

            WeakReference<MainActivity2> activityReference = new WeakReference<>(MainActivity2.this);
            boolean success = false;

            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("PreferredAuthentications", "publickey,password");
                jsch.addIdentity(privateKeyPathAndroid);
                session.setPassword(password);
                session.connect();

                ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                // Ensure we change to the correct remote directory
                channelSftp.cd(remotePath);

                // Get the list of files in the remote directory
                Vector<ChannelSftp.LsEntry> fileList = channelSftp.ls("*");

                // Extract the name of the remote directory
                String remoteDirectoryName = new File(remotePath).getName();

                // Create the local directory with the remote directory's name if it doesn't exist
                Path localDirectoryPath = Paths.get(localParentPath, remoteDirectoryName);
                Files.createDirectories(localDirectoryPath);

                // Initialize progress variables for the entire directory
                long totalSize = 0;
                long downloadedSize = 0;

                // Calculate the total size of all files for progress calculation
                for (ChannelSftp.LsEntry entry : fileList) {
                    totalSize += entry.getAttrs().getSize();
                }

                // Download each file in the remote directory
                for (ChannelSftp.LsEntry entry : fileList) {
                    String remoteFile = entry.getFilename();
                    String localFile = localDirectoryPath + File.separator + remoteFile;

                    // If the entry is a directory, recursively download it
                    if (entry.getAttrs().isDir()) {
                        downloadDirectory(remotePath + "/" + remoteFile, localDirectoryPath.toString());
                    } else {
                        // Set up a buffer for reading the file
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        runOnUiThread(() -> progressBar.setVisibility(VISIBLE));

                        // Open an OutputStream to write the file locally
                        try (OutputStream outputStream = Files.newOutputStream(Paths.get(localFile));
                             InputStream inputStream = channelSftp.get(remoteFile)) {

                            while ((bytesRead = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, bytesRead);

                                // Calculate and publish the download progress for the entire directory
                                downloadedSize += bytesRead;
                                int progress = (int) ((downloadedSize * 100) / totalSize);
                                runOnUiThread(() -> progressBar.setProgress(progress));
                            }
                        }
                    }
                }

                // Disconnect the channel and session
                channelSftp.disconnect();
                session.disconnect();

                success = true;
            } catch (JSchException | SftpException | IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Error during directory download: " + e.getMessage()));
            }

            boolean finalSuccess = success;
            runOnUiThread(() -> {
                MainActivity2 activity = activityReference.get();
                if (activity != null && !activity.isFinishing()) {
                    if (finalSuccess) {
                        GreenCustomToast.showCustomToast(activity.getApplicationContext(), "Directory downloaded: " + remotePath);
                    } else {
                        CustomToast.showCustomToast(activity.getApplicationContext(), "Directory download failed.");
                    }

                    progressBar.setProgress(0);
                    progressBar.setVisibility(View.GONE);
                }
            });
        });
    }


    private void updateFileListView(List<String> newDirectoryContents) {
        fileList.clear();  // Clear the list before adding new files

        // Use a case-insensitive comparator for sorting
        if (newDirectoryContents != null) {
            newDirectoryContents.sort(String.CASE_INSENSITIVE_ORDER);

            // Add the "go up" item at the top
            fileList.add(GO_UP);

            // Add new files and directories
            fileList.addAll(newDirectoryContents);
        }

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
                if (newDirectoryContents != null && newDirectoryContents.contains(selectedFile)) {
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

            // Call connectAndListDirectory based on checkbox state
            connectAndListDirectory(true);
        }

    private void downloadCompressedDirectory(final String remotePath, final String localParentPath) {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            String keysDirectory = getApplicationContext().getFilesDir().getPath();
            String privateKeyPathAndroid = keysDirectory + "/ssh4android";

            WeakReference<MainActivity2> activityReference = new WeakReference<>(MainActivity2.this);
            boolean success = false;

            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("PreferredAuthentications", "publickey,password");
                jsch.addIdentity(privateKeyPathAndroid);
                session.setPassword(password);
                session.connect();

                ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                // Navigate into the selected remote directory
                channelSftp.cd(remotePath);

                // Navigate back out to the original directory
                channelSftp.cd("..");

                // Extract the name of the remote directory
                String remoteDirectoryName = new File(remotePath).getName();

                // Create the local directory with the remote directory's name if it doesn't exist
                Path localDirectoryPath = Paths.get(localParentPath, remoteDirectoryName);
                Files.createDirectories(localDirectoryPath);

                // Create the local compressed file path
                String compressedFileName = remoteDirectoryName + ".tar.gz";
                String compressedFilePath = localParentPath + File.separator + compressedFileName;

                // Compress the remote directory
                compressDirectory(remotePath, compressedFilePath, channelSftp);

                // Download the compressed archive
                downloadFile(compressedFilePath);

                // Disconnect the channel and session
                channelSftp.disconnect();
                session.disconnect();

                success = true;
            } catch (JSchException | SftpException | IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Error during directory compression/download: " + e.getMessage()));
            }

            boolean finalSuccess = success;
            runOnUiThread(() -> {
                MainActivity2 activity = activityReference.get();
                if (activity != null && !activity.isFinishing()) {
                    if (finalSuccess) {
                        GreenCustomToast.showCustomToast(activity.getApplicationContext(), "Directory downloaded: " + remotePath);
                    } else {
                        CustomToast.showCustomToast(activity.getApplicationContext(), "Directory download failed.");
                    }
                    runOnUiThread(() -> progressBar.setProgress(0));  // Set progress to 0 upon completion
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                }
            });
        });
    }

    private void compressDirectory(String remotePath, String compressedFilePath, ChannelSftp channelSftp) {
        try (TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(new GZIPOutputStream(Files.newOutputStream(Paths.get(compressedFilePath))))) {
            // Get the total number of files in the remote directory
            Vector<ChannelSftp.LsEntry> fileList = channelSftp.ls(remotePath + "/*");

            // Set the maximum progress value for the entire directory
            int totalFiles = fileList.size();
            int maxProgress = totalFiles * 100;

            // Update progress for directory compression start
            runOnUiThread(() -> progressBar.setProgress(0));
            compressDirectoryRecursive(remotePath, "", tarArchiveOutputStream, channelSftp, maxProgress);
            tarArchiveOutputStream.finish();
        } catch (IOException | SftpException e) {
            e.printStackTrace();
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Error during directory compression: " + e.getMessage()));
        }
    }

    private void compressDirectoryRecursive(String remotePath, String relativePath, TarArchiveOutputStream tarArchiveOutputStream, ChannelSftp channelSftp, int maxProgress) throws IOException, SftpException {
        // Get the list of files in the remote directory
        Vector<ChannelSftp.LsEntry> fileList = channelSftp.ls(remotePath + "/*");

        // Initialize progress variables
        int currentProgress = 0;
        int cumulativeProgress;

        for (int i = 0; i < fileList.size(); i++) {
            ChannelSftp.LsEntry entry = fileList.get(i);
            String remoteFile = entry.getFilename();
            String remoteFilePath = remotePath + "/" + remoteFile;

            // Create a TarArchiveEntry for each file in the directory
            TarArchiveEntry tarEntry = new TarArchiveEntry(relativePath + remoteFile);
            tarEntry.setSize(entry.getAttrs().getSize());


            if (entry.getAttrs().isDir()) {
                // If it's a directory, recursively compress its content
                compressDirectoryRecursive(remoteFilePath, relativePath + remoteFile + "/", tarArchiveOutputStream, channelSftp, maxProgress);
            } else {
                try (InputStream inputStream = channelSftp.get(remoteFilePath)) {
                    // Put the entry in the tar archive
                    tarArchiveOutputStream.putArchiveEntry(tarEntry);

                    // Write the file content to the tar archive
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        tarArchiveOutputStream.write(buffer, 0, bytesRead);
                    }
                    // Close the current entry
                    tarArchiveOutputStream.closeArchiveEntry();
                }
            }

            runOnUiThread(() -> progressBar.setVisibility(VISIBLE));

            // Update progress for each file processed
            currentProgress++;
            cumulativeProgress = currentProgress * 100 / fileList.size();
            int finalCumulativeProgress = cumulativeProgress;
            runOnUiThread(() -> progressBar.setProgress(Math.min(finalCumulativeProgress, maxProgress)));
        }
    }

        private void connectAndListDirectory(boolean showHiddenFiles) {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {

            String keysDirectory = getApplicationContext().getFilesDir().getPath();
            String privateKeyPathAndroid = keysDirectory + "/ssh4android";

            List<String> newDirectoryContents = new ArrayList<>();

            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("PreferredAuthentications", "publickey,password");
                jsch.addIdentity(privateKeyPathAndroid);
                session.setPassword(password);
                session.connect();

                ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                // Change the current directory on the remote server
                channelSftp.cd(currentRemoteDirectory);

                // List the contents of the current directory, including hidden files if showHiddenFiles is true
                Vector<LsEntry> list = channelSftp.ls(showHiddenFiles ? ".*" : "*");

                for (LsEntry entry : list) {
                    String entryName = entry.getFilename();
                    if (!entryName.equals(".") && !entryName.equals("..")) {
                        newDirectoryContents.add(entryName);
                    }
                }

                // Disconnect the channel and session
                channelSftp.disconnect();
                session.disconnect();
            } catch (JSchException | SftpException e) {
                e.printStackTrace();
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Error during directory listing: " + e.getMessage()));
            }

            runOnUiThread(() -> {
                directoryContents = newDirectoryContents;  // Update the directoryContents variable
                updateFileListView(directoryContents);
            });
        });
    }
}