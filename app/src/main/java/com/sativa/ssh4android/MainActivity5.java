package com.sativa.ssh4android;

import static java.lang.Thread.sleep;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity5 extends Activity {
    private AutoCompleteTextView inputAutoComplete;
    private Button enterButton;
    private List<String> questions;
    private int currentQuestionIndex;
    private static final String INPUT_HISTORY_KEY = "input_history";
    private String username;
    private String serverAddress;
    private String password;
    private String command;
    private AlertDialog alertDialog;
    private Set<String> inputHistory;
    static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private CheckBox savePasswordCheckbox;
    private View button;
    private TextView outputTextView;
    private Button button6;
    private TextView textView2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main5);

        button6 = findViewById(R.id.button6);
        button = findViewById(R.id.button);
        inputAutoComplete = findViewById(R.id.inputAutoComplete);
        enterButton = findViewById(R.id.enterButton);
        savePasswordCheckbox = findViewById(R.id.savePasswordCheckbox);
        outputTextView = findViewById(R.id.outputTextView);
        textView2 = findViewById(R.id.textView2);

        Executor executor = Executors.newSingleThreadExecutor();
        CompletableFuture.runAsync(this::performSSHOperations, executor);

        getWindow().setBackgroundDrawableResource(R.drawable.panther);

        inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);

        inputAutoComplete.requestFocus();
        getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        runOnUiThread(() -> button6.setVisibility(View.GONE));
        runOnUiThread(() -> button.setVisibility(View.GONE));
        runOnUiThread(() -> textView2.setVisibility(View.GONE));

        button.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
            button.startAnimation(myAnim);
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
        questions.add("Command?");

        currentQuestionIndex = 0;
        setNextQuestion();

        saveInputHistory(new ArrayList<>(inputHistory));

        enterButton.setOnClickListener(view -> handleInput());
        final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
        enterButton.startAnimation(myAnim);
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
                savePasswordCheckbox.setVisibility(View.GONE);
                inputAutoComplete.setText("");
                inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);
                break;
            case 3:
                command = input;
                // Hide the keyboard after the fourth question is answered
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(inputAutoComplete.getWindowToken(), 0);
                }
                break;
        }

        if (currentQuestionIndex < questions.size()) {
            // Set next question
            setNextQuestion();
        } else {
            // All questions answered, initiate connection and command execution

            inputAutoComplete.setText("");
            inputAutoComplete.setVisibility(View.GONE);
            enterButton.setVisibility(View.GONE);

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
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
            acceptButton.startAnimation(myAnim);
            // Handle host key acceptance
            // You can continue with the remote file transfer here
            alertDialog.dismiss(); // Dismiss the dialog
            performSSHOperations();
        });

        denyButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
            denyButton.startAnimation(myAnim);
            // Handle host key denial
            // Show a message or take appropriate action
            alertDialog.dismiss(); // Dismiss the dialog
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Host key denied."));
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
                }

                if (session.isConnected()) {
                    session.disconnect();
                }

            } catch (JSchException | IOException e) {
                e.printStackTrace();
            }
            connectAndExecuteCommand2();
        });
    }

    // Upload public key to the server's authorized_keys file
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
    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
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

        executor.execute(() -> {

            String keysDirectory = getApplicationContext().getFilesDir().getPath();
            String privateKeyPathAndroid = keysDirectory + "/ssh4android";

            WeakReference<MainActivity5> activityReference = new WeakReference<>(MainActivity5.this);
            StringBuilder output = new StringBuilder();
            boolean success = false;

            MainActivity5 activity = activityReference.get();
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

                        // Update the UI dynamically as output is received
                        runOnUiThread(() -> {
                            if (output.length() > 0) {
                                outputTextView.setVisibility(View.VISIBLE);
                                outputTextView.setText(output.toString());
                            }
                        });
                    }

                    if (channelExec.isClosed()) {
                        if (in.available() > 0) continue;
                        break;
                    }

                    // Introduce a delay to avoid excessive UI updates
                    sleep(500); // You can adjust the delay as needed
                }

                channelExec.disconnect();
                session.disconnect();

                runOnUiThread(() -> {
                    button6.setVisibility(View.VISIBLE);
                    button.setVisibility(View.VISIBLE);
                    textView2.setVisibility(View.VISIBLE);

                    button6.setOnClickListener(view -> {
                        final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
                        button6.startAnimation(myAnim);

                        // Clear previous output and prepare for a new command
                        output.setLength(0);
                        // Clear previous output in the UI
                        activity.runOnUiThread(() -> {
                            activity.outputTextView.setText("");
                            outputTextView.setText("");
                            activity.outputTextView.setVisibility(View.GONE);

                            // Optionally, reset other variables related to the previous command
                            command = "";
                            enterButton.setVisibility(View.VISIBLE);

                            // Show inputAutoComplete and request focus
                            inputAutoComplete.setVisibility(View.VISIBLE);
                            inputAutoComplete.requestFocus();

                            runOnUiThread(() -> textView2.setVisibility(View.GONE));
                            // Show the keyboard
                            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.showSoftInput(inputAutoComplete, InputMethodManager.SHOW_IMPLICIT);
                            }

                            // Dismiss the dialog if it's showing
                            if (alertDialog != null && alertDialog.isShowing()) {
                                alertDialog.dismiss();
                            }
                        });
                    });
                });

                success = true;
            } catch (JSchException | IOException | InterruptedException e) {
                e.printStackTrace();
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Exception: " + e.getMessage()));
            }


            boolean finalSuccess = success;
            runOnUiThread(() -> {
                MainActivity5 finalActivity = activityReference.get();
                if (finalActivity != null && !finalActivity.isFinishing()) {
                    if (!finalSuccess) {
                        runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), ""));
                    }
                }
            });
        });
    }
}