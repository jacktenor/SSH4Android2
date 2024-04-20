package com.sativa.ssh4android;

import static com.sativa.ssh4android.MainActivity5.REQUEST_WRITE_EXTERNAL_STORAGE;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private Button button;
    private final AtomicInteger lastProgress = new AtomicInteger(-1);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        getWindow().setBackgroundDrawableResource(R.drawable.panther);

        checkAndRequestPermission();

        inputAutoComplete = findViewById(R.id.inputAutoComplete);
        enterButton = findViewById(R.id.enterButton);
        progressBar = findViewById(R.id.progressBar);
        savePasswordCheckbox = findViewById(R.id.savePasswordCheckbox);
        button = findViewById(R.id.button);

        inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);

        button.setOnClickListener(view -> {
            Intent i = new Intent(MainActivity3.this, MainActivity.class);
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

        currentQuestionIndex = 0;
        setNextQuestion();

        saveInputHistory(new ArrayList<>(inputHistory));

        progressBar.setVisibility(View.GONE); // Set initial visibility to GONE

        enterButton.setOnClickListener(view -> handleInput());
    }
    private void checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
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
            Session session = null;
            String hostKey = null;

            try {
                JSch jsch = new JSch();
                session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "yes");
                session.setConfig("PreferredAuthentications", "publickey,password");
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
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity3.this, R.anim.bounce);
            acceptButton.startAnimation(myAnim);
            // Handle host key acceptance
            // You can continue with the remote file transfer here
            performSSHOperations();
            openFilePicker();
            alertDialog.dismiss();
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

    private void performSSHOperations() {
        String publicKeyPathServer = "/home/" + username + "/.ssh/authorized_keys";
        String keysDirectory = getApplicationContext().getFilesDir().getPath();
        String privateKeyPathAndroid = keysDirectory + "/ssh4android";
        String publicKeyPathAndroid = keysDirectory + "/ssh4android.pub";

        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                JSch jsch = new JSch();
                Session session = jsch.getSession(username, serverAddress, 22);
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("PreferredAuthentications", "publickey,password");

                if (!Files.exists(Paths.get(privateKeyPathAndroid))) {
                    KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA);
                    keyPair.writePrivateKey(privateKeyPathAndroid);
                    Files.setPosixFilePermissions(Paths.get(privateKeyPathAndroid), PosixFilePermissions.fromString("rw-------"));

                    byte[] publicKeyBytes = keyPair.getPublicKeyBlob();
                    String publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes);

                    try (FileWriter writer = new FileWriter(publicKeyPathAndroid)) {
                        writer.write("ssh-rsa " + publicKeyString + " " + username);
                    }
                }

                jsch.addIdentity(privateKeyPathAndroid);
                session.setPassword(password);
                session.connect();

                Log.d("SSH", "Key-based authentication successful");
                uploadPublicKey(session, publicKeyPathAndroid, publicKeyPathServer);

                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (JSchException | IOException ex) {
                Log.w("SSH4Android", ex.getMessage(), ex);
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), ex.getClass().getSimpleName() + ": " + ex.getMessage()));
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
                    runOnUiThread(() -> GreenCustomToast.showCustomToast(getApplicationContext(), "Key added to accepted_keys"));
                } catch (IOException | SftpException e) {
                    Log.w("SSH4Android", e.getMessage(), e);
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException | SftpException: " + e.getMessage()));
                }
            } else {
                Log.d("SSH", "Key already exists in authorized_keys file. Skipping upload.");
            }
        } catch (IOException e) {
            Log.w("SSH4Android", e.getMessage(), e);
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException: " + e.getMessage()));
        } finally {
            channelSftp.disconnect();
        }
    }

    // Read existing keys from the authorized_keys file
    private String readExistingKeys (Session session, String publicKeyPathServer) throws
            JSchException, IOException {
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
    private static byte[] readAllBytes (InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();

    }
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // Allow all file types
        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE);
    }

    private String getFilePathFromUri (Uri uri){
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
            Log.w("SSH4Android", e.getMessage(), e);
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException: " + e.getMessage()));
        }
        return null;
    }

    @Override
    protected void onActivityResult ( int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            Uri selectedFileUri = data.getData();
            executeFileUpload(selectedFileUri);
        }
    }

    private void executeFileUpload(Uri selectedFileUri) {
        Executor executor = Executors.newSingleThreadExecutor();
        runOnUiThread(() -> {
            progressBar.setIndeterminate(true);
            progressBar.setVisibility(View.VISIBLE);
        });

        executor.execute(() -> {
            try {
                String keysDirectory = getApplicationContext().getFilesDir().getPath();
                String privateKeyPathAndroid = keysDirectory + "/ssh4android";
                String localFileLocation = getFilePathFromUri(selectedFileUri);

                if (localFileLocation == null) {
                    return;
                }

                String remoteFileName = new File(localFileLocation).getName();
                remoteFileDestination = "/home/" + username + "/Downloads/" + remoteFileName;

                JSch jsch = new JSch();
                Session transferSession = jsch.getSession(username, serverAddress, 22);
                transferSession.setConfig("StrictHostKeyChecking", "no");
                transferSession.setConfig("PreferredAuthentications", "publickey,password");
                jsch.addIdentity(privateKeyPathAndroid);
                transferSession.setPassword(password);
                transferSession.connect();

                Channel channel = transferSession.openChannel("sftp");
                if (channel != null) {
                    channel.connect();
                    ChannelSftp sftpChannel = (ChannelSftp) channel;

                    SftpProgressMonitor progressMonitor = new SftpProgressMonitor() {
                        private long max;
                        private long transferred;

                        @Override
                        public void init(int op, String src, String dest, long max) {
                            // Initialization logic here
                            transferred = 0;
                            this.max = max;
                            runOnUiThread(() -> progressBar.setIndeterminate(false));
                        }

                        @Override
                        public boolean count(long count) {
                            try {
                                transferred += count;

                                if (max > 0) {
                                    int progress = (int) ((transferred * 100) / max);
                                    int finalProgress = Math.min(progress, 100);

                                    if (finalProgress != lastProgress.get()) {
                                        lastProgress.set(finalProgress);
                                        runOnUiThread(() -> progressBar.setProgress(finalProgress));
                                    }
                                }
                            } catch (Exception e) {
                                Log.w("SSH4Android", e.getMessage(), e);
                                return false;
                            }
                            return true;
                        }

                        @Override
                        public void end() {
                            // Cleanup logic here, if needed
                        }
                    };

                        // Progress monitoring implementation
                    try {
                        SftpATTRS attrs = sftpChannel.stat(remoteFileDestination);
                        if (attrs != null) {
                            runOnUiThread(() -> showFileOverwriteConfirmationDialog(localFileLocation, sftpChannel, transferSession, progressMonitor));
                            return;
                        }
                    } catch (SftpException ignored) {
                        // File doesn't exist, proceed with upload
                    }

                    sftpChannel.put(localFileLocation, remoteFileDestination, progressMonitor);
                    sftpChannel.exit();
                    transferSession.disconnect();
                    runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                    showChooseDialog();
                }
            } catch (JSchException | SftpException e) {
                Log.w("SSH4Android", e.getMessage(), e);
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        });
    }

    // Add this method to show the file overwrite confirmation dialog using the custom alert dialog design
    private void showFileOverwriteConfirmationDialog(String localFilePath, ChannelSftp sftpChannel, Session transferSession, SftpProgressMonitor progressMonitor) {
        // Inflate the custom dialog layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_host_key, null);

        // Find UI elements in the inflated layout
        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        Button acceptButton = dialogView.findViewById(R.id.button_accept);
        Button denyButton = dialogView.findViewById(R.id.button_deny);

        // Set content and behavior for the dialog elements
        titleTextView.setText(R.string.file_exists);
        messageTextView.setText(R.string.a_file_with_the_same_name_already_exists_do_you_want_to_overwrite_it);

        acceptButton.setText(R.string.overwrite7);
        acceptButton.setOnClickListener(view -> {
            // User chose to overwrite, proceed with upload
            alertDialog.dismiss(); // Dismiss the dialog
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(() -> {
                try {
                    sftpChannel.put(localFilePath, remoteFileDestination, progressMonitor);
                } catch (SftpException e) {
                    Log.w("SSH4Android", e.getMessage(), e);
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "SftpException: " + e.getMessage()));
                }
                sftpChannel.exit();
                transferSession.disconnect();
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                showChooseDialog();
            });
        });

        denyButton.setText(R.string.cancel4);
        denyButton.setOnClickListener(view -> {
            // User chose to cancel, don't upload the file
            alertDialog.dismiss(); // Dismiss the dialog
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "File upload canceled."));
            showChooseDialog(); // Show the choose dialog again
        });

        // Create and show the AlertDialog with the custom layout
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity3.this);
        builder.setView(dialogView);
        runOnUiThread(() -> alertDialog = builder.create());
        runOnUiThread(() -> alertDialog.show());
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

        runOnUiThread(() ->  alertDialog = builder.create());
        runOnUiThread(() ->  alertDialog.show());
    }
}