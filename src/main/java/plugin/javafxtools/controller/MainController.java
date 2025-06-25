package plugin.javafxtools.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import plugin.javafxtools.base.ModuleLogger;
import plugin.javafxtools.service.LoggingService;
import plugin.javafxtools.util.TimeUtils;

/**
 * 主控制器 - 协调各功能模块和共享服务
 */
public class MainController {
    @FXML
    private TabPane tabPane; // 主TabPane容器

    @FXML
    private HttpRequestController httpRequestTabController;  // 对应 httpRequestTab
    @FXML
    private WebSocketController webSocketTabController;      // 对应 webSocketTab
    @FXML
    private NetworkToolsController networkToolsTabController; // 对应 networkToolsTab
    @FXML
    private DataFormatController dataFormatTabController;    // 对应 dataFormatTab


    // 共享服务
    private final LoggingService loggingService = new LoggingService();

    // 中央日志区域（可选）
    @FXML
    private TextArea centralLogArea;

    /**
     * 初始化方法 - 由JavaFX在FXML加载完成后自动调用
     */
    @FXML
    public void initialize() {
        try {
            // 注册全局日志区域（中央日志）
            if (centralLogArea != null) {
                loggingService.addGlobalLogArea(centralLogArea);
            }
            // 配置子控制器
            setupControllers();
            loggingService.info("主控制器初始化完成");
        } catch (Exception e) {
            System.err.println("主控制器初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * 配置各子控制器的日志服务并绑定日志区域
     */
    private void setupControllers() {
        checkControllerInjection();
        if (httpRequestTabController != null) {
            loggingService.info("HTTP请求控制器初始化成功");
        } else {
            loggingService.info("HTTP请求Tab或控制器为空");
        }

        if (webSocketTabController != null) {
            loggingService.info("WebSocket控制器初始化成功");
        } else {
            loggingService.info("WebSocket Tab或控制器为空");
        }

        if (networkToolsTabController != null) {
            loggingService.info("网络工具控制器初始化成功");
        } else {
            loggingService.info("网络工具 Tab或控制器为空");
        }

        if (dataFormatTabController != null) {
            loggingService.info("数据格式化控制器初始化成功");
        } else {
            loggingService.info("数据格式化 Tab或控制器为空");
        }
    }


    /**
     * 检查控制器注入状态
     */
    private void checkControllerInjection() {
        StringBuilder errorMsg = new StringBuilder();

        if (httpRequestTabController == null) {
            errorMsg.append("HTTP请求控制器注入失败\n");
        }
        if (webSocketTabController == null) {
            errorMsg.append("WebSocket控制器注入失败\n");
        }
        if (networkToolsTabController == null) {
            errorMsg.append("网络工具控制器注入失败\n");
        }
        if (dataFormatTabController == null) {
            errorMsg.append("数据格式化控制器注入失败\n");
        }

        if (!errorMsg.isEmpty()) {
            throw new IllegalStateException(errorMsg.toString());
        }
    }


    /**
     * 获取主TabPane
     *
     * @return 主TabPane实例
     */
    public TabPane getTabPane() {
        return tabPane;
    }

    /**
     * 根据 Tab 对象获取对应的日志区域
     */
    private TextArea getLogAreaByTab(Tab tab) {
        if (tab.getContent() != null && tab.getContent().getUserData() != null) {
            Object controller = tab.getContent().getUserData();
            if (controller instanceof ModuleLogger) {
                return ((ModuleLogger) controller).getLogArea();
            }
        }
        return null;
    }

    /**
     * 记录全局日志到中央日志区
     */
    private void logToGlobal(String level, String message) {
        String formattedMessage = String.format("[%s][%s] %s",
                TimeUtils.getCurrentDateTime(), level, message);

        Platform.runLater(() -> {
            if (centralLogArea != null && centralLogArea.getScene() != null) {
                centralLogArea.appendText(formattedMessage + "\n");
                centralLogArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    /**
     * 清理所有资源
     */
    public void cleanup() {
        try {
            // 清理子控制器资源
            if (httpRequestTabController != null) {
                httpRequestTabController.cleanup();
            }
            if (webSocketTabController != null) {
                webSocketTabController.cleanup();
            }
            if (networkToolsTabController != null) {
                networkToolsTabController.cleanup();
            }
            if (dataFormatTabController != null) {
                dataFormatTabController.cleanup();
            }
            // 清理日志区域
            if (centralLogArea != null) {
                centralLogArea.clear();
            }
            logToGlobal("INFO", "应用程序资源已清理");

            System.out.println("MainController 资源已清理");
        } catch (Exception e) {
            System.err.println("资源清理出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
