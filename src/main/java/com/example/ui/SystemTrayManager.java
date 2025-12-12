package com.example.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * 系统托盘管理器
 * 使用 JPopupMenu + JDialog 方案支持中文菜单
 */
public class SystemTrayManager {
    
    private static SystemTrayManager instance;
    private SystemTray systemTray;
    private TrayIcon trayIcon;
    private JPopupMenu jPopupMenu;
    private JDialog popupDialog;
    
    private SystemTrayManager() {
        if (!SystemTray.isSupported()) {
            System.err.println("系统不支持托盘");
            return;
        }
        systemTray = SystemTray.getSystemTray();
        initTrayIcon();
    }
    
    public static synchronized SystemTrayManager getInstance() {
        if (instance == null) {
            instance = new SystemTrayManager();
        }
        return instance;
    }
    
    private void initTrayIcon() {
        // 创建托盘图标
        Image icon = createTrayIconImage();
        
        // 创建 JDialog 作为 JPopupMenu 的载体
        popupDialog = new JDialog();
        popupDialog.setUndecorated(true);
        popupDialog.setSize(1, 1);
        popupDialog.setAlwaysOnTop(true);
        
        // 创建 JPopupMenu（支持中文）
        jPopupMenu = new JPopupMenu() {
            @Override
            public void firePopupMenuWillBecomeInvisible() {
                super.firePopupMenuWillBecomeInvisible();
                // 菜单消失时隐藏 Dialog
                popupDialog.setVisible(false);
            }
        };
        
        Font menuFont = new Font("微软雅黑", Font.PLAIN, 12);
        
        // 截图菜单项
        JMenuItem captureItem = new JMenuItem("截图");
        captureItem.setFont(menuFont);
        captureItem.addActionListener(e -> {
            // 隐藏所有可见的主窗口
            for (Window window : Window.getWindows()) {
                if (window instanceof JFrame && window.isVisible() && !(window instanceof ScreenCaptureWindow)) {
                    ((JFrame) window).setExtendedState(JFrame.ICONIFIED);
                }
            }
            // 延迟启动截图，等待窗口最小化
            Timer timer = new Timer(300, evt -> {
                SwingUtilities.invokeLater(() -> {
                    ScreenCaptureWindow captureWindow = new ScreenCaptureWindow();
                    captureWindow.setVisible(true);
                });
            });
            timer.setRepeats(false);
            timer.start();
        });
        jPopupMenu.add(captureItem);
        
        // 打开图片置顶菜单项
        JMenuItem openImageItem = new JMenuItem("图片置顶");
        openImageItem.setFont(menuFont);
        openImageItem.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> {
                ImageTopWindow.openImageFile();
            });
        });
        jPopupMenu.add(openImageItem);
        
        jPopupMenu.addSeparator();
        
        // 主窗口菜单项
        JMenuItem mainWindowItem = new JMenuItem("主窗口");
        mainWindowItem.setFont(menuFont);
        mainWindowItem.addActionListener(e -> showMainWindow());
        jPopupMenu.add(mainWindowItem);
        
        jPopupMenu.addSeparator();
        
        // 退出菜单项
        JMenuItem exitItem = new JMenuItem("退出");
        exitItem.setFont(menuFont);
        exitItem.addActionListener(e -> {
            removeTrayIcon();
            System.exit(0);
        });
        jPopupMenu.add(exitItem);
        
        // 创建托盘图标（不使用 AWT PopupMenu）
        trayIcon = new TrayIcon(icon, "截图工具");
        trayIcon.setImageAutoSize(true);
        
        // 鼠标事件处理
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                    // 双击左键打开主窗口
                    showMainWindow();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    // 右键显示菜单，使用鼠标在屏幕上的绝对坐标
                    Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
                    showJPopupMenu(mouseLocation.x, mouseLocation.y);
                }
            }
        });
    }
    
    private void showJPopupMenu(int screenX, int screenY) {
        // 获取菜单大小
        Dimension menuSize = jPopupMenu.getPreferredSize();
        
        // 获取屏幕工作区域（排除任务栏）
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Rectangle screenBounds = ge.getMaximumWindowBounds();
        
        // 计算菜单位置（在鼠标位置上方显示，因为托盘通常在底部）
        int menuX = screenX;
        int menuY = screenY - menuSize.height;
        
        // 如果超出左边界
        if (menuX < screenBounds.x) {
            menuX = screenBounds.x;
        }
        // 如果超出右边界
        if (menuX + menuSize.width > screenBounds.x + screenBounds.width) {
            menuX = screenBounds.x + screenBounds.width - menuSize.width;
        }
        // 如果超出上边界，则在下方显示
        if (menuY < screenBounds.y) {
            menuY = screenY;
        }
        
        // 设置 Dialog 位置并显示
        popupDialog.setLocation(menuX, menuY);
        popupDialog.setVisible(true);
        
        // 在 Dialog 上显示菜单
        jPopupMenu.show(popupDialog, 0, 0);
    }
    
    /**
     * 创建托盘图标图像
     */
    private Image createTrayIconImage() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // 绘制一个简单的截图图标（相机/截图形状）
        g2d.setColor(new Color(0, 122, 255));
        g2d.fillRoundRect(1, 3, 14, 10, 3, 3);
        
        // 镜头
        g2d.setColor(Color.WHITE);
        g2d.fillOval(5, 5, 6, 6);
        g2d.setColor(new Color(0, 122, 255));
        g2d.fillOval(6, 6, 4, 4);
        
        // 闪光灯
        g2d.setColor(new Color(255, 200, 0));
        g2d.fillRect(11, 4, 2, 2);
        
        g2d.dispose();
        return image;
    }
    
    /**
     * 显示主窗口
     */
    private void showMainWindow() {
        for (Window window : Window.getWindows()) {
            if (window instanceof MainFrame) {
                MainFrame mainFrame = (MainFrame) window;
                // 如果窗口最小化，恢复正常状态
                if (mainFrame.getExtendedState() == JFrame.ICONIFIED) {
                    mainFrame.setExtendedState(JFrame.NORMAL);
                }
                mainFrame.setVisible(true);
                mainFrame.toFront();
                mainFrame.requestFocus();
                return;
            }
        }
    }
    
    /**
     * 显示托盘图标
     */
    public void showTrayIcon() {
        if (systemTray == null || trayIcon == null) {
            return;
        }
        try {
            // 检查是否已添加
            for (TrayIcon icon : systemTray.getTrayIcons()) {
                if (icon == trayIcon) {
                    return;
                }
            }
            systemTray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 移除托盘图标
     */
    public void removeTrayIcon() {
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
        }
    }
    
    /**
     * 显示托盘消息
     */
    public void showMessage(String caption, String text, TrayIcon.MessageType messageType) {
        if (trayIcon != null) {
            trayIcon.displayMessage(caption, text, messageType);
        }
    }
    
    /**
     * 检查系统是否支持托盘
     */
    public static boolean isSupported() {
        return SystemTray.isSupported();
    }
}
