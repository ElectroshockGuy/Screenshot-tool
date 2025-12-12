package com.example.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 图片置顶窗口 - 可拖拽、缩放、始终置顶的图片显示窗口
 */
public class ImageTopWindow extends JFrame {

    private BufferedImage originalImage;
    private BufferedImage displayImage;
    private double scale = 1.0;
    private Point dragStartPoint;
    private JLabel imageLabel;
    private boolean forceOnTop = false;
    private Timer forceOnTopTimer;

    public ImageTopWindow(File imageFile) {
        try {
            originalImage = ImageIO.read(imageFile);
            if (originalImage == null) {
                throw new Exception("无法读取图片文件");
            }
            displayImage = originalImage;
            this.forceOnTop = true;
            initWindow(imageFile.getName(), true);
            initListeners();
            startForceOnTopTimer();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "打开图片失败: " + e.getMessage(),
                    "错误",
                    JOptionPane.ERROR_MESSAGE);
            dispose();
        }
    }

    public ImageTopWindow(BufferedImage image, String title) {
        this(image, title, true);
    }
    
    public ImageTopWindow(BufferedImage image, String title, boolean forceOnTop) {
        this.originalImage = image;
        this.displayImage = image;
        this.forceOnTop = forceOnTop;
        initWindow(title, forceOnTop);
        initListeners();
        if (forceOnTop) {
            startForceOnTopTimer();
        }
    }

    private void initWindow(String title, boolean forceOnTop) {
        setTitle("置顶 - " + title);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setAlwaysOnTop(true);
        if (forceOnTop) {
            // 强制置顶：设置为工具窗口类型，Win+D 时不会被最小化
            setType(Type.UTILITY);
        }
        setUndecorated(false);
        setResizable(true);

        // 创建图片显示面板
        imageLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (displayImage != null) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    // 保持宽高比绘制图片
                    int imgWidth = originalImage.getWidth();
                    int imgHeight = originalImage.getHeight();
                    g2d.drawImage(originalImage, 0, 0, getWidth(), getHeight(), 0, 0, imgWidth, imgHeight, null);
                }
            }
        };

        // 创建右键菜单
        JPopupMenu popupMenu = createPopupMenu();
        imageLabel.setComponentPopupMenu(popupMenu);

        // 计算初始窗口大小，保持原始宽高比
        int imgWidth = originalImage.getWidth();
        int imgHeight = originalImage.getHeight();
        
        // 如果图片超过屏幕80%，则缩放
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int maxWidth = (int) (screenSize.width * 0.8);
        int maxHeight = (int) (screenSize.height * 0.8);
        
        if (imgWidth > maxWidth || imgHeight > maxHeight) {
            double scaleX = (double) maxWidth / imgWidth;
            double scaleY = (double) maxHeight / imgHeight;
            scale = Math.min(scaleX, scaleY);
        } else {
            scale = 1.0;
        }
        
        int windowWidth = (int) (imgWidth * scale);
        int windowHeight = (int) (imgHeight * scale);
        
        imageLabel.setPreferredSize(new Dimension(windowWidth, windowHeight));
        setContentPane(imageLabel);
        pack(); // 使用pack()让窗口自动适应内容大小，会自动加上标题栏高度
        setLocationRelativeTo(null);
    }

    private JPopupMenu createPopupMenu() {
        JPopupMenu popup = new JPopupMenu();

        // 缩放选项
        JMenu zoomMenu = new JMenu("缩放");
        String[] zoomLevels = {"25%", "50%", "75%", "100%", "150%", "200%"};
        double[] zoomValues = {0.25, 0.5, 0.75, 1.0, 1.5, 2.0};
        for (int i = 0; i < zoomLevels.length; i++) {
            final double zoomValue = zoomValues[i];
            JMenuItem zoomItem = new JMenuItem(zoomLevels[i]);
            zoomItem.addActionListener(e -> setZoom(zoomValue));
            zoomMenu.add(zoomItem);
        }
        popup.add(zoomMenu);

        // 适应窗口
        JMenuItem fitItem = new JMenuItem("适应窗口");
        fitItem.addActionListener(e -> fitToWindow());
        popup.add(fitItem);

        // 原始大小
        JMenuItem originalSizeItem = new JMenuItem("原始大小");
        originalSizeItem.addActionListener(e -> setZoom(1.0));
        popup.add(originalSizeItem);

        popup.addSeparator();

        // 复制到剪贴板
        JMenuItem copyItem = new JMenuItem("复制到剪贴板");
        copyItem.addActionListener(e -> copyToClipboard());
        popup.add(copyItem);

        // 另存为
        JMenuItem saveAsItem = new JMenuItem("另存为...");
        saveAsItem.addActionListener(e -> saveImageAs());
        popup.add(saveAsItem);

        popup.addSeparator();

        // 置顶模式切换（单选）
        JCheckBoxMenuItem forceOnTopItem = new JCheckBoxMenuItem("强制置顶", forceOnTop);
        JCheckBoxMenuItem normalOnTopItem = new JCheckBoxMenuItem("普通置顶", !forceOnTop);
        
        forceOnTopItem.setToolTipText("Win+D时不会消失，始终在最前");
        forceOnTopItem.addActionListener(e -> {
            setForceOnTop(true);
            forceOnTopItem.setSelected(true);
            normalOnTopItem.setSelected(false);
        });
        
        normalOnTopItem.setToolTipText("可被其他窗口覆盖");
        normalOnTopItem.addActionListener(e -> {
            setForceOnTop(false);
            normalOnTopItem.setSelected(true);
            forceOnTopItem.setSelected(false);
        });
        
        popup.add(forceOnTopItem);
        popup.add(normalOnTopItem);

        popup.addSeparator();

        // 关闭
        JMenuItem closeItem = new JMenuItem("关闭");
        closeItem.addActionListener(e -> dispose());
        popup.add(closeItem);

        return popup;
    }

    private void initListeners() {
        // 鼠标拖拽移动窗口
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    dragStartPoint = e.getPoint();
                }
            }
        });

        imageLabel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragStartPoint != null) {
                    Point currentLocation = getLocation();
                    setLocation(
                            currentLocation.x + e.getX() - dragStartPoint.x,
                            currentLocation.y + e.getY() - dragStartPoint.y
                    );
                }
            }
        });

        // 鼠标滚轮缩放
        imageLabel.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                if (e.getWheelRotation() < 0) {
                    // 向上滚动，放大
                    setZoom(scale * 1.1);
                } else {
                    // 向下滚动，缩小
                    setZoom(scale * 0.9);
                }
            }
        });

        // 双击切换原始大小/适应窗口
        imageLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    if (Math.abs(scale - 1.0) < 0.01) {
                        fitToWindow();
                    } else {
                        setZoom(1.0);
                    }
                }
            }
        });

        // ESC关闭窗口
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                if (forceOnTop) {
                    bringToFrontIfNeeded();
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopForceOnTopTimer();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                stopForceOnTopTimer();
            }
        });
    }

    private void setForceOnTop(boolean enabled) {
        if (this.forceOnTop == enabled) {
            return;
        }
        this.forceOnTop = enabled;
        
        // 切换窗口类型需要重建窗口
        Type targetType = enabled ? Type.UTILITY : Type.NORMAL;
        if (getType() != targetType) {
            Point location = getLocation();
            Dimension size = getSize();
            
            setVisible(false);
            dispose();
            setType(targetType);
            setSize(size);
            setLocation(location);
            setVisible(true);
        }
        
        setAlwaysOnTop(true);
        
        if (enabled) {
            startForceOnTopTimer();
            toFront();
        } else {
            stopForceOnTopTimer();
        }
    }

    private void startForceOnTopTimer() {
        if (forceOnTopTimer != null && forceOnTopTimer.isRunning()) {
            return;
        }
        forceOnTopTimer = new Timer(800, e -> {
            if (forceOnTop && isVisible()) {
                bringToFrontIfNeeded();
            }
        });
        forceOnTopTimer.setRepeats(true);
        forceOnTopTimer.start();
    }

    private void stopForceOnTopTimer() {
        if (forceOnTopTimer != null) {
            forceOnTopTimer.stop();
            forceOnTopTimer = null;
        }
    }

    private void bringToFrontIfNeeded() {
        if (!isVisible()) {
            return;
        }
        // UTILITY 窗口类型在 Win+D 时不会被最小化，无需检查 ICONIFIED 状态
        setAlwaysOnTop(true);
        toFront();
    }

    private void setZoom(double newScale) {
        this.scale = Math.max(0.1, Math.min(5.0, newScale));
        int newWidth = (int) (originalImage.getWidth() * scale);
        int newHeight = (int) (originalImage.getHeight() * scale);
        imageLabel.setPreferredSize(new Dimension(newWidth, newHeight));
        pack(); // 使用pack()自动计算窗口大小（包含标题栏）
        repaint();
    }

    private void fitToWindow() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int maxWidth = (int) (screenSize.width * 0.8);
        int maxHeight = (int) (screenSize.height * 0.8);
        
        double scaleX = (double) maxWidth / originalImage.getWidth();
        double scaleY = (double) maxHeight / originalImage.getHeight();
        setZoom(Math.min(scaleX, scaleY));
    }

    private void copyToClipboard() {
        if (originalImage == null) return;

        TransferableImage transferable = new TransferableImage(originalImage);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);
        showAutoCloseToast("图片已复制到剪贴板");
    }

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

    private void saveImageAs() {
        if (originalImage == null) return;

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("另存为");
        
        // 默认文件名
        String defaultName = "image_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date()) + ".png";
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

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getAbsolutePath() + ".png");
            }

            try {
                ImageIO.write(originalImage, "PNG", file);
                // 使用自动消失的Toast提示
                showAutoCloseToast("图片已保存到:\n" + file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this,
                        "保存失败: " + e.getMessage(),
                        "错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 打开文件选择器并创建置顶窗口
     */
    public static void openImageFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("选择图片");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".png") || name.endsWith(".jpg") || 
                       name.endsWith(".jpeg") || name.endsWith(".gif") || 
                       name.endsWith(".bmp") || name.endsWith(".webp");
            }

            @Override
            public String getDescription() {
                return "图片文件 (*.png, *.jpg, *.jpeg, *.gif, *.bmp, *.webp)";
            }
        });

        int result = fileChooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            SwingUtilities.invokeLater(() -> {
                ImageTopWindow window = new ImageTopWindow(selectedFile);
                window.setVisible(true);
            });
        }
    }

    /**
     * 用于复制图片到剪贴板的Transferable实现
     */
    private static class TransferableImage implements Transferable {
        private final BufferedImage image;

        public TransferableImage(BufferedImage image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
