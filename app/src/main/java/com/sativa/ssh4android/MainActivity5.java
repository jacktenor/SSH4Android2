package com.sativa.ssh4android;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
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
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
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
    private String keysDirectory;
    private String privateKeyPathAndroid;
    private AlertDialog alertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main5);

        keysDirectory = getApplicationContext().getFilesDir().getPath();
        privateKeyPathAndroid = keysDirectory + "/ssh4android";

        button6 = findViewById(R.id.button6);
        button = findViewById(R.id.button);
        cancelButton = findViewById(R.id.cancelButton); // Initialize the cancel button
        inputAutoComplete = findViewById(R.id.inputAutoComplete);
        enterButton = findViewById(R.id.enterButton);
        savePasswordCheckbox = findViewById(R.id.savePasswordCheckbox);
        outputTextView = findViewById(R.id.outputTextView);
        textView2 = findViewById(R.id.textView2);
        outputScrollView = findViewById(R.id.outputScrollView);

        checkAndRequestPermission();

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

        questions = new ArrayList<>();
        questions.add("SSH server address?");
        questions.add("Username?");
        questions.add("Password?");
        questions.add("Port?");
        questions.add("Command?");
        currentQuestionIndex = 0;
        setNextQuestion();

        enterButton.setOnClickListener(view -> {
            try {
                handleInput();
            } catch (IOException e) {
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException:\n" + e.getMessage()));
            }
        });
        final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
        enterButton.startAnimation(myAnim);
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

        // Set up AutoCompleteTextView with input history for non-password inputs
        if (currentQuestionIndex != 2) { // Do not set adapter for password input
            Set<String> inputHistory = loadInputHistory();
            ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(inputHistory));
            inputAutoComplete.setAdapter(autoCompleteAdapter);
        } else {
            inputAutoComplete.setAdapter(null);

            // Retrieve and autofill saved password if applicable
            Credential savedCredentials = Credential.getSavedCredentials(getApplicationContext());
            if (savedCredentials != null
                    && savedCredentials.serverAddress().equals(serverAddress)
                    && savedCredentials.username().equals(username)) {
                String savedPassword = getPassword(savedCredentials.serverAddress(), savedCredentials.username());
                if (savedPassword != null) {
                    inputAutoComplete.setText(savedPassword);
                }
            }
        }
        currentQuestionIndex++;
    }

    private void updateInputHistory(String newInput) {
        inputHistory.add(newInput);
        saveInputHistory(new ArrayList<>(inputHistory));
    }

    private void handleInput() throws IOException {
        String input = inputAutoComplete.getText().toString();

        // Only update input history for non-password inputs
        if (currentQuestionIndex != 3) {
            updateInputHistory(input);
        }

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
                password = input;
                if (savePasswordCheckbox.isChecked()) {
                    savePassword();
                    Credential credential = new Credential(serverAddress, username, password);
                    credential.saveCredentials(getApplicationContext());
                }
                savePasswordCheckbox.setVisibility(View.GONE);
                inputAutoComplete.setText("");
                inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);
                break;
            case 3:
                port = input.isEmpty() ? "22" : input;
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

            connectAndExecuteCommand();
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

    private void connectAndExecuteCommand() {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {

            Session session = null;
            String hostKey = null;

            try {
                JSch jsch = new JSch();
                File privateKeyFile = new File(privateKeyPathAndroid);

                // Check if the private key file exists
                if (privateKeyFile.exists()) {
                    jsch.addIdentity(privateKeyPathAndroid);
                }

                session = jsch.getSession(username, serverAddress, Integer.parseInt(port));
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
        Button addKeyButton = dialogView.findViewById(R.id.addKeyButton);

        // Set content and behavior for the dialog elements
        titleTextView.setText(R.string.host_key_verification6);
        messageTextView.setText(String.format("%s%s%s", getString(R.string.host_key_fingerprint7), hostKey, getString(R.string.do_you_want_to_accept_it)));

        // Set click listeners for buttons
        acceptButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
            acceptButton.startAnimation(myAnim);
            // Handle host key acceptance
            // You can continue with the remote file transfer here
            alertDialog.dismiss(); // Dismiss the dialog
            connectAndExecuteCommand2();
        });

        denyButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
            denyButton.startAnimation(myAnim);
            // Handle host key denial
            // Show a message or take appropriate action
            alertDialog.dismiss(); // Dismiss the dialog
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Host key denied."));
        });

        // Set click listeners for buttons
        addKeyButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
            addKeyButton.startAnimation(myAnim);
            // Handle host key acceptance
            // You can continue with the remote file transfer here
            alertDialog.dismiss(); // Dismiss the dialog
            performSSHOperations();
        });

        // Create and show the AlertDialog with the custom layout
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity5.this);
        builder.setView(dialogView);
        alertDialog = builder.create();
        alertDialog.show();
    }

    private void performSSHOperations() {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {

            String publicKeyPathAndroid = keysDirectory + "/ssh4android.pub";
            String publicKeyPathServer = "/home/" + username + "/.ssh/authorized_keys";

            try {
                JSch jsch = new JSch();

                final Path path = Paths.get(privateKeyPathAndroid);
                if (!Files.exists(path)) {
                    KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA);
                    keyPair.writePrivateKey(privateKeyPathAndroid);
                    Log.i("SSH", "Generating private key... : " + privateKeyPathAndroid);
                    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

                    byte[] publicKeyBytes = keyPair.getPublicKeyBlob();
                    String publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes);

                    try (FileWriter writer = new FileWriter(publicKeyPathAndroid)) {
                        writer.write("ssh-rsa " + publicKeyString + " " + username);
                    } catch (IOException e) {
                        Log.w("SSH4Android", e.getMessage(), e);
                        runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException:\n" + e.getMessage()));
                    }
                }

                File privateKeyFile = new File(privateKeyPathAndroid);

                // Check if the private key file exists
                if (privateKeyFile.exists()) {
                    jsch.addIdentity(privateKeyPathAndroid);
                }
                Session session = jsch.getSession(username, serverAddress, Integer.parseInt(port));
                session.setConfig("StrictHostKeyChecking", "no");
                session.setConfig("PreferredAuthentications", "publickey,password");
                session.setPassword(password);
                try {
                    session.connect();
                    uploadPublicKey(session, publicKeyPathAndroid, publicKeyPathServer);
                } catch (JSchException keyAuthException) {
                    Log.w("SSH4Android", keyAuthException.getMessage(), keyAuthException);
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "JSchException:\n" + keyAuthException.getMessage()));
                }
                connectAndExecuteCommand2();

                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (JSchException | IOException e) {
                Log.w("SSH4Android", e.getMessage(), e);
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "JSchException | IOException:\n" + e.getMessage()));
            }
        });
    }

    private void uploadPublicKey(Session session, String publicKeyPathAndroid, String publicKeyPathServer)
            throws JSchException, IOException {

        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.connect();

        final Path path = Paths.get(publicKeyPathAndroid);
        try (InputStream publicKeyStream = Files.newInputStream(path)) {
            String existingKeysContent = readExistingKeys(session, publicKeyPathServer);

            if (publicKeyStream != null && !existingKeysContent.contains(new String(Files.readAllBytes(path)))) {
                String newKeyContent = "\n" + new String(Files.readAllBytes(path));
                String updatedKeysContent = existingKeysContent + newKeyContent;

                try (InputStream updatedKeysStream = new ByteArrayInputStream(updatedKeysContent.getBytes())) {
                    channelSftp.put(updatedKeysStream, publicKeyPathServer);
                    runOnUiThread(() -> GreenCustomToast.showCustomToast(getApplicationContext(), "Key added to authorized_keys"));
                } catch (IOException | SftpException e) {
                    Log.w("SSH4Android", e.getMessage(), e);
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException | SftpException:\n" + e.getMessage()));
                }
            } else {
                runOnUiThread(() -> GreenCustomToast.showCustomToast(getApplicationContext(), "Key already exists in authorized_keys"));
            }
        } catch (IOException e) {
            Log.w("SSH4Android", e.getMessage(), e);
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException:\n" + e.getMessage()));
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
        Executor executor = Executors.newSingleThreadExecutor();
        runOnUiThread(() -> {
            outputScrollView.setVisibility(View.VISIBLE);
            outputTextView.setVisibility(View.VISIBLE);
            outputTextView.setText("");
            cancelButton.setVisibility(View.VISIBLE); // Show the cancel button
            button.setVisibility(View.VISIBLE);
            textView2.setVisibility(View.VISIBLE);
            button6.setVisibility(View.GONE);
        });

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
                File privateKeyFile = new File(privateKeyPathAndroid);

                // Check if the private key file exists
                if (privateKeyFile.exists()) {
                    jsch.addIdentity(privateKeyPathAndroid);
                }
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

                // Set the "Again?" button click listener to reset the UI and clear the output
                button6.setOnClickListener(view -> {
                    outputTextView.setText(""); // Clear the output
                    resetUI();
                });

                // Hide the cancel button
                // Update UI after command finishes
                runOnUiThread(() -> {
                    cancelButton.setVisibility(View.GONE);
                    button6.setVisibility(View.VISIBLE);
                });

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
        enterButton.setVisibility(View.VISIBLE);
        inputAutoComplete.setVisibility(View.VISIBLE);
        textView2.setVisibility(View.VISIBLE);
        button6.setVisibility(View.VISIBLE);

        inputAutoComplete.requestFocus();
        command = "";

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
        cancelButton.setVisibility(View.GONE); // Hide the cancel button

        // Ensure the UI is reset on the main thread
        runOnUiThread(this::resetUI);
    }

    private void updateOutput(String chunk) {
        outputTextView.append(chunk);
        outputScrollView.post(() -> outputScrollView.fullScroll(View.FOCUS_DOWN));
    }

    public record Credential(String serverAddress, String username, String password) {

        public void saveCredentials(Context context) {
                SharedPreferences sharedPreferences = context.getSharedPreferences("SavedCredentials", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("serverAddress", serverAddress);
                editor.putString("username", username);
                editor.putString("password", password);
                editor.apply();
            }

            public static Credential getSavedCredentials(Context context) {
                SharedPreferences sharedPreferences = context.getSharedPreferences("SavedCredentials", MODE_PRIVATE);
                String serverAddress = sharedPreferences.getString("serverAddress", null);
                String username = sharedPreferences.getString("username", null);
                String password = sharedPreferences.getString("password", null);

                if (serverAddress != null && username != null && password != null) {
                    return new Credential(serverAddress, username, password);
                }
                return null;
            }
        }
}
