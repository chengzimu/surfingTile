package com.yadli.surfingtile;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class SurfingTileService extends TileService {

    private static final String TAG = "SurfingTileService";
    private static final String SCRIPT_PATH = "/data/adb/box_bll/scripts/box.service";
    private static final String DISABLE_FILE = "/data/adb/modules/Surfing/disable";

    @Override
    public void onStartListening() {
        super.onStartListening();
        // Update tile state when it becomes visible
        Tile tile = getQsTile();
        if (tile != null) {
            int state = getBoxState();
            tile.setState(state);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        Log.d(TAG, "onClick enter");
        int state = getBoxState();

        switch(state) {
            case Tile.STATE_ACTIVE:
                state = Tile.STATE_INACTIVE;
                break;
            case Tile.STATE_INACTIVE:
                state = Tile.STATE_ACTIVE;
                break;
            case Tile.STATE_UNAVAILABLE:
                // If service is unavailable, show error and continue anyway
                Log.e(TAG, "Cannot access box service. Check if your device is rooted.");
                state = Tile.STATE_ACTIVE;
                break;
        }

        boolean success = setBoxState(state);
        if (!success) {
            Log.e(TAG, "Failed to change box service state");
            return;
        }

        // Handle user click
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setState(state);
            tile.updateTile();

            // Show status message
            if (state == Tile.STATE_ACTIVE) {
                Log.d(TAG, "Box service started");
            } else {
                Log.d(TAG, "Box service stopped");
            }
        }
        Log.d(TAG, "onClick exit");
    }

    /*
    private int getBoxStateSU() {
        if (!isScriptAvailable()) {
            Log.e(TAG, "Box service script not found at: " + SCRIPT_PATH);
            return Tile.STATE_UNAVAILABLE;
        }

        try {
            // Create a process to execute the command with root permission
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", SCRIPT_PATH + " status"});

            // Read the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }

            String outputStr = output.toString();
            Log.d(TAG, "Status output: " + outputStr);

            // Wait for the process to complete
            int exitCode = process.waitFor();
            Log.d(TAG, "Status exit code: " + exitCode);

            // Check if service is running using regex
            if (exitCode == 0 && outputStr.contains("服务正在运行")) {
                return Tile.STATE_ACTIVE;
            } else if (exitCode == 1 && outputStr.contains("服务已停止")) {
                return Tile.STATE_INACTIVE;
            } else {
                Log.e(TAG, "Unexpected status response: " + outputStr);
                return Tile.STATE_UNAVAILABLE;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error checking box service status", e);
            return Tile.STATE_UNAVAILABLE;
        }
    }
    */

    private int getBoxState() {
        // Use HTTP request to check service status instead of shell commands
        // Note, do not use java URL connection -- it gets this app killed
        try {
            // Use curl to check the service status without su
            Process process = Runtime.getRuntime().exec(new String[]{
                    "curl", "-s", "--connect-timeout", "1", "-m", "1", "http://localhost:9090"});

            // Wait for the process to complete, but with a timeout
            boolean completed = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                completed = process.waitFor(1500, TimeUnit.MILLISECONDS);
            }
            int exitCode = completed ? process.exitValue() : -1;

            Log.d(TAG, "Curl exit code: " + exitCode);

            if (exitCode == 0 || exitCode == 28) {
                // If curl succeeded or at least connects (tcp port is open), service is likely running
                Log.d(TAG, "Curl connects, service is running");
                return Tile.STATE_ACTIVE;
            } else {
                // Curl failed or returned empty response
                Log.d(TAG, "Curl failed, service is not running");
                return Tile.STATE_INACTIVE;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error using curl to check service: " + e.getMessage());
            return Tile.STATE_INACTIVE;
        }
    }

    private boolean setBoxState(int state) {
        try {
            String command;
            if (state == Tile.STATE_ACTIVE) {
                command = "rm -f " + DISABLE_FILE;
            } else {
                command = "touch " + DISABLE_FILE;
            }

            Log.d(TAG, "Executing command: " + command);

            // Execute the command with root permission
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});

            // Read the output for debugging
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            Log.d(TAG, "Command output: " + output);

            // Read error stream as well
            reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder error = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                error.append(line);
            }
            if (error.length() > 0) {
                Log.e(TAG, "Command error: " + error);
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();
            Log.d(TAG, "Command exit code: " + exitCode);

            // For stopping, exit code 1 might be expected
            if (state == Tile.STATE_INACTIVE && exitCode == 1) {
                return true;
            }

            return exitCode == 0;

        } catch (Exception e) {
            Log.e(TAG, "Error changing box service state", e);
            return false;
        }
    }

    /*
    private boolean isScriptAvailable() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "[ -x " + SCRIPT_PATH + " ] && echo exists"});

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String output = reader.readLine();

            process.waitFor();

            return "exists".equals(output);
        } catch (Exception e) {
            Log.e(TAG, "Error checking if script exists", e);
            return false;
        }
    }
     */

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        Log.d(TAG, "Tile added to quick settings");
    }
}
