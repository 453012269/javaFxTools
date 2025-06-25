package plugin.javafxtools.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import plugin.javafxtools.base.ModuleLogger;
import plugin.javafxtools.util.TimeUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * HTTP请求控制器 - 处理HTTP请求调度功能
 */
public class HttpRequestController implements ModuleLogger {

    // UI组件
    @FXML
    private TextField startTimeField;
    @FXML
    private TextField intervalField;
    @FXML
    private TextField urlField;
    @FXML
    private ComboBox<String> methodComboBox;
    @FXML
    private TextArea paramsArea;
    @FXML
    private TextArea logArea;
    @FXML
    private Button startButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button nowButton;

    // 业务字段
    private ScheduledExecutorService scheduler;
    private Future<?> currentTaskFuture;
    private boolean isRunning = false;

    /**
     * 获取日志区域
     */
    @Override
    public TextArea getLogArea() {
        return logArea;
    }


    /**
     * 自定义日志方法 - 只输出到本模块日志区
     */
    @Override
    public void log(String level, String message) {
        String formattedMessage = String.format("[%s][%s] %s",
                TimeUtils.getCurrentDateTime(), level, message);

        Platform.runLater(() -> {
            if (logArea != null && logArea.getScene() != null) {
                logArea.appendText(formattedMessage + "\n");
                logArea.setScrollTop(Double.MAX_VALUE); // 自动滚动到底部
            }
        });
    }

    /**
     * 初始化方法 - 由JavaFX自动调用
     */
    @FXML
    public void initialize() {
        try {
            // 初始化UI组件状态
            stopButton.setDisable(true);

            // 初始化请求方法下拉框
            methodComboBox.getItems().addAll("GET", "POST", "PUT", "DELETE");
            methodComboBox.setValue("GET");

            // 设置默认开始时间
            startTimeField.setText(TimeUtils.getCurrentDateTime());

            // 设置默认URL示例
            urlField.setText("https://jsonplaceholder.typicode.com/posts");

            // 设置参数区域提示
            paramsArea.setPromptText("GET参数示例: userId=1&id=2\nPOST参数示例: {\"title\":\"foo\",\"body\":\"bar\",\"userId\":1}");

            info("HTTP请求模块初始化完成");

        } catch (Exception e) {
            error("HTTP控制器初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理"现在"按钮点击 - 设置开始时间为当前时间
     */
    @FXML
    private void handleNowButton() {
        startTimeField.setText(TimeUtils.getCurrentDateTime());
        info("已设置开始时间为当前时间");
    }

    /**
     * 处理"开始"按钮点击 - 启动HTTP请求调度
     */
    @FXML
    private void handleStartButton() {
        if (isRunning) {
            info("调度器已在运行中");
            return;
        }

        // 获取用户输入
        String startTimeStr = startTimeField.getText().trim();
        String intervalStr = intervalField.getText().trim();
        String urlStr = urlField.getText().trim();
        String method = methodComboBox.getValue();
        String params = paramsArea.getText().trim();

        // 验证输入
        if (startTimeStr.isEmpty() || intervalStr.isEmpty() || urlStr.isEmpty()) {
            error("请填写所有必填字段（开始时间、间隔、URL）");
            return;
        }

        try {
            // 解析开始时间和间隔
            Date startTime = TimeUtils.parseDateTime(startTimeStr, TimeUtils.DEFAULT_DATETIME_FORMAT);
            if (startTime == null) {
                error("开始时间格式不正确，请使用 yyyy-MM-dd HH:mm:ss 格式");
                return;
            }
            long interval = Long.parseLong(intervalStr) * 1000; // 转换为毫秒
            long delay = startTime.getTime() - System.currentTimeMillis();

            // 如果开始时间已过，立即执行
            if (delay < 0) {
                debug("开始时间已过，将立即执行第一次请求");
                delay = 0;
            }

            // 创建单线程调度器
            scheduler = Executors.newSingleThreadScheduledExecutor();
            isRunning = true;

            // 更新UI状态
            startButton.setDisable(true);
            stopButton.setDisable(false);
            nowButton.setDisable(true);

            // 创建定时任务
            Runnable task = () -> {
                try {
                    info("准备发送 " + method + " 请求到: " + urlStr);
                    if (method.equals("POST") || method.equals("PUT")) {
                        info("请求体: " + params);
                    }

                    // 发送请求并记录响应
                    String response = sendRequest(urlStr, method, params);
                    info("请求成功: " + response);
                } catch (IOException e) {
                    error("请求失败: " + e.getMessage());
                } catch (Exception e) {
                    error("意外错误: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            };

            // 调度任务
            currentTaskFuture = scheduler.scheduleAtFixedRate(task, delay, interval, TimeUnit.MILLISECONDS);
            info(String.format("调度器已启动，将在 %s 开始执行，间隔 %d 秒",
                    TimeUtils.formatDateTime(startTime, TimeUtils.DEFAULT_DATETIME_FORMAT), interval / 1000));

        } catch (Exception e) {
            error("启动调度器失败: " + e.getMessage());
            stopScheduler();
        }
    }

    /**
     * 处理"停止"按钮点击 - 停止HTTP请求调度
     */
    @FXML
    private void handleStopButton() {
        stopScheduler();
       info("调度器已停止");
    }

    /**
     * 停止调度器并清理资源
     */
    private void stopScheduler() {
        isRunning = false;

        // 取消当前任务
        if (currentTaskFuture != null) {
            currentTaskFuture.cancel(true);
        }

        // 关闭调度器
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        // 更新UI状态
        Platform.runLater(() -> {
            startButton.setDisable(false);
            stopButton.setDisable(true);
            nowButton.setDisable(false);
        });
    }

    /**
     * 发送HTTP请求
     *
     * @param urlStr 请求URL
     * @param method 请求方法
     * @param params 请求参数
     * @return 响应内容
     * @throws IOException 如果发生I/O错误
     */
    private String sendRequest(String urlStr, String method, String params) throws IOException {
        String fullUrl = urlStr;
        HttpURLConnection connection = null;

        try {
            // 处理GET请求参数
            if (method.equals("GET") && !params.isEmpty()) {
                String encodedParams = URLEncoder.encode(params, "UTF-8");
                fullUrl += (urlStr.contains("?") ? "&" : "?") + encodedParams;
            }

            // 创建URL连接
            URL url = new URL(fullUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(5000); // 5秒连接超时
            connection.setReadTimeout(10000);   // 10秒读取超时

            // 设置通用请求头
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "JavaFX-HTTP-Client");

            // 处理POST/PUT请求
            if (method.equals("POST") || method.equals("PUT")) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

                // 写入请求体
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = params.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }
            }

            // 获取响应码
            int responseCode = connection.getResponseCode();

            // 读取响应内容
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    responseCode < HttpURLConnection.HTTP_BAD_REQUEST ?
                            connection.getInputStream() : connection.getErrorStream(), "utf-8"))) {

                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }

                // 检查响应码
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP 错误代码: " + responseCode +
                            "\n服务器返回: " + response.toString());
                }

                return response.toString();
            }
        } finally {
            // 确保连接关闭
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        stopScheduler();
        System.out.println("HttpRequestController 资源已清理");
    }
}