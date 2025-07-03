package plugin.javafxtools.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import plugin.javafxtools.base.ModuleLogger;
import plugin.javafxtools.util.TimeUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 应用程序启动器控制器 - 优化版，使用 JSON 文本格式存储应用路径，更易于查看和编辑
 */
public class AppLauncherController implements ModuleLogger {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final ExecutorService backgroundExecutor = Executors.newCachedThreadPool();

    private static final String STORAGE_FILE = "app_launcher_paths.json";
    private static final long PROCESS_CHECK_DELAY_MS = 1000;
    private static final int PROCESS_TERMINATE_TIMEOUT_MS = 2000;

    @FXML
    private TextField appPathField;
    @FXML
    private ListView<String> appListView;
    @FXML
    private Button browseButton, addButton, launchSingleButton, launchAllButton, killProcessButton, removeButton, clearButton;
    @FXML
    private TextArea logArea;

    private final List<String> appPaths = new ArrayList<>();
    private Stage primaryStage;
    private final ProcessTracker processTracker = new ProcessTracker();

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    @Override
    public TextArea getLogArea() {
        return logArea;
    }

    public void cleanup() {
        processTracker.killAllProcesses();
        appPaths.clear();
        // 关闭线程池
        backgroundExecutor.shutdown(); // 优雅关闭，已提交任务会继续执行
        executor.shutdown();
        try {
            if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow(); // 超时后强制关闭
            }
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // 超时后强制关闭
            }
        } catch (InterruptedException e) {
            backgroundExecutor.shutdownNow();
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        info("已清理所有资源");
    }

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            appPathField.setPromptText("输入应用程序路径或点击浏览...");
            logArea.setPromptText("操作日志将显示在这里...");
            appListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            loadAppPaths();
            info("应用程序启动器初始化完成");
        });
    }

    @FXML
    private void handleBrowse() {
        if (primaryStage == null) {
            error("主舞台未初始化，无法打开文件选择器");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择可执行文件");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        if (isWindows()) {
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("可执行文件", "*.exe", "*.bat", "*.cmd"),
                    new FileChooser.ExtensionFilter("所有文件", "*.*")
            );
        } else {
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("可执行文件", "*"),
                    new FileChooser.ExtensionFilter("所有文件", "*.*")
            );
        }
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            appPathField.setText(selectedFile.getAbsolutePath());
            info("已选择文件: " + selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleAdd() {
        String appPath = appPathField.getText().trim();
        if (appPath.isEmpty()) {
            error("请输入或选择应用程序路径");
            return;
        }
        File file = new File(appPath);
        if (!file.exists()) {
            error("指定路径不存在: " + appPath);
            return;
        }
        if (appPaths.contains(appPath)) {
            info("应用程序已存在: " + appPath);
            return;
        }
        appPaths.add(appPath);
        updateAppList();
        saveAppPaths();
        info("已添加应用程序: " + appPath);
        appPathField.clear();
    }

    @FXML
    private void handleLaunchSingle() {
        int selectedIndex = appListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            restartApplication(appPaths.get(selectedIndex));
        } else {
            error("请先选择要启动的应用程序");
        }
    }

    @FXML
    private void handleLaunchAll() {
        if (appPaths.isEmpty()) {
            error("应用程序列表为空");
            return;
        }
        info("开始批量启动 " + appPaths.size() + " 个应用程序...");
        executor.submit(() -> {
            for (String path : appPaths) {
                Platform.runLater(() -> info("准备启动: " + path));
                restartApplication(path);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    error("批量启动中断");
                    break;
                }
            }
            Platform.runLater(() -> info("批量启动操作完成"));
        });
    }

    @FXML
    private void handleKillProcess() {
        int selectedIndex = appListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            String path = appPaths.get(selectedIndex);
            if (processTracker.killProcess(path)) {
                info("已结束进程: " + path);
            } else {
                info("未找到运行中的进程: " + path);
            }
        } else {
            error("请先选择要结束的应用程序");
        }
    }

    @FXML
    private void handleRemove() {
        int selectedIndex = appListView.getSelectionModel().getSelectedIndex();
        if (selectedIndex >= 0) {
            String removedPath = appPaths.get(selectedIndex);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("确认移除");
            alert.setHeaderText("确认要移除所选应用程序吗？");
            alert.setContentText("[" + removedPath + "] 会被移除，相关进程将被终止。是否继续？");
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                info("用户取消了移除操作");
                return;
            }
            appPaths.remove(selectedIndex);
            if (processTracker.killProcess(removedPath)) {
                info("已停止并移除: " + removedPath);
            }
            updateAppList();
            saveAppPaths();
            info("已从列表移除: " + removedPath);
        } else {
            error("请先选择要移除的应用程序");
        }
    }

    @FXML
    private void handleClear() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认清空");
        alert.setHeaderText("确认要清除所有应用程序路径吗？");
        alert.setContentText("此操作将终止所有已启动的进程并清空列表，是否继续？");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            info("用户取消了清空操作");
            return;
        }
        boolean anyProcessKilled = appPaths.stream()
                .map(processTracker::killProcess)
                .reduce(false, Boolean::logicalOr);
        if (!anyProcessKilled) info("没有正在运行的进程");
        appPaths.clear();
        updateAppList();
        saveAppPaths();
        info("已清除所有应用程序路径");
    }

    /**
     * 清空日志按钮
     */
    @FXML
    private void handleClearLog() {
        Platform.runLater(() -> {
            if (logArea != null) {
                logArea.clear();
            }
        });
    }

    /**
     * 重启应用程序（如果正在运行则先关闭）
     */
    private void restartApplication(String path) {
        // 不要直接Platform.runLater，因为此方法已在事件线程调用
        if (processTracker.isProcessRunning(path)) {
            info("正在停止运行中的进程: " + path);
            if (processTracker.killProcess(path)) {
                info("成功停止进程: " + path);
                try {
                    Thread.sleep(PROCESS_CHECK_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } else {
                error("停止进程失败: " + path);
                return;
            }
        }
        launchApplication(path);
    }

    /**
     * 启动应用程序
     */
    private void launchApplication(String path) {
        try {
            Process process = processTracker.startProcess(path);
            if (process != null) {
                info("成功启动: " + path);
                startReadStreamThread(process.getInputStream(), msg -> info("[输出] " + path + ": " + msg));
                startReadStreamThread(process.getErrorStream(), msg -> error("[错误] " + path + ": " + msg));
            }
        } catch (IOException e) {
            error("启动失败: " + path + " - " + e.getMessage());
        }
    }

    private void startReadStreamThread(InputStream stream, Consumer<String> consumer) {
        backgroundExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                String line;
                while ((line = reader.readLine()) != null) consumer.accept(line);
            } catch (IOException ignored) {
            }
        });
    }

    private void updateAppList() {
        Platform.runLater(() -> appListView.getItems().setAll(appPaths));
    }

    private void loadAppPaths() {
        File file = new File(STORAGE_FILE);
        if (!file.exists()) return;
        try (Reader reader = new FileReader(file)) {
            List<String> savedPaths = new Gson().fromJson(reader, new TypeToken<List<String>>() {
            }.getType());
            if (savedPaths != null) {
                appPaths.clear();
                appPaths.addAll(savedPaths);
                updateAppList();
                info("已加载 " + appPaths.size() + " 个应用程序路径");
            }
        } catch (Exception e) {
            error("加载路径失败: " + e.getMessage());
        }
    }

    private void saveAppPaths() {
        try (Writer writer = new FileWriter(STORAGE_FILE)) {
            new Gson().toJson(appPaths, writer);
        } catch (Exception e) {
            error("保存路径失败: " + e.getMessage());
        }
    }

    @Override
    public void log(String level, String message) {
        String formattedMessage = String.format("\n[%s][%s] %s", TimeUtils.getCurrentDateTime(), level, message);
        Platform.runLater(() -> {
            if (logArea != null && logArea.getScene() != null) {
                logArea.appendText(formattedMessage);
                logArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * 进程跟踪器 - 负责进程生命周期管理
     */
    private class ProcessTracker {
        private final Map<String, Process> managedProcesses = new ConcurrentHashMap<>();

        boolean isProcessRunning(String path) {
            Process managed = managedProcesses.get(path);
            if (managed != null && managed.isAlive()) return true;
            String processName = getProcessName(path);
            try {
                return isWindows() ? checkWindowsProcess(processName) : checkUnixProcess(processName);
            } catch (IOException e) {
                error("进程检查错误: " + e.getMessage());
                return false;
            }
        }

        boolean killProcess(String path) {
            boolean killed = false;
            String processName = getProcessName(path);
            Process managed = managedProcesses.remove(path);
            if (managed != null && managed.isAlive()) {
                managed.destroy();
                try {
                    if (!managed.waitFor(PROCESS_TERMINATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        managed.destroyForcibly();
                    }
                    killed = true;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    managed.destroyForcibly();
                    killed = true;
                }
            }
            try {
                killed |= isWindows() ? killWindowsProcess(processName) : killUnixProcess(processName);
            } catch (IOException e) {
                error("结束进程失败: " + e.getMessage());
            }
            return killed;
        }

        void killAllProcesses() {
            managedProcesses.forEach((path, process) -> {
                if (process.isAlive()) {
                    process.destroy();
                    try {
                        if (!process.waitFor(PROCESS_TERMINATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                            process.destroyForcibly();
                        }
                        info("已结束进程: " + path);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        process.destroyForcibly();
                    }
                }
            });
            managedProcesses.clear();
        }

        Process startProcess(String path) throws IOException {
            ProcessBuilder builder;
            if (path.toLowerCase().endsWith(".bat") && isWindows()) {
                builder = new ProcessBuilder("cmd.exe", "/c", path);
            } else if (path.toLowerCase().endsWith(".bat")) {
                builder = new ProcessBuilder("wine", "cmd.exe", "/c", path);
            } else {
                builder = new ProcessBuilder(path);
            }
            builder.directory(new File(path).getParentFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            managedProcesses.put(path, process);
            monitorProcess(path, process);
            return process;
        }

        private void monitorProcess(String path, Process process) {
            backgroundExecutor.submit(() -> {
                try {
                    int exitCode = process.waitFor();
                    managedProcesses.remove(path);
                    info(String.format("进程已退出: %s (退出码: %d)", path, exitCode));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        private String getProcessName(String path) {
            return new File(path).getName();
        }

        private boolean checkWindowsProcess(String processName) throws IOException {
            Process process = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "tasklist /FI \"IMAGENAME eq " + processName + "\""});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines().anyMatch(line -> line.trim().startsWith(processName));
            }
        }

        private boolean checkUnixProcess(String processName) throws IOException {
            Process process = Runtime.getRuntime().exec(new String[]{"ps", "-A"});
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines().anyMatch(line -> line.contains(processName));
            }
        }

        private boolean killWindowsProcess(String processName) throws IOException {
            Process killProcess = Runtime.getRuntime().exec(new String[]{"cmd", "/c", "taskkill /F /IM " + processName});
            try {
                return killProcess.waitFor() == 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        private boolean killUnixProcess(String processName) throws IOException {
            Process killProcess = Runtime.getRuntime().exec(new String[]{"pkill", "-f", processName});
            try {
                return killProcess.waitFor() == 0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}