package com.example.ui;

import com.example.service.ScreenCaptureService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatCyanLightIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatGradiantoDeepOceanIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.swing.*;
import java.awt.*;

/**
 * Swing主窗口 - 作为Spring Bean管理，可以注入其他Spring组件
 */
@Component
public class MainFrame extends JFrame {

    private final ScreenCaptureService screenCaptureService;

    private ButtonGroup themeButtonGroup;

    @Autowired
    public MainFrame(ScreenCaptureService screenCaptureService) {
        this.screenCaptureService = screenCaptureService;
    }

    @PostConstruct
    public void init() {
        initComponents();
        initLayout();
        initListeners();
    }

    private void initComponents() {
        setTitle("截图工具");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 520);
        setLocationRelativeTo(null); // 居中显示

        // 创建菜单栏
        initMenuBar();
    }

    private void initMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // 文件菜单
        JMenu fileMenu = new JMenu("文件");
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);

        // 截图菜单
        JMenu captureMenu = new JMenu("截图");
        
        JMenuItem fullScreenItem = new JMenuItem("全屏截图");
        fullScreenItem.setAccelerator(KeyStroke.getKeyStroke("ctrl shift A"));
        fullScreenItem.addActionListener(e -> {
            // 先最小化窗口，然后截图
            setExtendedState(JFrame.ICONIFIED);
            Timer timer = new Timer(300, evt -> {
                screenCaptureService.captureFullScreen();
                setExtendedState(JFrame.NORMAL);
            });
            timer.setRepeats(false);
            timer.start();
        });
        
        JMenuItem selectAreaItem = new JMenuItem("选区截图");
        selectAreaItem.setAccelerator(KeyStroke.getKeyStroke("ctrl shift S"));
        selectAreaItem.addActionListener(e -> {
            // 先最小化窗口，然后截图
            setExtendedState(JFrame.ICONIFIED);
            Timer timer = new Timer(300, evt -> {
                screenCaptureService.captureSelectedArea();
            });
            timer.setRepeats(false);
            timer.start();
        });
        
        captureMenu.add(fullScreenItem);
        captureMenu.add(selectAreaItem);

        // 工具菜单
        JMenu toolsMenu = new JMenu("工具");
        
        JMenuItem imageTopItem = new JMenuItem("图片置顶");
        imageTopItem.setAccelerator(KeyStroke.getKeyStroke("ctrl shift T"));
        imageTopItem.addActionListener(e -> ImageTopWindow.openImageFile());
        toolsMenu.add(imageTopItem);

        // 外观菜单
        JMenu viewMenu = new JMenu("外观");
        JMenu themeSubMenu = new JMenu("主题");

        themeButtonGroup = new ButtonGroup();
        String[] themes = {
            "FlatLaf Light",
            "FlatLaf Dark",
            "Dracula",
            "One Dark",
            "Arc Dark",
            "Arc Orange",
            "Cyan Light",
            "Gradianto Deep Ocean"
        };

        for (String theme : themes) {
            JRadioButtonMenuItem themeItem = new JRadioButtonMenuItem(theme);
            if ("FlatLaf Light".equals(theme)) {
                themeItem.setSelected(true);
            }
            themeItem.addActionListener(e -> changeTheme(theme));
            themeButtonGroup.add(themeItem);
            themeSubMenu.add(themeItem);
        }

        viewMenu.add(themeSubMenu);

        // 帮助菜单
        JMenu helpMenu = new JMenu("帮助");
        
        JMenuItem shortcutItem = new JMenuItem("快捷键");
        shortcutItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                "快捷键说明\n\n" +
                "Ctrl+Shift+S    选区截图\n" +
                "Ctrl+Shift+A    全屏截图\n" +
                "Ctrl+Shift+T    图片置顶\n\n" +
                "截图时：\n" +
                "C键             复制色值\n" +
                "ESC             取消截图\n" +
                "右键            取消/重新选区",
                "快捷键",
                JOptionPane.INFORMATION_MESSAGE);
        });
        helpMenu.add(shortcutItem);
        
        JMenuItem aboutItem = new JMenuItem("关于");
        aboutItem.addActionListener(e -> {
            JOptionPane.showMessageDialog(this,
                "截图工具\n\n" +
                "版本: 1.0.0\n" +
                "JDK: 1.8\n" +
                "SpringBoot: 2.7.18\n" +
                "外观: FlatLaf 3.2.5",
                "关于",
                JOptionPane.INFORMATION_MESSAGE);
        });
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(captureMenu);
        menuBar.add(toolsMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);

        setJMenuBar(menuBar);
    }

    private void initLayout() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 中间内容区域
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // 标题
        JLabel titleLabel = new JLabel("截图工具");
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 32));
        titleLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

        // 副标题
        JLabel subtitleLabel = new JLabel("轻量、快捷、功能丰富的截图工具");
        subtitleLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(128, 128, 128));
        subtitleLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

        // 功能描述面板
        JPanel featurePanel = new JPanel();
        featurePanel.setLayout(new BoxLayout(featurePanel, BoxLayout.Y_AXIS));
        featurePanel.setOpaque(false);
        featurePanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        
        String[] features = {
            "📷 选区截图 - 支持多种标注工具",
            "📌 图片置顶 - 支持强制置顶模式",
            "🎨 取色器 - 按C键复制颜色值",
            "✏️ 丰富标注 - 箭头/文字/形状/序号"
        };
        
        for (String feature : features) {
            JLabel featureLabel = new JLabel(feature);
            featureLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            featureLabel.setForeground(new Color(100, 100, 100));
            featureLabel.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            featurePanel.add(featureLabel);
            featurePanel.add(Box.createVerticalStrut(4));
        }

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setOpaque(false);

        JButton selectCaptureBtn = new JButton("选区截图");
        selectCaptureBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        selectCaptureBtn.setPreferredSize(new Dimension(120, 40));
        selectCaptureBtn.addActionListener(e -> {
            setExtendedState(JFrame.ICONIFIED);
            Timer timer = new Timer(300, evt -> screenCaptureService.captureSelectedArea());
            timer.setRepeats(false);
            timer.start();
        });

        JButton fullCaptureBtn = new JButton("全屏截图");
        fullCaptureBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        fullCaptureBtn.setPreferredSize(new Dimension(120, 40));
        fullCaptureBtn.addActionListener(e -> {
            setExtendedState(JFrame.ICONIFIED);
            Timer timer = new Timer(300, evt -> {
                screenCaptureService.captureFullScreen();
                setExtendedState(JFrame.NORMAL);
            });
            timer.setRepeats(false);
            timer.start();
        });

        JButton imageTopBtn = new JButton("图片置顶");
        imageTopBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        imageTopBtn.setPreferredSize(new Dimension(120, 40));
        imageTopBtn.addActionListener(e -> ImageTopWindow.openImageFile());

        buttonPanel.add(selectCaptureBtn);
        buttonPanel.add(fullCaptureBtn);
        buttonPanel.add(imageTopBtn);

        // 快捷键提示
        JPanel shortcutPanel = new JPanel();
        shortcutPanel.setLayout(new BoxLayout(shortcutPanel, BoxLayout.Y_AXIS));
        shortcutPanel.setOpaque(false);
        shortcutPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JLabel shortcutTitle = new JLabel("⌨️ 快捷键");
        shortcutTitle.setFont(new Font("微软雅黑", Font.BOLD, 11));
        shortcutTitle.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);

        String[] shortcuts = {
            "Ctrl+Shift+S 选区截图  |  Ctrl+Shift+A 全屏截图",
            "Ctrl+Shift+T 图片置顶  |  C键 复制色值  |  ESC 取消"
        };
        
        shortcutPanel.add(shortcutTitle);
        shortcutPanel.add(Box.createVerticalStrut(6));
        
        for (String shortcut : shortcuts) {
            JLabel label = new JLabel(shortcut);
            label.setFont(new Font("微软雅黑", Font.PLAIN, 10));
            label.setForeground(new Color(120, 120, 120));
            label.setAlignmentX(java.awt.Component.CENTER_ALIGNMENT);
            shortcutPanel.add(label);
            shortcutPanel.add(Box.createVerticalStrut(3));
        }

        // 组装中间面板
        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(titleLabel);
        centerPanel.add(Box.createVerticalStrut(8));
        centerPanel.add(subtitleLabel);
        centerPanel.add(Box.createVerticalStrut(20));
        centerPanel.add(featurePanel);
        centerPanel.add(buttonPanel);
        centerPanel.add(shortcutPanel);
        centerPanel.add(Box.createVerticalGlue());

        // 底部状态栏
        JLabel statusLabel = new JLabel("💡 提示：程序最小化后可通过系统托盘访问");
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(150, 150, 150));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void initListeners() {
        // 目前没有额外的监听器
    }

    private void changeTheme(String themeName) {
        try {
            switch (themeName) {
                case "FlatLaf Light":
                    FlatLightLaf.setup();
                    break;
                case "FlatLaf Dark":
                    FlatDarkLaf.setup();
                    break;
                case "Dracula":
                    FlatDraculaIJTheme.setup();
                    break;
                case "One Dark":
                    FlatOneDarkIJTheme.setup();
                    break;
                case "Arc Dark":
                    FlatArcDarkIJTheme.setup();
                    break;
                case "Arc Orange":
                    FlatArcOrangeIJTheme.setup();
                    break;
                case "Cyan Light":
                    FlatCyanLightIJTheme.setup();
                    break;
                case "Gradianto Deep Ocean":
                    FlatGradiantoDeepOceanIJTheme.setup();
                    break;
                default:
                    FlatLightLaf.setup();
            }
            // 更新所有组件的外观
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "切换主题失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}
