package com.yadli.surfingtile;

import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class SurfingTileService extends TileService {

    private static final String TAG = "SurfingTileService";
    private static final String SCRIPT_PATH = "/data/adb/box_bll/scripts/box.service";
    private static final String DISABLE_FILE = "/data/adb/modules/Surfing/disable";
    private static final String LOG_DIR = "/data/adb/modules/Surfingtile";
    private static final String LOG_FILE = LOG_DIR + "/tile.log";

    @Override
    public void onStartListening() {
        super.onStartListening();
        saveLog("onStartListening - 开始监听磁贴状态");
        
        // Update tile state when it becomes visible
        Tile tile = getQsTile();
        if (tile != null) {
            saveLog("当前磁贴UI状态: " + tile.getState() + 
                   " (STATE_ACTIVE=" + Tile.STATE_ACTIVE + 
                   ", STATE_INACTIVE=" + Tile.STATE_INACTIVE + ")");
            
            int state = getBoxState();
            saveLog("getBoxState检测结果: " + state + 
                   " (" + (state == Tile.STATE_ACTIVE ? "ACTIVE" : "INACTIVE") + ")");
            
            tile.setState(state);
            tile.updateTile();
            saveLog("更新磁贴状态为: " + state);
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        saveLog("=== onClick开始 ===");
        
        Tile tile = getQsTile();
        if (tile == null) {
            saveLog("错误: Tile为null!");
            return;
        }
        
        int currentTileState = tile.getState();
        saveLog("点击时磁贴状态: " + currentTileState + 
               " (" + (currentTileState == Tile.STATE_ACTIVE ? "ACTIVE" : "INACTIVE") + ")");
        
        int targetState;
        if (currentTileState == Tile.STATE_ACTIVE) {
            targetState = Tile.STATE_INACTIVE;
            saveLog("用户操作: 关闭服务 (ACTIVE → INACTIVE)");
        } else {
            targetState = Tile.STATE_ACTIVE;
            saveLog("用户操作: 开启服务 (INACTIVE → ACTIVE)");
        }
        
        boolean success = setBoxState(targetState);
        saveLog("setBoxState执行结果: " + (success ? "成功" : "失败"));
        
        if (success) {
            tile.setState(targetState);
            tile.updateTile();
            saveLog("磁贴UI更新为: " + targetState);
        }
        
        saveLog("=== onClick结束 ===");
    }

    private int getBoxState() {
        saveLog("[getBoxState] 开始检测服务状态...");
        
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "curl", "-s", "--connect-timeout", "1", "-m", "1", "http://localhost:9090"});

            boolean completed = false;
            int exitCode = -1;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                completed = process.waitFor(1500, TimeUnit.MILLISECONDS);
                exitCode = completed ? process.exitValue() : -1;
            } else {
                exitCode = process.waitFor();
                completed = true;
            }

            saveLog("[getBoxState] curl退出码: " + exitCode + ", completed: " + completed);
            
            // 读取curl的输出（如果有）
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            if (output.length() > 0) {
                saveLog("[getBoxState] curl输出: " + output.toString());
            }
            
            // 读取错误输出
            reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder error = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                error.append(line);
            }
            if (error.length() > 0) {
                saveLog("[getBoxState] curl错误: " + error.toString());
            }
            
            // 记录详细的检测逻辑
            if (exitCode == 0) {
                saveLog("[getBoxState] 判断结果: ACTIVE (curl成功)");
                return Tile.STATE_ACTIVE;
            } else if (exitCode == 28) {
                saveLog("[getBoxState] 警告: curl超时 (exitCode=28) - 返回ACTIVE");
                return Tile.STATE_ACTIVE;
            } else {
                saveLog("[getBoxState] 判断结果: INACTIVE (curl失败, exitCode=" + exitCode + ")");
                return Tile.STATE_INACTIVE;
            }

        } catch (Exception e) {
            saveLog("[getBoxState] 异常: " + e.getMessage() + " - 返回INACTIVE");
            return Tile.STATE_INACTIVE;
        }
    }

    private boolean setBoxState(int state) {
        saveLog("[setBoxState] 开始设置状态为: " + 
               (state == Tile.STATE_ACTIVE ? "ACTIVE(开启)" : "INACTIVE(关闭)"));
        
        try {
            String command;
            if (state == Tile.STATE_ACTIVE) {
                command = "rm -f " + DISABLE_FILE;
                saveLog("[setBoxState] 执行命令: 删除disable文件");
            } else {
                command = "touch " + DISABLE_FILE;
                saveLog("[setBoxState] 执行命令: 创建disable文件");
            }

            // Execute the command with root permission
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});

            // Read the output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            if (output.length() > 0) {
                saveLog("[setBoxState] 命令输出: " + output);
            }

            // Read error stream as well
            reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder error = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                error.append(line);
            }
            if (error.length() > 0) {
                saveLog("[setBoxState] 命令错误: " + error);
            }

            // Wait for the process to complete
            int exitCode = process.waitFor();
            saveLog("[setBoxState] 命令退出码: " + exitCode);

            // 验证文件状态
            String checkCmd = "[ -f " + DISABLE_FILE + " ] && echo 'exists'";
            Process checkProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", checkCmd});
            BufferedReader checkReader = new BufferedReader(new InputStreamReader(checkProcess.getInputStream()));
            String checkResult = checkReader.readLine();
            checkProcess.waitFor();
            
            boolean fileExists = "exists".equals(checkResult);
            saveLog("[setBoxState] disable文件状态: " + (fileExists ? "存在" : "不存在"));
            
            // 对于停止操作，exitCode=1可能是正常的
            if (state == Tile.STATE_INACTIVE && exitCode == 1) {
                saveLog("[setBoxState] 停止操作退出码为1，视为成功");
                return true;
            }

            boolean success = exitCode == 0;
            saveLog("[setBoxState] 最终结果: " + (success ? "成功" : "失败"));
            return success;

        } catch (Exception e) {
            saveLog("[setBoxState] 异常: " + e.getMessage());
            return false;
        }
    }

    private void saveLog(String message) {
        try {
            // 同时输出到Logcat
            Log.d(TAG, message);
            
            // 创建日志目录
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 创建时间戳
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            
            // 写入日志文件
            String logEntry = timestamp + " - " + message + "\n";
            FileOutputStream fos = new FileOutputStream(LOG_FILE, true);
            fos.write(logEntry.getBytes());
            fos.close();
            
        } catch (Exception e) {
            Log.e(TAG, "保存日志失败: " + e.getMessage());
        }
    }

    @Override
    public void onTileAdded() {
        super.onTileAdded();
        saveLog("=== 磁贴添加到快捷设置 ===");
    }
    
    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
        saveLog("=== 磁贴从快捷设置移除 ===");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        saveLog("=== TileService销毁 ===");
    }
}
