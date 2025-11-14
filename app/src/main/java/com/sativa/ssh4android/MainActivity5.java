package com.sativa.ssh4android;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import jackpal.androidterm.emulatorview.EmulatorView;
import jackpal.androidterm.emulatorview.TermSession;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
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
import jackpal.androidterm.emulatorview.ColorScheme;

public class MainActivity5 extends AppCompatActivity {
    private AutoCompleteTextView inputAutoComplete;
    private Button enterButton;
    private Button button6;
    private Button button;
    private TextView textView2;
    private CheckBox savePasswordCheckbox;
    private ScrollView outputScrollView;
    private String serverAddress;
    private String username;
    private String password;
    private String port;
    private Set<String> inputHistory;
    private int currentQuestionIndex;
    private List<String> questions;
    private AlertDialog alertDialog;
    private static final String INPUT_HISTORY_KEY = "inputHistory";
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 1;
    private EmulatorView terminalView;
    private static ShellTermSession termSession;
    private volatile boolean imeVisible = false;
    private android.view.GestureDetector terminalGestureDetector;
    private Session probeSession; // For host key probing
    private String knownHostsPath;
    // NEW: opt-in flag + checkbox ref


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main5);

        button6 = findViewById(R.id.button6);
        button = findViewById(R.id.button);
        inputAutoComplete = findViewById(R.id.inputAutoComplete);
        enterButton = findViewById(R.id.enterButton);
        savePasswordCheckbox = findViewById(R.id.savePasswordCheckbox);
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
        currentQuestionIndex = 0;
        setNextQuestion();

        saveInputHistory(new ArrayList<>(inputHistory));

        enterButton.setOnClickListener(view -> {
            try {
                handleInput();
            } catch (IOException e) {
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException: " + e.getMessage()));
            }
        });
        final Animation myAnim = AnimationUtils.loadAnimation(this, R.anim.bounce);
        enterButton.startAnimation(myAnim);

        knownHostsPath = getFilesDir().getPath() + "/known_hosts";

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

    // REPLACE your current handleInput() with this version
    private void handleInput() throws IOException {
        String input = inputAutoComplete.getText().toString();

        // Keep your input history behavior
        updateInputHistory(input);
        Set<String> inputHistory = loadInputHistory();
        inputHistory.add(input);
        saveInputHistory(new ArrayList<>(inputHistory));

        switch (currentQuestionIndex - 1) {
            case 0: { // Server / Host
                serverAddress = input;
                break;
            }

            case 1: { // Username
                username = input;

                // Show only the "Save password" checkbox for the next step
                savePasswordCheckbox.setVisibility(View.VISIBLE);

                // Switch input field to password mode for the next prompt
                inputAutoComplete.setInputType(
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                );
                break;
            }

            case 2: { // Password
                password = input;

                // Persist password if user asked
                if (savePasswordCheckbox.isChecked()) {
                    savePassword();
                }

                // Hide the checkbox and reset the input field to normal text for the next question
                savePasswordCheckbox.setVisibility(View.GONE);
                inputAutoComplete.setText("");
                inputAutoComplete.setInputType(InputType.TYPE_CLASS_TEXT);
                break;
            }

            case 3: { // Port
                port = input.isEmpty() ? "22" : input;

                // Hide keyboard once we have the last answer
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(inputAutoComplete.getWindowToken(), 0);
                }
                break;
            }
        }

        // Advance or connect
        if (currentQuestionIndex < questions.size()) {
            setNextQuestion();
        } else {
            // Finished the Q&A: hide input UI and kick off the connection (which will show your custom dialog every time)
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

    // Probe the server EVERY time to read the current host key fingerprint,
// then show your custom dialog. Never "fail login" on this probe.
    private void connectAndExecuteCommand() {
        Executor executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            String hostKeyFingerprint = null;

            JSch jsch = new JSch();
            try {
                // Honor known_hosts (we still record keys there)
                File knownHostsFile = new File(knownHostsPath);
                if (knownHostsFile.exists()) {
                    jsch.setKnownHosts(knownHostsPath);
                }

                // Build a probe session. IMPORTANT: do NOT set a password here.
                // We only need the key-exchange to complete so we can read the host key.
                int p = 22;
                try { p = Integer.parseInt(port == null || port.isEmpty() ? "22" : port); } catch (Exception ignored) {}

                probeSession = jsch.getSession(username, serverAddress, p);

                // Avoid real auth attempts; we want just the handshake + hostkey.
                probeSession.setConfig(getString(R.string.preferredauthentications), getString(R.string.publickey_password)); // harmless, but no password provided
                probeSession.setConfig(getString(R.string.pubkeyacceptedalgorithms), getString(R.string.rsa_sha2_256_rsa_sha2_512_ssh_ed25519_ssh_rsa));
                probeSession.setConfig(getString(R.string.hostkeyalgorithms), getString(R.string.ssh_ed25519_rsa_sha2_512_rsa_sha2_256_ssh_rsa));

                // Make sure the probe never blocks on host key verification
                probeSession.setConfig(getString(R.string.stricthostkeychecking), "no");

                String probeError = null;
                try {
                    // This may throw "Auth fail" on servers that require an auth immediately.
                    // That's fine—we'll still try to read the host key below.
                    probeSession.connect(15000);
                } catch (Exception e) {
                    // Swallow auth failures for the probe; they are expected with empty passwords.
                    String msg = (e.getMessage() == null) ? "" : e.getMessage().toLowerCase();
                    boolean authFail = msg.contains("auth fail") || msg.contains("userauth fail");
                    if (!authFail) {
                        // Non-auth errors (network, DNS, cipher mismatch, etc.) are real probe errors.
                        probeError = e.getMessage();
                    }
                } finally {
                    // CRITICAL: even if connect() threw an exception, JSch has the host key from KEX.
                    try {
                        if (probeSession != null && probeSession.getHostKey() != null) {
                            hostKeyFingerprint = probeSession.getHostKey().getFingerPrint(jsch);
                        }
                    } catch (Exception ignored) {}

                    try { if (probeSession != null && probeSession.isConnected()) probeSession.disconnect(); } catch (Exception ignored) {}
                }

                // Only surface a toast if we have no fingerprint AND there was a real (non-auth) failure
                final String fpFinal = hostKeyFingerprint;
                final String realError = probeError;
                runOnUiThread(() -> {
                    if (fpFinal == null && realError != null && !realError.trim().isEmpty()) {
                        CustomToast.showCustomToast(getApplicationContext(), "Probe error: " + realError);
                    }
                    // Show your dialog EVERY time; pass the fingerprint (may be null if a hard error)
                    showHostKeyDialog(fpFinal);
                });

            } catch (Exception outer) {
                // Extremely defensive: unexpected failures
                final String msg = outer.getMessage();
                runOnUiThread(() -> {
                    CustomToast.showCustomToast(getApplicationContext(), (msg == null) ? "Probe error." : msg);
                    showHostKeyDialog((String) null);
                });
            }
        });
    }

    private void showHostKeyDialog(@Nullable String hostKey) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_host_key, null);

        TextView titleTextView = dialogView.findViewById(R.id.dialog_title);
        TextView messageTextView = dialogView.findViewById(R.id.dialog_message);
        Button acceptButton = dialogView.findViewById(R.id.button_accept);
        Button denyButton = dialogView.findViewById(R.id.button_deny);
        Button addKeyButton = dialogView.findViewById(R.id.addKeyButton);

        titleTextView.setText(R.string.host_key_verification6);

        String shown = (hostKey == null || hostKey.trim().isEmpty())
                ? getString(R.string.unknown_host_key)  // fallback label in your strings.xml
                : hostKey;

        messageTextView.setText(
                String.format("%s%s%s",
                        getString(R.string.host_key_fingerprint7),
                        shown,
                        getString(R.string.do_you_want_to_accept_it))
        );

        acceptButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
            acceptButton.startAnimation(myAnim);

            // Save and proceed
            saveHostKey(probeSession);
            try { if (probeSession != null && probeSession.isConnected()) probeSession.disconnect(); } catch (Exception ignored) {}
            alertDialog.dismiss();

            ui(this::connectAndExecuteCommand2);
        });

        denyButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
            denyButton.startAnimation(myAnim);

            try { if (probeSession != null && probeSession.isConnected()) probeSession.disconnect(); } catch (Exception ignored) {}
            alertDialog.dismiss();
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Host key denied."));
        });

        addKeyButton.setOnClickListener(view -> {
            final Animation myAnim = AnimationUtils.loadAnimation(MainActivity5.this, R.anim.bounce);
            addKeyButton.startAnimation(myAnim);

            // Save host key then do key-gen/upload flow; that flow will connect after upload
            saveHostKey(probeSession);
            try { if (probeSession != null && probeSession.isConnected()) probeSession.disconnect(); } catch (Exception ignored) {}
            alertDialog.dismiss();

            performSSHOperations();
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity5.this);
        builder.setView(dialogView);
        alertDialog = builder.create();
        alertDialog.show();
    }

    private void saveHostKey(Session session) {
        if (session == null || session.getHostKey() == null) return;
        try {
            FileWriter writer = new FileWriter(knownHostsPath, true); // Append if exists
            String entry = serverAddress + " " + session.getHostKey().getType() + " " + session.getHostKey().getKey() + "\n";
            writer.write(entry);
            writer.close();
            Files.setPosixFilePermissions(Paths.get(knownHostsPath), PosixFilePermissions.fromString("rw-------"));
            Log.i("SSH", "Saved host key to " + knownHostsPath);
        } catch (IOException e) {
            Log.w("SSH", "Failed to save host key: " + e.getMessage());
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "Failed to save host key: " + e.getMessage()));
        }
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

                File knownHostsFile = new File(knownHostsPath);
                if (knownHostsFile.exists()) {
                    jsch.setKnownHosts(knownHostsPath);
                }

                final Path path = Paths.get(privateKeyPathAndroid);
                if (!Files.exists(path)) {
                    KeyPair keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
                    keyPair.writePrivateKey(privateKeyPathAndroid);
                    Log.i("SSH", "Generating private key... : " + privateKeyPathAndroid);
                    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

                    byte[] publicKeyBytes = keyPair.getPublicKeyBlob();
                    String publicKeyString = Base64.getEncoder().encodeToString(publicKeyBytes);

                    try (FileWriter writer = new FileWriter(publicKeyPathAndroid)) {
                        writer.write("ssh-rsa " + publicKeyString + " " + username);
                    } catch (IOException e) {
                        Log.w("SSH4Android", e.getMessage(), e);
                        runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException: " + e.getMessage()));
                    }
                }

                Session session = jsch.getSession(username, serverAddress, Integer.parseInt(port));
                session.setConfig("StrictHostKeyChecking", "yes"); // Now yes, since known_hosts saved
                session.setConfig("PreferredAuthentications", "publickey,password");
                session.setConfig("PubkeyAcceptedAlgorithms", "rsa-sha2-256,rsa-sha2-512,ssh-ed25519,ssh-rsa");
                session.setConfig("HostKeyAlgorithms", "ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ssh-rsa");
                jsch.addIdentity(privateKeyPathAndroid);  // Add key BEFORE connect
                session.setPassword(password);
                if (password == null || password.isEmpty()) {
                    session.setConfig("PreferredAuthentications", "publickey");
                }
                try {
                    session.connect();
                    uploadPublicKey(session, publicKeyPathAndroid, publicKeyPathServer);
                } catch (JSchException keyAuthException) {
                    Log.w("SSH4Android", keyAuthException.getMessage(), keyAuthException);
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "JSchException: " + keyAuthException.getMessage()));
                }

                // Only proceed if connection succeeded
                if (session.isConnected()) {
                    ui(this::connectAndExecuteCommand2);
                    session.disconnect();
                }
            } catch (JSchException | IOException e) {
                Log.w("SSH4Android", e.getMessage(), e);
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "JSchException | IOException: " + e.getMessage()));
            }
        });
    }

    private void ui(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else runOnUiThread(r);
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
                    runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "IOException | SftpException: " + e.getMessage()));
                }
            } else {
                runOnUiThread(() -> GreenCustomToast.showCustomToast(getApplicationContext(), "Key already exists in authorized_keys"));
            }
        } catch (IOException e) {
            Log.w("SSH4Android", e.getMessage(), e);
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

    // --- MainActivity5.java — REPLACE THIS METHOD ---
    // --- MainActivity5.java — REPLACE THIS METHOD ---
    private void connectAndExecuteCommand2() {
        final String h = serverAddress;
        final String u = username;
        final String p = password; // may be empty
        final int portInt;
        try {
            portInt = Integer.parseInt(port == null || port.isEmpty() ? "22" : port);
        } catch (NumberFormatException ignored) {
            CustomToast.showCustomToast(getApplicationContext(), "Invalid port");
            return;
        }

        final String privateKey = new java.io.File(getFilesDir(), "ssh4android").getAbsolutePath();

        // No branching here: the "Install key" action lives in your dialog's Add button.
        ui(() -> startTerminal(h, portInt, u, p, privateKey));
    }

    // NEW: installs a key using password auth (if requested), then starts the terminal
    private void installKeyThenStartTerminal(String host, int port, String user, String pwd, String privateKeyPath) {
        // If no password, we cannot push the key via SFTP. Fall back gracefully.
        if (pwd == null || pwd.isEmpty()) {
            runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(),
                    "Cannot install key without a password. Opening terminal..."));
            startTerminal(host, port, user, pwd, privateKeyPath);
            return;
        }

        new Thread(() -> {
            Session session = null;
            try {
                // Ensure local keypair exists: <files>/ssh4android (+ .pub)
                String keysDir = getFilesDir().getPath();
                String priv = keysDir + "/ssh4android";
                String pub  = keysDir + "/ssh4android.pub";
                File privF = new File(priv);
                File pubF  = new File(pub);

                JSch jsch = new JSch();

                // Generate RSA 2048 keypair if missing (same as your performSSHOperations)
                if (!privF.exists() || !pubF.exists()) {
                    KeyPair kp = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048);
                    kp.writePrivateKey(priv);
                    Files.setPosixFilePermissions(Paths.get(priv), PosixFilePermissions.fromString("rw-------"));
                    byte[] pubBlob = kp.getPublicKeyBlob();
                    String pubStr = Base64.getEncoder().encodeToString(pubBlob);
                    try (FileWriter w = new FileWriter(pub)) {
                        w.write("ssh-rsa " + pubStr + " " + user);
                    }
                    kp.dispose();
                }

                // known_hosts (saved earlier by your probe+accept flow)
                File knownHostsFile = new File(knownHostsPath);
                if (knownHostsFile.exists()) {
                    jsch.setKnownHosts(knownHostsPath);
                }

                // Create a session that will certainly work with PASSWORD (key not authorized yet)
                session = jsch.getSession(user, host, port);
                session.setConfig("StrictHostKeyChecking", "yes");
                session.setConfig("PreferredAuthentications", "password,publickey");
                session.setConfig("PubkeyAcceptedAlgorithms", "rsa-sha2-256,rsa-sha2-512,ssh-ed25519,ssh-rsa");
                session.setConfig("HostKeyAlgorithms", "ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ssh-rsa");
                session.setPassword(pwd);

                session.connect(20000);

                // Push public key to ~/.ssh/authorized_keys (idempotent)
                String publicKeyPathServer  = "/home/" + user + "/.ssh/authorized_keys";
                uploadPublicKey(session, pub, publicKeyPathServer);

                // Flip the flag so we don't loop again

                runOnUiThread(() -> {
                    GreenCustomToast.showCustomToast(getApplicationContext(), "SSH key installed. Using it next time.");
                    startTerminal(host, port, user, pwd, privateKeyPath);
                });
            } catch (Exception e) {
                runOnUiThread(() ->
                        CustomToast.showCustomToast(getApplicationContext(), "Key install failed: " + e.getMessage()));
                // Still give them a terminal session
                runOnUiThread(() -> startTerminal(host, port, user, pwd, privateKeyPath));
            } finally {
                try { if (session != null && session.isConnected()) session.disconnect(); } catch (Exception ignored) {}
            }
        }, "ssh-install-key").start();
    }

    private void startTerminal(String host, int port, String user, String password, String privateKeyPath) {
        // Build a lightweight screen that won’t fight your existing layouts
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setId(View.generateViewId());
        root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // Keep your panther background (or set any drawable you use)
        root.setBackgroundResource(R.drawable.panther);

        // NEW: Disable clipping to allow translated views to draw outside bounds (for smooth pan-up)
        root.setClipChildren(false);
        root.setClipToPadding(false);

        terminalView = new EmulatorView(this, null);
        terminalView.setId(View.generateViewId());
        terminalView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        terminalView.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        root.addView(terminalView);
        // Swap the whole content to the terminal screen
        setContentView(root);
        attachKeyboardGesturesToTerminal();
        // Change to ADJUST_NOTHING to prevent window resizing/panning (keeps background fixed)
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        // Track IME visibility and height; manually translate the terminal up to avoid overlay without resizing
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            int kbHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) terminalView.getLayoutParams();
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.TOP;
            lp.bottomMargin = imeVisible ? kbHeight : 0;
            terminalView.setLayoutParams(lp);

            // Give the emulator some breathing room so the prompt never hides under the IME
            terminalView.setPadding(
                    terminalView.getPaddingLeft(),
                    terminalView.getPaddingTop(),
                    terminalView.getPaddingRight(),
                    imeVisible ? kbHeight : 0
            );

            terminalView.post(() -> {
                terminalView.updateSize(true);
                Log.d("Terminal", "Keyboard visible: " + imeVisible + ", kbHeight: " + kbHeight + ", viewHeight: " + terminalView.getHeight());
            });

            return insets;
        });

        if (Looper.myLooper() != Looper.getMainLooper()) {
            ui(() -> startTerminal(host, port, user, password, privateKeyPath));
            return;
        }

        // Connect on background thread, attach on UI
        termSession = new ShellTermSession();
        new Thread(() -> {
            try {
                termSession.connectAndStartShell(host, port, user, password, privateKeyPath, this);
                runOnUiThread(() -> {
                    terminalView.setDensity(getResources().getDisplayMetrics()); // set BEFORE attach
                    terminalView.attachSession(termSession);
                    terminalView.setFocusable(true);
                    terminalView.setFocusableInTouchMode(true);
                    terminalView.requestFocus();

                    // Set transparent background with bright white text for vividness/contrast
                    // This allows R.drawable.panther to show through the emulator screen
                    ColorScheme transparentScheme = new ColorScheme(
                            android.graphics.Color.WHITE,  // Foreground: Bright white for max visibility on dark image
                            0x00000000                     // Background: Fully transparent to reveal panther.jpeg
                    );
                    terminalView.setColorScheme(transparentScheme);

                    // show IME once attached
                    showKeyboardNow();
                });
            } catch (Exception e) {
                runOnUiThread(() -> CustomToast.showCustomToast(getApplicationContext(), "SSH error: " + e.getMessage()));

            }
        }, "ssh-connect").start();
    }

    // Find R.id.terminalView if present; otherwise create one and add to the root.
    private void ensureTerminalView() {
        if (terminalView == null) {
            // Try to find from layout first
            try {
                terminalView = findViewById(R.id.terminalView);
            } catch (Throwable ignored) {}

            if (terminalView == null) {
                // Create programmatically and attach to the activity root
                terminalView = new EmulatorView(this, null);
                terminalView.setId(View.generateViewId());

                View content = findViewById(android.R.id.content);
                ViewGroup root = (content instanceof ViewGroup)
                        ? (ViewGroup) content
                        : getWindow().getDecorView() instanceof ViewGroup
                        ? (ViewGroup) getWindow().getDecorView()
                        : null;

                if (root != null) {
                    ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );
                    root.addView(terminalView, lp);
                }
            }
        }

        terminalView.setKeepScreenOn(true);
        terminalView.setFocusable(true);
        terminalView.setFocusableInTouchMode(true);
        terminalView.requestFocus();
    }

    // Hide the prompt UI so the terminal can take over the screen
    private void hideQuestionnaireUIForTerminal() {
        try { inputAutoComplete.setVisibility(View.GONE); } catch (Exception ignored) {}
        try { enterButton.setVisibility(View.GONE); } catch (Exception ignored) {}
        try { savePasswordCheckbox.setVisibility(View.GONE); } catch (Exception ignored) {}
        try { outputScrollView.setVisibility(View.GONE); } catch (Exception ignored) {}
        try { textView2.setVisibility(View.GONE); } catch (Exception ignored) {}
        try { button.setVisibility(View.GONE); } catch (Exception ignored) {}
        try { button6.setVisibility(View.GONE); } catch (Exception ignored) {}
    }

    private void attachKeyboardGesturesToTerminal() {
        if (terminalView == null) return;

        // Build once, reuse (prevents null in listener)
        if (terminalGestureDetector == null) {
            final InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

            terminalGestureDetector = new android.view.GestureDetector(
                    this,
                    new android.view.GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(@NonNull MotionEvent e) {
                            // Return true so we always receive UP even after tiny movement
                            return true;
                        }

                        @Override
                        public boolean onSingleTapUp(@NonNull MotionEvent e) {
                            showKeyboardNow();
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(@NonNull MotionEvent e) {
                            if (imeVisible) {
                                View f = getCurrentFocus();
                                if (imm != null && f != null) {
                                    imm.hideSoftInputFromWindow(f.getWindowToken(), 0);
                                }
                            } else {
                                showKeyboardNow();
                            }
                            return true;
                        }
                    });
        }

        // Always set the listener; guard against a null detector at runtime
        terminalView.setOnTouchListener((v, ev) -> {
            android.view.GestureDetector gd = terminalGestureDetector;
            if (gd != null) {
                return gd.onTouchEvent(ev);
            }
            // Fallback: if detector somehow null, at least handle a quick tap
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                showKeyboardNow();
                return true;
            }
            return false;
        });
    }

    // ADD this helper
    private void showKeyboardNow() {
        if (terminalView == null) return;

        terminalView.setFocusable(true);
        terminalView.setFocusableInTouchMode(true);
        terminalView.requestFocus();

        WindowInsetsControllerCompat c = ViewCompat.getWindowInsetsController(terminalView);
        if (c != null) c.show(WindowInsetsCompat.Type.ime());

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            boolean asked = imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
            if (!asked) imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }

        terminalView.postDelayed(() -> {
            if (!imeVisible) {
                InputMethodManager imm2 = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm2 != null) imm2.showSoftInput(terminalView, InputMethodManager.SHOW_FORCED);
            }
        }, 80);
    }

    // Minimal TermSession wrapper that reuses your proven JSch settings
    private static class ShellTermSession extends TermSession {
        private Session sshSession;
        private com.jcraft.jsch.ChannelShell shell;

        void connectAndStartShell(String host, int port, String user,
                                  @Nullable String password, @Nullable String privateKeyPath,
                                  Context ctx) throws Exception {
            com.jcraft.jsch.JSch jsch = new com.jcraft.jsch.JSch();

            // Normalize private key (accept dir/.pub)
            String pk = normalizePrivateKeyPath(privateKeyPath, ctx);
            boolean haveKey = (pk != null);
            boolean havePwd = (password != null && !password.isEmpty());

            if (haveKey) {
                jsch.addIdentity(pk);
            }

            String knownHostsPath = new File(ctx.getFilesDir(), "known_hosts").getAbsolutePath();
            File knownHostsFile = new File(knownHostsPath);
            if (knownHostsFile.exists()) {
                jsch.setKnownHosts(knownHostsPath);
            }

            com.jcraft.jsch.Session ssh = jsch.getSession(user, host, port);

            // Set strict checking yes now that known_hosts is saved
            ssh.setConfig("StrictHostKeyChecking", "yes");

            // Disable strict KEX by omitting extensions in KEX proposal
            ssh.setConfig("kex", "ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha256,diffie-hellman-group16-sha512,diffie-hellman-group18-sha512,diffie-hellman-group14-sha256");

            // Modern algos (unchanged)
            ssh.setConfig("PubkeyAcceptedAlgorithms",
                    "rsa-sha2-256,rsa-sha2-512,ssh-ed25519,ssh-rsa"); // auth sig algos
            ssh.setConfig("HostKeyAlgorithms",
                    "ssh-ed25519,rsa-sha2-512,rsa-sha2-256,ssh-rsa"); // server host key algos

            if (havePwd) ssh.setPassword(password);

            String preferred;
            if (haveKey && havePwd) preferred = "publickey,password,keyboard-interactive";
            else if (haveKey)       preferred = "publickey,keyboard-interactive";
            else if (havePwd)       preferred = "password,keyboard-interactive";
            else throw new IllegalStateException("No credentials (need key or password).");

            ssh.setConfig("PreferredAuthentications", preferred);

            // Debug logging (keep for now)
            com.jcraft.jsch.JSch.setLogger(new com.jcraft.jsch.Logger() {
                @Override public boolean isEnabled(int level) { return true; }
                @Override public void log(int level, String msg) { android.util.Log.d("JSch", msg); }
            });
            android.util.Log.d("SSH", "PreferredAuth=" + preferred +
                    " haveKey=" + haveKey + " keyPath=" + pk + " havePwd=" + havePwd);

            ssh.connect(15000);

            com.jcraft.jsch.ChannelShell sh = (com.jcraft.jsch.ChannelShell) ssh.openChannel("shell");
            sh.setPtyType("xterm");

            java.io.InputStream remoteIn = sh.getInputStream();
            java.io.OutputStream remoteOut = sh.getOutputStream();
            setTermIn(remoteIn);
            setTermOut(remoteOut);

            sh.connect(5000);

            this.sshSession = ssh;
            this.shell = sh;
        }

        @Override
        public void close() {
            try {
                if (shell != null) shell.disconnect();
            } catch (Exception ignored) {
            }
            try {
                if (sshSession != null) sshSession.disconnect();
            } catch (Exception ignored) {
            }
        }

        // --- key resolver shared by session ---
        private static @Nullable String normalizePrivateKeyPath(@Nullable String path, android.content.Context ctx) {
            if (path == null || path.trim().isEmpty()) {
                String def = new java.io.File(ctx.getFilesDir(), "ssh4android").getAbsolutePath();
                java.io.File pk = new java.io.File(def);
                return (pk.isFile() && pk.length() > 0) ? def : null;
            }

            String cand = path.trim();
            java.io.File f = new java.io.File(cand);
            if (f.isDirectory()) f = new java.io.File(f, "ssh4android");
            String p = f.getAbsolutePath();
            if (p.endsWith(".pub")) p = p.substring(0, p.length() - 4);
            java.io.File pk = new java.io.File(p);
            return (pk.isFile() && pk.length() > 0) ? p : null;
        }
    }
}