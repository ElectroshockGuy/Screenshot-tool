package com.example.service;

import com.example.ui.ScreenCaptureWindow;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 截图服务类 - 提供全屏截图和选区截图功能
 */
@Service
public class ScreenCaptureService {

    /**
     * 全屏截图
     */
    public void captureFullScreen() {
        try {
            Robot robot = new Robot();
            // 获取逻辑分辨率和物理分辨率
            Dimension logicalSize = Toolkit.getDefaultToolkit().getScreenSize();
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            DisplayMode dm = gd.getDisplayMode();
            int physicalWidth = dm.getWidth();
            int physicalHeight = dm.getHeight();
            
            // 判断是否需要使用物理分辨率（适配高DPI）
            double scaleX = (double) physicalWidth / logicalSize.width;
            double scaleY = (double) physicalHeight / logicalSize.height;
            boolean needsScaling = Math.abs(scaleX - 1.0) > 0.01 || Math.abs(scaleY - 1.0) > 0.01;
            
            BufferedImage screenImage;
            if (needsScaling) {
                screenImage = robot.createScreenCapture(new Rectangle(0, 0, physicalWidth, physicalHeight));
            } else {
                screenImage = robot.createScreenCapture(new Rectangle(logicalSize));
            }

            // 保存截图
            saveImage(screenImage);
        } catch (AWTException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "截图失败: " + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 选区截图 - 打开选区窗口
     */
    public void captureSelectedArea() {
        // 在EDT线程中打开选区窗口
        SwingUtilities.invokeLater(() -> {
            ScreenCaptureWindow captureWindow = new ScreenCaptureWindow();
            captureWindow.setVisible(true);
        });
    }

    /**
     * 保存图片
     */
    private void saveImage(BufferedImage image) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("保存截图");

        // 默认文件名
        String defaultName = "screenshot_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".png";
        fileChooser.setSelectedFile(new File(defaultName));

        // 文件过滤器
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".png");
            }

            @Override
            public String getDescription() {
                return "PNG 图片 (*.png)";
            }
        });

        int result = fileChooser.showSaveDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }

            try {
                ImageIO.write(image, "PNG", file);
                // 使用自动消失的Toast提示，无需用户确认
                showAutoCloseToast("截图已保存到:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "保存失败: " + e.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 显示自动消失的Toast提示
     */
    private void showAutoCloseToast(String message) {
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog();
            dialog.setUndecorated(true);
            dialog.setAlwaysOnTop(true);
            
            JLabel label = new JLabel("<html><center>" + message.replace("\n", "<br>") + "</center></html>", SwingConstants.CENTER);
            label.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            label.setForeground(Color.WHITE);
            label.setBackground(new Color(50, 50, 50, 230));
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
            
            dialog.add(label);
            dialog.pack();
            
            // 居中显示
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            dialog.setLocation(
                (screenSize.width - dialog.getWidth()) / 2,
                (screenSize.height - dialog.getHeight()) / 2
            );
            
            dialog.setVisible(true);
            
            // 2秒后自动关闭
            Timer timer = new Timer(2000, e -> dialog.dispose());
            timer.setRepeats(false);
            timer.start();
        });
    }
}
