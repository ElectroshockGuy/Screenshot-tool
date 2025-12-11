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
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 选区截图窗口 - 全屏透明窗口，用于鼠标框选截图区域
 * 支持工具栏操作：置顶、复制、保存、取消
 */
public class ScreenCaptureWindow extends JFrame {

    private BufferedImage screenImage;
    private BufferedImage capturedImage;
    private Point startPoint;
    private Point endPoint;
    private Point mousePoint; // 当前鼠标位置，用于取色
    private Rectangle captureRect;
    private boolean isSelecting = false;
    private boolean selectionComplete = false;
    private JPanel toolBar;
    private CapturePanel capturePanel;
    
    // 标注相关
    private enum AnnotationMode { NONE, TEXT, ARROW, MOSAIC, NUMBER }
    private AnnotationMode currentMode = AnnotationMode.NONE;
    private java.util.List<Annotation> annotations = new java.util.ArrayList<>();
    private Point annotationStart;
    private Point annotationEnd;
    private boolean isAnnotating = false;
    private Color annotationColor = Color.RED;
    private String pendingText = null; // 待放置的文字
    private Annotation selectedAnnotation = null; // 当前选中的标注
    private Point dragStart = null; // 拖动起始点
    private boolean isDraggingAnnotation = false; // 是否正在拖动标注
    private int nextNumber = 1; // 下一个序号

    public ScreenCaptureWindow() {
        initWindow();
        initListeners();
    }

    private void initWindow() {
        // 获取全屏截图
        try {
            Robot robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            screenImage = robot.createScreenCapture(new Rectangle(screenSize));
        } catch (AWTException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "截图失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            dispose();
            return;
        }

        // 设置无边框全屏
        setUndecorated(true);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 设置内容面板
        capturePanel = new CapturePanel();
        setContentPane(capturePanel);

        // 创建工具栏（初始隐藏）
        createToolBar();
    }

    private void createToolBar() {
        toolBar = new JPanel();
        toolBar.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        toolBar.setBackground(new Color(45, 45, 45));
        toolBar.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));
        toolBar.setVisible(false);

        // 置顶按钮
        JButton pinButton = createToolButton("📌 置顶", "将截图置顶显示");
        pinButton.addActionListener(e -> pinToTop());

        // 复制按钮
        JButton copyButton = createToolButton("📋 复制", "复制到剪贴板");
        copyButton.addActionListener(e -> copyToClipboard());

        // 保存按钮
        JButton saveButton = createToolButton("💾 保存", "保存截图");
        saveButton.addActionListener(e -> saveImage());

        // 取消按钮
        JButton cancelButton = createToolButton("✖ 取消", "取消截图");
        cancelButton.addActionListener(e -> dispose());

        // 标注按钮
        JButton textButton = createToolButton("T 文字", "添加文字标注");
        textButton.addActionListener(e -> setAnnotationMode(AnnotationMode.TEXT));
        
        JButton arrowButton = createToolButton("→ 箭头", "添加箭头标注");
        arrowButton.addActionListener(e -> setAnnotationMode(AnnotationMode.ARROW));
        
        JButton mosaicButton = createToolButton("▦ 马赛克", "添加马赛克");
        mosaicButton.addActionListener(e -> setAnnotationMode(AnnotationMode.MOSAIC));
        
        JButton numberButton = createToolButton("① 序号", "添加序号标注");
        numberButton.addActionListener(e -> setAnnotationMode(AnnotationMode.NUMBER));

        toolBar.add(textButton);
        toolBar.add(arrowButton);
        toolBar.add(mosaicButton);
        toolBar.add(numberButton);
        toolBar.add(createSeparator());
        toolBar.add(pinButton);
        toolBar.add(copyButton);
        toolBar.add(saveButton);
        toolBar.add(createSeparator());
        toolBar.add(cancelButton);

        capturePanel.setLayout(null);
        capturePanel.add(toolBar);
    }

    private JButton createToolButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(60, 60, 60));
        button.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText(tooltip);

        // 鼠标悬停效果
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(80, 80, 80));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(60, 60, 60));
            }
        });

        return button;
    }

    private JSeparator createSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 24));
        separator.setForeground(new Color(80, 80, 80));
        return separator;
    }

    private void showToolBar() {
        if (captureRect == null) return;

        toolBar.setVisible(true);
        Dimension toolBarSize = toolBar.getPreferredSize();
        int toolBarWidth = (int) toolBarSize.getWidth();
        int toolBarHeight = (int) toolBarSize.getHeight();

        // 计算工具栏位置（优先放在选区右下角外部）
        int x = captureRect.x + captureRect.width - toolBarWidth;
        int y = captureRect.y + captureRect.height + 5;

        // 如果超出屏幕底部，尝试放到选区上方
        if (y + toolBarHeight > getHeight()) {
            y = captureRect.y - toolBarHeight - 5;
        }

        // 如果上方也放不下（全屏截图情况），放到选区内部右下角
        if (y < 0) {
            y = captureRect.y + captureRect.height - toolBarHeight - 10;
        }

        // 如果超出屏幕右边，左移
        if (x + toolBarWidth > getWidth()) {
            x = getWidth() - toolBarWidth - 10;
        }

        // 如果超出屏幕左边
        if (x < 0) {
            x = 10;
        }

        // 确保工具栏在选区内可见（针对全屏情况）
        if (y < captureRect.y) {
            y = captureRect.y + 10;
        }

        toolBar.setBounds(x, y, toolBarWidth, toolBarHeight);
        repaint();
    }

    private void setAnnotationMode(AnnotationMode mode) {
        this.currentMode = mode;
        if (mode == AnnotationMode.TEXT) {
            // 文字模式：先输入文字，然后点击放置
            String text = JOptionPane.showInputDialog(this, "请输入文字（然后点击选区内位置放置）：", "文字标注", JOptionPane.PLAIN_MESSAGE);
            if (text != null && !text.trim().isEmpty()) {
                pendingText = text;
                // 保持TEXT模式，等待用户点击放置
            } else {
                this.currentMode = AnnotationMode.NONE;
            }
        }
        // NUMBER模式不需要特殊处理，直接点击放置即可
    }

    private void initListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    Point p = e.getPoint();
                    
                    // 如果在标注模式且在选区内
                    if (selectionComplete && currentMode != AnnotationMode.NONE && captureRect != null && captureRect.contains(p)) {
                        if (toolBar.isVisible() && toolBar.getBounds().contains(p)) {
                            return;
                        }
                        
                        // 文字模式：点击放置文字（可多次放置同一文字）
                        if (currentMode == AnnotationMode.TEXT && pendingText != null) {
                            annotations.add(new TextAnnotation(p.x, p.y, pendingText, annotationColor));
                            // 保持TEXT模式，可以继续放置同样的文字
                            repaint();
                            return;
                        }
                        
                        // 序号模式：点击放置序号（自动递增）
                        if (currentMode == AnnotationMode.NUMBER) {
                            annotations.add(new NumberAnnotation(p.x, p.y, nextNumber++, annotationColor));
                            // 保持NUMBER模式，可以继续放置下一个序号
                            repaint();
                            return;
                        }
                        
                        annotationStart = p;
                        annotationEnd = p;
                        isAnnotating = true;
                        return;
                    }
                    
                    // 如果已完成选区且点击在工具栏外
                    if (selectionComplete) {
                        if (toolBar.isVisible() && toolBar.getBounds().contains(p)) {
                            return;
                        }
                        
                        // 检查是否点击了已有标注（用于拖动）
                        if (captureRect != null && captureRect.contains(p) && currentMode == AnnotationMode.NONE) {
                            for (int i = annotations.size() - 1; i >= 0; i--) {
                                if (annotations.get(i).contains(p)) {
                                    selectedAnnotation = annotations.get(i);
                                    dragStart = p;
                                    isDraggingAnnotation = true;
                                    repaint();
                                    return;
                                }
                            }
                        }
                        
                        // 如果点击在选区内，不做任何操作
                        if (captureRect != null && captureRect.contains(p)) {
                            return;
                        }
                        // 点击在选区外，重新开始选择
                        selectionComplete = false;
                        toolBar.setVisible(false);
                        annotations.clear();
                        currentMode = AnnotationMode.NONE;
                        pendingText = null;
                    }
                    startPoint = e.getPoint();
                    endPoint = startPoint;
                    isSelecting = true;
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    // 右键取消
                    if (currentMode != AnnotationMode.NONE) {
                        currentMode = AnnotationMode.NONE;
                        pendingText = null;
                        repaint();
                    } else if (selectionComplete) {
                        // 如果已选择，右键清除选区重新选择
                        selectionComplete = false;
                        toolBar.setVisible(false);
                        startPoint = null;
                        endPoint = null;
                        captureRect = null;
                        annotations.clear();
                        repaint();
                    } else {
                        dispose();
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (isDraggingAnnotation) {
                        isDraggingAnnotation = false;
                        selectedAnnotation = null;
                        dragStart = null;
                        repaint();
                    } else if (isAnnotating) {
                        annotationEnd = e.getPoint();
                        finishAnnotation();
                        isAnnotating = false;
                    } else if (isSelecting) {
                        endPoint = e.getPoint();
                        isSelecting = false;
                        finishSelection();
                    }
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                mousePoint = e.getPoint();
                if (isDraggingAnnotation && selectedAnnotation != null && dragStart != null) {
                    int dx = e.getX() - dragStart.x;
                    int dy = e.getY() - dragStart.y;
                    selectedAnnotation.move(dx, dy);
                    dragStart = e.getPoint();
                    repaint();
                } else if (isAnnotating) {
                    annotationEnd = e.getPoint();
                    repaint();
                } else if (isSelecting) {
                    endPoint = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mousePoint = e.getPoint();
                repaint();
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                } else if (e.getKeyCode() == KeyEvent.VK_C && !selectionComplete && mousePoint != null) {
                    // C键复制色值（未选区时）
                    copyColorToClipboard();
                }
            }
        });
    }

    private void finishAnnotation() {
        if (annotationStart == null || annotationEnd == null || captureRect == null) {
            return;
        }
        
        if (currentMode == AnnotationMode.ARROW) {
            annotations.add(new ArrowAnnotation(annotationStart, annotationEnd, annotationColor));
        } else if (currentMode == AnnotationMode.MOSAIC) {
            int x = Math.min(annotationStart.x, annotationEnd.x);
            int y = Math.min(annotationStart.y, annotationEnd.y);
            int w = Math.abs(annotationEnd.x - annotationStart.x);
            int h = Math.abs(annotationEnd.y - annotationStart.y);
            if (w > 5 && h > 5) {
                annotations.add(new MosaicAnnotation(new Rectangle(x, y, w, h)));
            }
        }
        
        annotationStart = null;
        annotationEnd = null;
        repaint();
    }

    private void finishSelection() {
        if (startPoint == null || endPoint == null) {
            return;
        }

        int x = Math.min(startPoint.x, endPoint.x);
        int y = Math.min(startPoint.y, endPoint.y);
        int width = Math.abs(endPoint.x - startPoint.x);
        int height = Math.abs(endPoint.y - startPoint.y);

        if (width < 5 || height < 5) {
            return;
        }

        captureRect = new Rectangle(x, y, width, height);
        capturedImage = screenImage.getSubimage(x, y, width, height);
        selectionComplete = true;

        // 显示工具栏
        showToolBar();
        repaint();
    }

    private BufferedImage getAnnotatedImage() {
        if (capturedImage == null || captureRect == null) return capturedImage;
        
        BufferedImage result = new BufferedImage(capturedImage.getWidth(), capturedImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = result.createGraphics();
        g2d.drawImage(capturedImage, 0, 0, null);
        
        // 绘制标注（需要转换坐标）
        for (Annotation annotation : annotations) {
            annotation.draw(g2d, -captureRect.x, -captureRect.y, screenImage);
        }
        
        g2d.dispose();
        return result;
    }

    private void pinToTop() {
        if (capturedImage == null) return;

        BufferedImage finalImage = getAnnotatedImage();
        dispose();
        SwingUtilities.invokeLater(() -> {
            ImageTopWindow topWindow = new ImageTopWindow(finalImage, "截图 " + 
                    new SimpleDateFormat("HH:mm:ss").format(new Date()));
            topWindow.setVisible(true);
        });
    }

    private void copyToClipboard() {
        if (capturedImage == null) return;

        BufferedImage finalImage = getAnnotatedImage();
        TransferableImage transferable = new TransferableImage(finalImage);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transferable, null);

        dispose();
        JOptionPane.showMessageDialog(null, "截图已复制到剪贴板", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void copyColorToClipboard() {
        if (mousePoint == null || screenImage == null) return;

        int mx = mousePoint.x;
        int my = mousePoint.y;

        if (mx < 0 || my < 0 || mx >= screenImage.getWidth() || my >= screenImage.getHeight()) {
            return;
        }

        int rgb = screenImage.getRGB(mx, my);
        Color color = new Color(rgb);
        String hexColor = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());

        // 复制到剪贴板
        java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(hexColor);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);

        // 退出截图并显示提示
        dispose();
        showColorCopiedTip(hexColor);
    }

    private void showColorCopiedTip(String hexColor) {
        // 在屏幕中间显示提示
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog();
            dialog.setUndecorated(true);
            dialog.setAlwaysOnTop(true);
            
            JLabel label = new JLabel("  " + hexColor + " 已复制  ", SwingConstants.CENTER);
            label.setFont(new Font("微软雅黑", Font.PLAIN, 16));
            label.setForeground(Color.WHITE);
            label.setBackground(new Color(50, 50, 50, 220));
            label.setOpaque(true);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(100, 100, 100), 1),
                    BorderFactory.createEmptyBorder(10, 20, 10, 20)
            ));
            
            dialog.add(label);
            dialog.pack();
            dialog.setLocationRelativeTo(null); // 屏幕中间
            dialog.setVisible(true);

            Timer closeTimer = new Timer(1500, evt -> dialog.dispose());
            closeTimer.setRepeats(false);
            closeTimer.start();
        });
    }

    private void saveImage() {
        if (capturedImage == null) return;

        BufferedImage finalImage = getAnnotatedImage();
        dispose();

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
                ImageIO.write(finalImage, "PNG", file);
                JOptionPane.showMessageDialog(null,
                        "截图已保存到:\n" + file.getAbsolutePath(),
                        "保存成功",
                        JOptionPane.INFORMATION_MESSAGE);
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
     * 截图面板 - 绘制半透明遮罩和选区
     */
    private class CapturePanel extends JPanel {

        public CapturePanel() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g.create();

            // 绘制原始屏幕截图（1:1像素绘制，不缩放）
            g2d.drawImage(screenImage, 0, 0, null);

            // 绘制半透明遮罩
            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            // 绘制选区
            if (startPoint != null && endPoint != null) {
                int x = Math.min(startPoint.x, endPoint.x);
                int y = Math.min(startPoint.y, endPoint.y);
                int width = Math.abs(endPoint.x - startPoint.x);
                int height = Math.abs(endPoint.y - startPoint.y);

                if (width > 0 && height > 0) {
                    // 使用clip方式显示选区原图，避免getSubimage可能的质量损失
                    Shape oldClip = g2d.getClip();
                    g2d.setClip(x, y, width, height);
                    g2d.drawImage(screenImage, 0, 0, null);
                    g2d.setClip(oldClip);

                    // 绘制选区边框
                    g2d.setColor(new Color(0, 174, 255));
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawRect(x, y, width, height);

                    // 绘制八个调整点（选区完成后）
                    if (selectionComplete) {
                        drawResizeHandles(g2d, x, y, width, height);
                    }

                    // 绘制尺寸信息背景
                    String sizeInfo = width + " × " + height;
                    g2d.setFont(new Font("微软雅黑", Font.PLAIN, 12));
                    FontMetrics fm = g2d.getFontMetrics();
                    int textWidth = fm.stringWidth(sizeInfo) + 10;
                    int textHeight = fm.getHeight() + 4;

                    int textX = x;
                    int textY = y > 25 ? y - textHeight - 2 : y + height + 2;

                    // 背景
                    g2d.setColor(new Color(0, 0, 0, 180));
                    g2d.fillRoundRect(textX, textY, textWidth, textHeight, 4, 4);

                    // 文字
                    g2d.setColor(Color.WHITE);
                    g2d.drawString(sizeInfo, textX + 5, textY + fm.getAscent() + 2);
                }
            }

            // 绘制已完成的标注
            for (Annotation annotation : annotations) {
                annotation.draw(g2d, 0, 0, screenImage);
            }
            
            // 绘制正在进行的标注预览
            if (isAnnotating && annotationStart != null && annotationEnd != null) {
                if (currentMode == AnnotationMode.ARROW) {
                    drawArrowPreview(g2d, annotationStart, annotationEnd);
                } else if (currentMode == AnnotationMode.MOSAIC) {
                    drawMosaicPreview(g2d, annotationStart, annotationEnd);
                }
            }
            
            // 绘制文字放置预览
            if (currentMode == AnnotationMode.TEXT && pendingText != null && mousePoint != null && captureRect != null && captureRect.contains(mousePoint)) {
                drawTextPreview(g2d, mousePoint, pendingText);
            }
            
            // 绘制序号放置预览
            if (currentMode == AnnotationMode.NUMBER && mousePoint != null && captureRect != null && captureRect.contains(mousePoint)) {
                drawNumberPreview(g2d, mousePoint, nextNumber);
            }

            // 绘制提示信息
            if (!selectionComplete) {
                drawHelpText(g2d);
            }

            // 绘制取色器（鼠标位置的放大镜和颜色信息）
            if (mousePoint != null && !selectionComplete) {
                drawColorPicker(g2d, mousePoint.x, mousePoint.y);
            }

            g2d.dispose();
        }
        
        private void drawArrowPreview(Graphics2D g2d, Point start, Point end) {
            g2d.setColor(annotationColor);
            g2d.setStroke(new BasicStroke(2));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 绘制线段
            g2d.drawLine(start.x, start.y, end.x, end.y);
            
            // 绘制箭头
            double angle = Math.atan2(end.y - start.y, end.x - start.x);
            int arrowSize = 12;
            int x1 = (int) (end.x - arrowSize * Math.cos(angle - Math.PI / 6));
            int y1 = (int) (end.y - arrowSize * Math.sin(angle - Math.PI / 6));
            int x2 = (int) (end.x - arrowSize * Math.cos(angle + Math.PI / 6));
            int y2 = (int) (end.y - arrowSize * Math.sin(angle + Math.PI / 6));
            
            int[] xPoints = {end.x, x1, x2};
            int[] yPoints = {end.y, y1, y2};
            g2d.fillPolygon(xPoints, yPoints, 3);
        }
        
        private void drawMosaicPreview(Graphics2D g2d, Point start, Point end) {
            int x = Math.min(start.x, end.x);
            int y = Math.min(start.y, end.y);
            int w = Math.abs(end.x - start.x);
            int h = Math.abs(end.y - start.y);
            
            if (w > 5 && h > 5) {
                // 绘制马赛克预览边框
                g2d.setColor(new Color(255, 0, 0, 128));
                g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{5}, 0));
                g2d.drawRect(x, y, w, h);
            }
        }
        
        private void drawTextPreview(Graphics2D g2d, Point pos, String text) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setFont(new Font("微软雅黑", Font.BOLD, 16));
            
            // 半透明预览
            g2d.setColor(new Color(0, 0, 0, 100));
            g2d.drawString(text, pos.x + 1, pos.y + 1);
            g2d.setColor(new Color(annotationColor.getRed(), annotationColor.getGreen(), annotationColor.getBlue(), 180));
            g2d.drawString(text, pos.x, pos.y);
            
            // 提示文字
            g2d.setFont(new Font("微软雅黑", Font.PLAIN, 10));
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.drawString("点击放置文字", pos.x, pos.y + 20);
        }
        
        private void drawNumberPreview(Graphics2D g2d, Point pos, int number) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int size = 24;
            int cx = pos.x;
            int cy = pos.y;
            
            // 半透明圆形背景
            g2d.setColor(new Color(annotationColor.getRed(), annotationColor.getGreen(), annotationColor.getBlue(), 150));
            g2d.fillOval(cx - size / 2, cy - size / 2, size, size);
            
            // 数字
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            FontMetrics fm = g2d.getFontMetrics();
            String numStr = String.valueOf(number);
            int textWidth = fm.stringWidth(numStr);
            g2d.drawString(numStr, cx - textWidth / 2, cy + fm.getAscent() / 2 - 1);
            
            // 提示文字
            g2d.setFont(new Font("微软雅黑", Font.PLAIN, 10));
            g2d.setColor(new Color(255, 255, 255, 200));
            g2d.drawString("点击放置序号", cx - 25, cy + size / 2 + 15);
        }

        private void drawColorPicker(Graphics2D g2d, int mx, int my) {
            // 确保坐标在屏幕范围内
            if (mx < 0 || my < 0 || mx >= screenImage.getWidth() || my >= screenImage.getHeight()) {
                return;
            }

            // 获取当前像素颜色
            int rgb = screenImage.getRGB(mx, my);
            Color color = new Color(rgb);
            String hexColor = String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
            String rgbColor = String.format("RGB(%d,%d,%d)", color.getRed(), color.getGreen(), color.getBlue());
            String posInfo = String.format("(%d, %d)", mx, my);

            // 放大镜参数
            int magnifierSize = 120; // 放大镜大小
            int gridSize = 11; // 显示的像素网格数（奇数，中心为当前像素）
            int cellSize = magnifierSize / gridSize;
            int halfGrid = gridSize / 2;

            // 计算放大镜位置（在鼠标右下方，避免遮挡）
            int magX = mx + 20;
            int magY = my + 20;

            // 如果超出屏幕边界，调整位置
            if (magX + magnifierSize + 10 > getWidth()) {
                magX = mx - magnifierSize - 20;
            }
            if (magY + magnifierSize + 60 > getHeight()) {
                magY = my - magnifierSize - 80;
            }

            // 绘制放大镜背景
            g2d.setColor(new Color(30, 30, 30, 230));
            g2d.fillRoundRect(magX - 5, magY - 5, magnifierSize + 10, magnifierSize + 78, 8, 8);
            g2d.setColor(new Color(100, 100, 100));
            g2d.setStroke(new BasicStroke(1));
            g2d.drawRoundRect(magX - 5, magY - 5, magnifierSize + 10, magnifierSize + 78, 8, 8);

            // 绘制放大的像素网格
            for (int dy = -halfGrid; dy <= halfGrid; dy++) {
                for (int dx = -halfGrid; dx <= halfGrid; dx++) {
                    int px = mx + dx;
                    int py = my + dy;

                    // 获取像素颜色
                    Color pixelColor;
                    if (px >= 0 && py >= 0 && px < screenImage.getWidth() && py < screenImage.getHeight()) {
                        pixelColor = new Color(screenImage.getRGB(px, py));
                    } else {
                        pixelColor = Color.BLACK;
                    }

                    // 绘制像素格子
                    int cellX = magX + (dx + halfGrid) * cellSize;
                    int cellY = magY + (dy + halfGrid) * cellSize;
                    g2d.setColor(pixelColor);
                    g2d.fillRect(cellX, cellY, cellSize, cellSize);

                    // 绘制网格线
                    g2d.setColor(new Color(60, 60, 60));
                    g2d.drawRect(cellX, cellY, cellSize, cellSize);
                }
            }

            // 绘制中心十字准星
            int centerX = magX + halfGrid * cellSize;
            int centerY = magY + halfGrid * cellSize;
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRect(centerX, centerY, cellSize, cellSize);

            // 绘制颜色信息
            g2d.setFont(new Font("Consolas", Font.PLAIN, 12));
            int infoY = magY + magnifierSize + 5;

            // 颜色预览块
            g2d.setColor(color);
            g2d.fillRect(magX, infoY, 20, 20);
            g2d.setColor(Color.WHITE);
            g2d.drawRect(magX, infoY, 20, 20);

            // 十六进制颜色值
            g2d.setColor(Color.WHITE);
            g2d.drawString(hexColor, magX + 25, infoY + 14);

            // RGB值和坐标
            g2d.setFont(new Font("Consolas", Font.PLAIN, 10));
            g2d.setColor(new Color(180, 180, 180));
            g2d.drawString(rgbColor, magX, infoY + 32);
            g2d.drawString(posInfo, magX, infoY + 45);

            // 提示：C键
            g2d.setFont(new Font("微软雅黑", Font.PLAIN, 10));
            g2d.setColor(new Color(120, 120, 120));
            g2d.drawString("按C键复制色值", magX, infoY + 58);
        }

        private void drawResizeHandles(Graphics2D g2d, int x, int y, int width, int height) {
            int handleSize = 8;
            g2d.setColor(new Color(0, 174, 255));
            g2d.setStroke(new BasicStroke(1));

            // 四个角
            drawHandle(g2d, x - handleSize / 2, y - handleSize / 2, handleSize);
            drawHandle(g2d, x + width - handleSize / 2, y - handleSize / 2, handleSize);
            drawHandle(g2d, x - handleSize / 2, y + height - handleSize / 2, handleSize);
            drawHandle(g2d, x + width - handleSize / 2, y + height - handleSize / 2, handleSize);

            // 四条边中点
            drawHandle(g2d, x + width / 2 - handleSize / 2, y - handleSize / 2, handleSize);
            drawHandle(g2d, x + width / 2 - handleSize / 2, y + height - handleSize / 2, handleSize);
            drawHandle(g2d, x - handleSize / 2, y + height / 2 - handleSize / 2, handleSize);
            drawHandle(g2d, x + width - handleSize / 2, y + height / 2 - handleSize / 2, handleSize);
        }

        private void drawHandle(Graphics2D g2d, int x, int y, int size) {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(x, y, size, size);
            g2d.setColor(new Color(0, 174, 255));
            g2d.drawRect(x, y, size, size);
        }

        private void drawHelpText(Graphics2D g2d) {
            String helpText = "拖动鼠标选择截图区域 | 右键取消 | ESC退出";
            g2d.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(helpText) + 20;
            int textHeight = fm.getHeight() + 10;

            int x = 10;
            int y = 10;

            // 背景
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRoundRect(x, y, textWidth, textHeight, 6, 6);

            // 文字
            g2d.setColor(Color.WHITE);
            g2d.drawString(helpText, x + 10, y + fm.getAscent() + 5);
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

    /**
     * 标注基类
     */
    private interface Annotation {
        void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage);
        boolean contains(Point p);
        void move(int dx, int dy);
    }

    /**
     * 文字标注
     */
    private static class TextAnnotation implements Annotation {
        private int x, y;
        private final String text;
        private final Color color;
        private Rectangle bounds;

        public TextAnnotation(int x, int y, String text, Color color) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            updateBounds();
        }
        
        private void updateBounds() {
            // 估算文字边界（实际绘制时会更精确）
            int width = text.length() * 10 + 10;
            int height = 20;
            bounds = new Rectangle(x - 2, y - height + 4, width, height);
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setFont(new Font("微软雅黑", Font.BOLD, 16));
            FontMetrics fm = g2d.getFontMetrics();
            
            // 更新精确边界
            int width = fm.stringWidth(text) + 4;
            int height = fm.getHeight();
            bounds = new Rectangle(x - 2, y - fm.getAscent(), width, height);
            
            int drawX = x + offsetX;
            int drawY = y + offsetY;
            
            // 绘制文字阴影
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.drawString(text, drawX + 1, drawY + 1);
            
            // 绘制文字
            g2d.setColor(color);
            g2d.drawString(text, drawX, drawY);
        }
        
        @Override
        public boolean contains(Point p) {
            return bounds != null && bounds.contains(p);
        }
        
        @Override
        public void move(int dx, int dy) {
            this.x += dx;
            this.y += dy;
            updateBounds();
        }
    }

    /**
     * 序号标注
     */
    private static class NumberAnnotation implements Annotation {
        private int x, y;
        private final int number;
        private final Color color;
        private static final int SIZE = 24;

        public NumberAnnotation(int x, int y, int number, Color color) {
            this.x = x;
            this.y = y;
            this.number = number;
            this.color = color;
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int cx = x + offsetX;
            int cy = y + offsetY;
            
            // 圆形背景
            g2d.setColor(color);
            g2d.fillOval(cx - SIZE / 2, cy - SIZE / 2, SIZE, SIZE);
            
            // 白色边框
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(1));
            g2d.drawOval(cx - SIZE / 2, cy - SIZE / 2, SIZE, SIZE);
            
            // 数字
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            FontMetrics fm = g2d.getFontMetrics();
            String numStr = String.valueOf(number);
            int textWidth = fm.stringWidth(numStr);
            g2d.drawString(numStr, cx - textWidth / 2, cy + fm.getAscent() / 2 - 1);
        }
        
        @Override
        public boolean contains(Point p) {
            double dist = Math.sqrt((p.x - x) * (p.x - x) + (p.y - y) * (p.y - y));
            return dist <= SIZE / 2 + 3; // 稍微增大点击范围
        }
        
        @Override
        public void move(int dx, int dy) {
            this.x += dx;
            this.y += dy;
        }
    }

    /**
     * 箭头标注
     */
    private static class ArrowAnnotation implements Annotation {
        private Point start, end;
        private final Color color;

        public ArrowAnnotation(Point start, Point end, Color color) {
            this.start = new Point(start);
            this.end = new Point(end);
            this.color = color;
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2));
            
            int x1 = start.x + offsetX;
            int y1 = start.y + offsetY;
            int x2 = end.x + offsetX;
            int y2 = end.y + offsetY;
            
            // 绘制线段
            g2d.drawLine(x1, y1, x2, y2);
            
            // 绘制箭头
            double angle = Math.atan2(y2 - y1, x2 - x1);
            int arrowSize = 12;
            int ax1 = (int) (x2 - arrowSize * Math.cos(angle - Math.PI / 6));
            int ay1 = (int) (y2 - arrowSize * Math.sin(angle - Math.PI / 6));
            int ax2 = (int) (x2 - arrowSize * Math.cos(angle + Math.PI / 6));
            int ay2 = (int) (y2 - arrowSize * Math.sin(angle + Math.PI / 6));
            
            int[] xPoints = {x2, ax1, ax2};
            int[] yPoints = {y2, ay1, ay2};
            g2d.fillPolygon(xPoints, yPoints, 3);
        }
        
        @Override
        public boolean contains(Point p) {
            // 检测点是否在箭头线段附近（容差15像素，更容易点击）
            double dist = pointToLineDistance(p.x, p.y, start.x, start.y, end.x, end.y);
            return dist < 15;
        }
        
        private double pointToLineDistance(int px, int py, int x1, int y1, int x2, int y2) {
            double dx = x2 - x1;
            double dy = y2 - y1;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len == 0) return Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
            double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / (len * len)));
            double projX = x1 + t * dx;
            double projY = y1 + t * dy;
            return Math.sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY));
        }
        
        @Override
        public void move(int dx, int dy) {
            start.x += dx;
            start.y += dy;
            end.x += dx;
            end.y += dy;
        }
    }

    /**
     * 马赛克标注
     */
    private static class MosaicAnnotation implements Annotation {
        private Rectangle rect;
        private static final int BLOCK_SIZE = 10;

        public MosaicAnnotation(Rectangle rect) {
            this.rect = new Rectangle(rect);
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            int drawX = rect.x + offsetX;
            int drawY = rect.y + offsetY;
            
            // 对区域进行马赛克处理
            for (int by = 0; by < rect.height; by += BLOCK_SIZE) {
                for (int bx = 0; bx < rect.width; bx += BLOCK_SIZE) {
                    int imgX = rect.x + bx;
                    int imgY = rect.y + by;
                    
                    // 确保在图片范围内
                    if (imgX >= 0 && imgY >= 0 && imgX < screenImage.getWidth() && imgY < screenImage.getHeight()) {
                        // 获取块的平均颜色
                        int rgb = screenImage.getRGB(imgX, imgY);
                        Color blockColor = new Color(rgb);
                        
                        g2d.setColor(blockColor);
                        int blockW = Math.min(BLOCK_SIZE, rect.width - bx);
                        int blockH = Math.min(BLOCK_SIZE, rect.height - by);
                        g2d.fillRect(drawX + bx, drawY + by, blockW, blockH);
                    }
                }
            }
        }
        
        @Override
        public boolean contains(Point p) {
            return rect.contains(p);
        }
        
        @Override
        public void move(int dx, int dy) {
            rect.x += dx;
            rect.y += dy;
        }
    }
}
