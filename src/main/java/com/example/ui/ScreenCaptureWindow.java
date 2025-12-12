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
    private enum AnnotationMode { NONE, TEXT, ARROW, MOSAIC, NUMBER, RECT, CIRCLE, PEN, HIGHLIGHT, WATERMARK }
    private enum ArrowStyle { ARROW, LINE, WAVY, DASHED, DOUBLE_ARROW } // 箭头样式
    private AnnotationMode currentMode = AnnotationMode.NONE;
    private ArrowStyle currentArrowStyle = ArrowStyle.ARROW; // 当前箭头样式
    private int currentArrowStroke = 2; // 当前箭头粗细
    private static final int[] STROKE_OPTIONS = {1, 2, 3, 4, 5}; // 粗细选项
    private int currentTextSize = 16; // 当前文字大小
    private static final int[] TEXT_SIZE_OPTIONS = {12, 14, 16, 18, 20, 24, 28, 32}; // 文字大小选项
    private java.util.List<Annotation> annotations = new java.util.ArrayList<>();
    private Point annotationStart;
    private Point annotationEnd;
    private boolean isAnnotating = false;
    private Color annotationColor = Color.RED;
    private String pendingText = null; // 待放置的文字
    private Annotation selectedAnnotation = null; // 当前选中的标注
    private Point dragStart = null; // 拖动起始点
    private boolean isDraggingAnnotation = false; // 是否正在拖动标注
    private int activeHandle = -1; // 当前激活的控制手柄 (-1=无, 0=旋转, 1=缩放起点, 2=缩放终点)
    private double lastRotateAngle = 0; // 上一次旋转角度
    private int nextNumber = 1; // 下一个序号
    private java.util.List<Point> currentPenPath = new java.util.ArrayList<>(); // 当前画笔路径
    private java.util.Map<AnnotationMode, JButton> annotationButtons = new java.util.HashMap<>(); // 标注按钮映射
    private JPanel colorPanel; // 颜色选择面板
    private JPanel stylePanel; // 样式选择面板
    private JPanel strokePanel; // 粗细选择面板
    private JPanel arrowOptionsPanel; // 箭头选项综合面板
    private JPanel textOptionsPanel; // 文字选项综合面板
    private static final Color[] PRESET_COLORS = {
        new Color(255, 0, 0),      // 红色
        new Color(255, 165, 0),    // 橙色
        new Color(255, 255, 0),    // 黄色
        new Color(0, 255, 0),      // 绿色
        new Color(0, 191, 255),    // 天蓝色
        new Color(0, 0, 255),      // 蓝色
        new Color(128, 0, 128),    // 紫色
        new Color(255, 255, 255),  // 白色
        new Color(0, 0, 0)         // 黑色
    };

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
        textButton.addActionListener(e -> {
            setAnnotationMode(AnnotationMode.TEXT);
            showTextOptionsPanel(textButton);
        });
        annotationButtons.put(AnnotationMode.TEXT, textButton);
        
        JButton arrowButton = createToolButton("→ 箭头", "添加箭头标注");
        arrowButton.addActionListener(e -> {
            setAnnotationMode(AnnotationMode.ARROW);
            showArrowOptionsPanel(arrowButton);
        });
        annotationButtons.put(AnnotationMode.ARROW, arrowButton);
        
        JButton mosaicButton = createToolButton("▦ 马赛克", "添加马赛克");
        mosaicButton.addActionListener(e -> setAnnotationMode(AnnotationMode.MOSAIC));
        annotationButtons.put(AnnotationMode.MOSAIC, mosaicButton);
        
        JButton numberButton = createToolButton("① 序号", "添加序号标注");
        numberButton.addActionListener(e -> setAnnotationMode(AnnotationMode.NUMBER));
        annotationButtons.put(AnnotationMode.NUMBER, numberButton);
        
        JButton rectButton = createToolButton("□ 矩形", "添加矩形框");
        rectButton.addActionListener(e -> setAnnotationMode(AnnotationMode.RECT));
        annotationButtons.put(AnnotationMode.RECT, rectButton);
        
        JButton circleButton = createToolButton("○ 圆形", "添加圆形框");
        circleButton.addActionListener(e -> setAnnotationMode(AnnotationMode.CIRCLE));
        annotationButtons.put(AnnotationMode.CIRCLE, circleButton);
        
        JButton penButton = createToolButton("✎ 画笔", "自由绘制");
        penButton.addActionListener(e -> setAnnotationMode(AnnotationMode.PEN));
        annotationButtons.put(AnnotationMode.PEN, penButton);
        
        JButton highlightButton = createToolButton("▬ 高亮", "添加高亮标记");
        highlightButton.addActionListener(e -> setAnnotationMode(AnnotationMode.HIGHLIGHT));
        annotationButtons.put(AnnotationMode.HIGHLIGHT, highlightButton);
        
        JButton watermarkButton = createToolButton("㊊ 水印", "添加水印");
        watermarkButton.addActionListener(e -> setAnnotationMode(AnnotationMode.WATERMARK));
        annotationButtons.put(AnnotationMode.WATERMARK, watermarkButton);

        toolBar.add(textButton);
        toolBar.add(arrowButton);
        toolBar.add(mosaicButton);
        toolBar.add(numberButton);
        toolBar.add(rectButton);
        toolBar.add(circleButton);
        toolBar.add(penButton);
        toolBar.add(highlightButton);
        toolBar.add(watermarkButton);
        toolBar.add(createSeparator());
        
        JButton translateButton = createToolButton("译 翻译", "翻译选区文字");
        translateButton.addActionListener(e -> translateSelectedArea());
        toolBar.add(translateButton);
        
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

        // 鼠标悬停效果（需要考虑选中状态）
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!isButtonSelected(button)) {
                    button.setBackground(new Color(80, 80, 80));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!isButtonSelected(button)) {
                    button.setBackground(new Color(60, 60, 60));
                }
            }
        });

        return button;
    }
    
    private boolean isButtonSelected(JButton button) {
        for (java.util.Map.Entry<AnnotationMode, JButton> entry : annotationButtons.entrySet()) {
            if (entry.getValue() == button && entry.getKey() == currentMode) {
                return true;
            }
        }
        return false;
    }

    private JSeparator createSeparator() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 24));
        separator.setForeground(new Color(80, 80, 80));
        return separator;
    }
    
    private void createColorPanel() {
        colorPanel = new JPanel();
        colorPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
        colorPanel.setBackground(new Color(50, 50, 50));
        colorPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80), 1),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        colorPanel.setVisible(false);
        
        for (Color color : PRESET_COLORS) {
            JButton colorBtn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int size = 18;
                    int x = (getWidth() - size) / 2;
                    int y = (getHeight() - size) / 2;
                    g2d.setColor(color);
                    g2d.fillOval(x, y, size, size);
                    // 选中状态边框
                    if (annotationColor.equals(color)) {
                        g2d.setColor(Color.WHITE);
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawOval(x - 1, y - 1, size + 2, size + 2);
                    } else {
                        g2d.setColor(new Color(100, 100, 100));
                        g2d.setStroke(new BasicStroke(1));
                        g2d.drawOval(x, y, size, size);
                    }
                }
            };
            colorBtn.setPreferredSize(new Dimension(26, 26));
            colorBtn.setBackground(new Color(50, 50, 50));
            colorBtn.setBorder(BorderFactory.createEmptyBorder());
            colorBtn.setFocusPainted(false);
            colorBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            colorBtn.setToolTipText(String.format("RGB(%d, %d, %d)", color.getRed(), color.getGreen(), color.getBlue()));
            
            colorBtn.addActionListener(e -> {
                annotationColor = color;
                // 如果有选中的标注且支持颜色修改，同时修改它的颜色
                if (selectedAnnotation != null && selectedAnnotation.supportsColorChange()) {
                    selectedAnnotation.setColor(color);
                }
                // 不隐藏颜色面板，刷新显示选中状态
                colorPanel.repaint();
                repaint();
            });
            
            colorPanel.add(colorBtn);
        }
        
        capturePanel.add(colorPanel);
    }
    
    private void createStylePanel() {
        stylePanel = new JPanel();
        stylePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
        stylePanel.setBackground(new Color(50, 50, 50));
        stylePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80), 1),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        stylePanel.setVisible(false);
        
        String[][] styles = {
            {"→", "ARROW", "普通箭头"},
            {"—", "LINE", "直线"},
            {"~", "WAVY", "波浪线"},
            {"...", "DASHED", "虚线箭头"},
            {"←→", "DOUBLE_ARROW", "双向箭头"}
        };
        
        for (String[] styleInfo : styles) {
            String icon = styleInfo[0];
            String styleName = styleInfo[1];
            String tooltip = styleInfo[2];
            ArrowStyle arrowStyle = ArrowStyle.valueOf(styleName);
            
            JButton styleBtn = new JButton(icon) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (currentArrowStyle == arrowStyle) {
                        Graphics2D g2d = (Graphics2D) g;
                        g2d.setColor(new Color(0, 120, 215));
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                    }
                }
            };
            styleBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            styleBtn.setPreferredSize(new Dimension(32, 26));
            styleBtn.setBackground(new Color(60, 60, 60));
            styleBtn.setForeground(Color.WHITE);
            styleBtn.setBorder(BorderFactory.createEmptyBorder());
            styleBtn.setFocusPainted(false);
            styleBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            styleBtn.setToolTipText(tooltip);
            
            styleBtn.addActionListener(e -> {
                currentArrowStyle = arrowStyle;
                stylePanel.repaint();
                repaint();
            });
            
            stylePanel.add(styleBtn);
        }
        
        capturePanel.add(stylePanel);
    }
    
    private void showStylePanel(JButton button) {
        if (stylePanel == null) {
            createStylePanel();
        }
        
        Dimension panelSize = stylePanel.getPreferredSize();
        Point btnLoc = button.getLocationOnScreen();
        Point panelLoc = capturePanel.getLocationOnScreen();
        int x = btnLoc.x - panelLoc.x;
        
        // 样式面板显示在颜色面板下方
        int colorPanelHeight = (colorPanel != null) ? colorPanel.getPreferredSize().height : 0;
        int y = btnLoc.y - panelLoc.y - panelSize.height - colorPanelHeight - 10;
        
        if (y < 0) {
            y = btnLoc.y - panelLoc.y + button.getHeight() + colorPanelHeight + 10;
        }
        
        stylePanel.setBounds(x, y, panelSize.width, panelSize.height);
        stylePanel.setVisible(true);
        stylePanel.repaint();
    }
    
    private void hideStylePanel() {
        if (stylePanel != null && stylePanel.isVisible()) {
            stylePanel.setVisible(false);
        }
    }
    
    private void createStrokePanel() {
        strokePanel = new JPanel();
        strokePanel.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4));
        strokePanel.setBackground(new Color(50, 50, 50));
        strokePanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(80, 80, 80), 1),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        strokePanel.setVisible(false);
        
        for (int stroke : STROKE_OPTIONS) {
            JButton strokeBtn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // 绘制粗细示意线
                    g2d.setColor(Color.WHITE);
                    g2d.setStroke(new BasicStroke(stroke));
                    int y = getHeight() / 2;
                    g2d.drawLine(4, y, getWidth() - 4, y);
                    // 选中状态边框
                    if (currentArrowStroke == stroke) {
                        g2d.setColor(new Color(0, 120, 215));
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                    }
                }
            };
            strokeBtn.setPreferredSize(new Dimension(36, 24));
            strokeBtn.setBackground(new Color(60, 60, 60));
            strokeBtn.setBorder(BorderFactory.createEmptyBorder());
            strokeBtn.setFocusPainted(false);
            strokeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            strokeBtn.setToolTipText("粗细: " + stroke + "px");
            
            strokeBtn.addActionListener(e -> {
                currentArrowStroke = stroke;
                strokePanel.repaint();
                repaint();
            });
            
            strokePanel.add(strokeBtn);
        }
        
        capturePanel.add(strokePanel);
    }
    
    private void showStrokePanel(JButton button) {
        if (strokePanel == null) {
            createStrokePanel();
        }
        
        Dimension panelSize = strokePanel.getPreferredSize();
        Point btnLoc = button.getLocationOnScreen();
        Point panelLoc = capturePanel.getLocationOnScreen();
        int x = btnLoc.x - panelLoc.x;
        
        // 粗细面板显示在样式面板上方
        int colorPanelHeight = (colorPanel != null) ? colorPanel.getPreferredSize().height : 0;
        int stylePanelHeight = (stylePanel != null) ? stylePanel.getPreferredSize().height : 0;
        int y = btnLoc.y - panelLoc.y - panelSize.height - colorPanelHeight - stylePanelHeight - 15;
        
        if (y < 0) {
            y = btnLoc.y - panelLoc.y + button.getHeight() + colorPanelHeight + stylePanelHeight + 15;
        }
        
        strokePanel.setBounds(x, y, panelSize.width, panelSize.height);
        strokePanel.setVisible(true);
        strokePanel.repaint();
    }
    
    private void hideStrokePanel() {
        if (strokePanel != null && strokePanel.isVisible()) {
            strokePanel.setVisible(false);
        }
    }
    
    private void createArrowOptionsPanel() {
        arrowOptionsPanel = new JPanel();
        arrowOptionsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        arrowOptionsPanel.setBackground(new Color(45, 45, 45));
        arrowOptionsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 70, 70), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        arrowOptionsPanel.setVisible(false);
        
        // 粗细滑块区域
        JPanel strokeSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        strokeSection.setBackground(new Color(45, 45, 45));
        
        // 自定义粗细滑块（支持颜色变化）
        JPanel customSlider = new JPanel() {
            private int sliderValue = currentArrowStroke;
            private boolean isDragging = false;
            
            {
                setPreferredSize(new Dimension(100, 24));
                setBackground(new Color(45, 45, 45));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                
                addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent e) {
                        isDragging = true;
                        updateValue(e.getX());
                    }
                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent e) {
                        isDragging = false;
                    }
                });
                
                addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(java.awt.event.MouseEvent e) {
                        if (isDragging) {
                            updateValue(e.getX());
                        }
                    }
                });
            }
            
            private void updateValue(int x) {
                int trackStart = 20;
                int trackEnd = getWidth() - 20;
                int trackWidth = trackEnd - trackStart;
                
                double ratio = (double)(x - trackStart) / trackWidth;
                ratio = Math.max(0, Math.min(1, ratio));
                sliderValue = 1 + (int)(ratio * 4);
                currentArrowStroke = sliderValue;
                repaint();
                ScreenCaptureWindow.this.repaint();
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int h = getHeight();
                int w = getWidth();
                int trackStart = 20;
                int trackEnd = w - 20;
                int trackWidth = trackEnd - trackStart;
                
                // 左侧小圆点（显示当前颜色）
                g2d.setColor(annotationColor);
                g2d.fillOval(4, (h - 8) / 2, 8, 8);
                
                // 右侧大圆点（显示当前颜色）
                g2d.setColor(annotationColor);
                g2d.fillOval(w - 16, (h - 14) / 2, 14, 14);
                
                // 轨道背景
                g2d.setColor(new Color(80, 80, 80));
                g2d.fillRoundRect(trackStart, h / 2 - 2, trackWidth, 4, 4, 4);
                
                // 计算滑块位置
                double ratio = (sliderValue - 1) / 4.0;
                int thumbX = trackStart + (int)(ratio * trackWidth);
                
                // 已选择部分轨道（使用当前颜色）
                g2d.setColor(annotationColor);
                g2d.fillRoundRect(trackStart, h / 2 - 2, thumbX - trackStart, 4, 4, 4);
                
                // 滑块圆形（使用当前颜色）
                g2d.setColor(annotationColor);
                g2d.fillOval(thumbX - 6, h / 2 - 6, 12, 12);
                g2d.setColor(Color.WHITE);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawOval(thumbX - 6, h / 2 - 6, 12, 12);
            }
        };
        strokeSection.add(customSlider);
        
        arrowOptionsPanel.add(strokeSection);
        
        // 分隔线
        JSeparator sep1 = new JSeparator(SwingConstants.VERTICAL);
        sep1.setPreferredSize(new Dimension(1, 20));
        sep1.setForeground(new Color(80, 80, 80));
        arrowOptionsPanel.add(sep1);
        
        // 样式选择按钮（带下拉箭头）
        JButton styleButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int y = getHeight() / 2;
                g2d.setColor(annotationColor); // 使用当前选中的颜色
                
                // 根据当前样式绘制预览
                switch (currentArrowStyle) {
                    case LINE:
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawLine(8, y, 32, y);
                        break;
                    case WAVY:
                        g2d.setStroke(new BasicStroke(2));
                        for (int i = 0; i < 24; i += 6) {
                            int yOffset = (i / 6) % 2 == 0 ? -3 : 3;
                            g2d.drawLine(8 + i, y + (i == 0 ? 0 : ((i / 6 - 1) % 2 == 0 ? -3 : 3)), 
                                        8 + i + 6, y + yOffset);
                        }
                        break;
                    case DASHED:
                        g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{4, 2}, 0));
                        g2d.drawLine(8, y, 28, y);
                        g2d.setStroke(new BasicStroke(2));
                        int[] xd = {32, 26, 26};
                        int[] yd = {y, y - 4, y + 4};
                        g2d.fillPolygon(xd, yd, 3);
                        break;
                    case DOUBLE_ARROW:
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawLine(12, y, 28, y);
                        int[] xda1 = {32, 26, 26};
                        int[] yda1 = {y, y - 4, y + 4};
                        g2d.fillPolygon(xda1, yda1, 3);
                        int[] xda2 = {8, 14, 14};
                        int[] yda2 = {y, y - 4, y + 4};
                        g2d.fillPolygon(xda2, yda2, 3);
                        break;
                    case ARROW:
                    default:
                        g2d.setStroke(new BasicStroke(2));
                        g2d.drawLine(8, y, 28, y);
                        int[] xa = {32, 26, 26};
                        int[] ya = {y, y - 4, y + 4};
                        g2d.fillPolygon(xa, ya, 3);
                        break;
                }
                
                // 下拉箭头
                g2d.setColor(new Color(150, 150, 150));
                int[] triX = {38, 44, 41};
                int[] triY = {y - 2, y - 2, y + 3};
                g2d.fillPolygon(triX, triY, 3);
            }
        };
        styleButton.setPreferredSize(new Dimension(50, 28));
        styleButton.setBackground(new Color(60, 60, 60));
        styleButton.setBorder(BorderFactory.createLineBorder(new Color(80, 80, 80), 1));
        styleButton.setFocusPainted(false);
        styleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        styleButton.setToolTipText("选择样式");
        
        // 样式弹出菜单
        JPopupMenu styleMenu = new JPopupMenu();
        styleMenu.setBackground(new Color(50, 50, 50));
        String[][] styles = {
            {"→ 普通箭头", "ARROW"},
            {"— 直线", "LINE"},
            {"∿ 波浪线", "WAVY"},
            {"┄ 虚线箭头", "DASHED"},
            {"↔ 双向箭头", "DOUBLE_ARROW"}
        };
        for (String[] styleInfo : styles) {
            JMenuItem item = new JMenuItem(styleInfo[0]);
            item.setBackground(new Color(50, 50, 50));
            item.setForeground(Color.WHITE);
            item.addActionListener(e -> {
                currentArrowStyle = ArrowStyle.valueOf(styleInfo[1]);
                repaintAllComponents(arrowOptionsPanel);
                repaint();
            });
            styleMenu.add(item);
        }
        styleButton.addActionListener(e -> styleMenu.show(styleButton, 0, styleButton.getHeight()));
        arrowOptionsPanel.add(styleButton);
        
        // 分隔线
        JSeparator sep2 = new JSeparator(SwingConstants.VERTICAL);
        sep2.setPreferredSize(new Dimension(1, 20));
        sep2.setForeground(new Color(80, 80, 80));
        arrowOptionsPanel.add(sep2);
        
        // 颜色选择区域（圆角矩形色块）
        Color[] colors = {
            new Color(255, 59, 48),    // 红色
            new Color(255, 204, 0),    // 黄色
            new Color(52, 199, 89),    // 绿色
            new Color(0, 122, 255),    // 蓝色
            Color.WHITE,               // 白色
            new Color(142, 142, 147),  // 灰色
            Color.BLACK                // 黑色
        };
        
        for (Color color : colors) {
            JButton colorBtn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    // 选中状态：先绘制外层高亮边框
                    if (annotationColor.equals(color)) {
                        g2d.setColor(new Color(0, 122, 255));
                        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                        // 内层色块
                        g2d.setColor(color);
                        g2d.fillRoundRect(3, 3, getWidth() - 6, getHeight() - 6, 4, 4);
                    } else {
                        // 未选中：直接绘制色块
                        g2d.setColor(color);
                        g2d.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 6, 6);
                    }
                }
            };
            colorBtn.setPreferredSize(new Dimension(30, 26));
            colorBtn.setBackground(new Color(45, 45, 45));
            colorBtn.setBorder(BorderFactory.createEmptyBorder());
            colorBtn.setFocusPainted(false);
            colorBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            colorBtn.addActionListener(e -> {
                annotationColor = color;
                if (selectedAnnotation != null && selectedAnnotation.supportsColorChange()) {
                    selectedAnnotation.setColor(color);
                }
                // 递归重绘所有子组件以更新圆点指示器颜色
                repaintAllComponents(arrowOptionsPanel);
                repaint();
            });
            arrowOptionsPanel.add(colorBtn);
        }
        
        capturePanel.add(arrowOptionsPanel);
    }
    
    private void showArrowOptionsPanel(JButton button) {
        if (arrowOptionsPanel == null) {
            createArrowOptionsPanel();
        }
        
        Dimension panelSize = arrowOptionsPanel.getPreferredSize();
        Point btnLoc = button.getLocationOnScreen();
        Point panelLoc = capturePanel.getLocationOnScreen();
        int x = btnLoc.x - panelLoc.x;
        int y = btnLoc.y - panelLoc.y - panelSize.height - 5;
        
        if (y < 0) {
            y = btnLoc.y - panelLoc.y + button.getHeight() + 5;
        }
        
        arrowOptionsPanel.setBounds(x, y, panelSize.width, panelSize.height);
        arrowOptionsPanel.setVisible(true);
        arrowOptionsPanel.repaint();
    }
    
    private void hideArrowOptionsPanel() {
        if (arrowOptionsPanel != null && arrowOptionsPanel.isVisible()) {
            arrowOptionsPanel.setVisible(false);
        }
    }
    
    private void createTextOptionsPanel() {
        textOptionsPanel = new JPanel();
        textOptionsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        textOptionsPanel.setBackground(new Color(45, 45, 45));
        textOptionsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 70, 70), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        textOptionsPanel.setVisible(false);
        
        // 字体大小选择区域
        JPanel sizeSection = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        sizeSection.setBackground(new Color(45, 45, 45));
        
        JLabel sizeLabel = new JLabel("字号:");
        sizeLabel.setForeground(Color.WHITE);
        sizeLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        sizeSection.add(sizeLabel);
        
        // 字体大小下拉选择
        JComboBox<Integer> sizeCombo = new JComboBox<>();
        for (int size : TEXT_SIZE_OPTIONS) {
            sizeCombo.addItem(size);
        }
        sizeCombo.setSelectedItem(currentTextSize);
        sizeCombo.setPreferredSize(new Dimension(60, 24));
        sizeCombo.setBackground(new Color(60, 60, 60));
        sizeCombo.setForeground(Color.WHITE);
        sizeCombo.addActionListener(e -> {
            currentTextSize = (Integer) sizeCombo.getSelectedItem();
            repaint();
        });
        sizeSection.add(sizeCombo);
        
        textOptionsPanel.add(sizeSection);
        
        // 分隔线
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 20));
        sep.setForeground(new Color(80, 80, 80));
        textOptionsPanel.add(sep);
        
        // 颜色选择区域
        Color[] colors = {
            new Color(255, 59, 48),    // 红色
            new Color(255, 204, 0),    // 黄色
            new Color(52, 199, 89),    // 绿色
            new Color(0, 122, 255),    // 蓝色
            Color.WHITE,               // 白色
            new Color(142, 142, 147),  // 灰色
            Color.BLACK                // 黑色
        };
        
        for (Color color : colors) {
            JButton colorBtn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    if (annotationColor.equals(color)) {
                        g2d.setColor(new Color(0, 122, 255));
                        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                        g2d.setColor(color);
                        g2d.fillRoundRect(3, 3, getWidth() - 6, getHeight() - 6, 4, 4);
                    } else {
                        g2d.setColor(color);
                        g2d.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 6, 6);
                    }
                }
            };
            colorBtn.setPreferredSize(new Dimension(30, 26));
            colorBtn.setBackground(new Color(45, 45, 45));
            colorBtn.setBorder(BorderFactory.createEmptyBorder());
            colorBtn.setFocusPainted(false);
            colorBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            colorBtn.addActionListener(e -> {
                annotationColor = color;
                repaintAllComponents(textOptionsPanel);
                repaint();
            });
            textOptionsPanel.add(colorBtn);
        }
        
        capturePanel.add(textOptionsPanel);
    }
    
    private void showTextOptionsPanel(JButton button) {
        if (textOptionsPanel == null) {
            createTextOptionsPanel();
        }
        
        Dimension panelSize = textOptionsPanel.getPreferredSize();
        Point btnLoc = button.getLocationOnScreen();
        Point panelLoc = capturePanel.getLocationOnScreen();
        int x = btnLoc.x - panelLoc.x;
        int y = btnLoc.y - panelLoc.y - panelSize.height - 5;
        
        if (y < 0) {
            y = btnLoc.y - panelLoc.y + button.getHeight() + 5;
        }
        
        textOptionsPanel.setBounds(x, y, panelSize.width, panelSize.height);
        textOptionsPanel.setVisible(true);
        textOptionsPanel.repaint();
    }
    
    private void hideTextOptionsPanel() {
        if (textOptionsPanel != null && textOptionsPanel.isVisible()) {
            textOptionsPanel.setVisible(false);
        }
    }
    
    private void repaintAllComponents(java.awt.Container container) {
        container.repaint();
        for (java.awt.Component comp : container.getComponents()) {
            comp.repaint();
            if (comp instanceof java.awt.Container) {
                repaintAllComponents((java.awt.Container) comp);
            }
        }
    }
    
    private void showColorPanel() {
        if (colorPanel == null) {
            createColorPanel();
        }
        
        int x, y;
        Dimension panelSize = colorPanel.getPreferredSize();
        
        // 如果有选中的箭头，在箭头附近显示颜色面板
        if (selectedAnnotation != null && selectedAnnotation.supportsColorChange()) {
            Point center = selectedAnnotation.getCenter();
            if (center != null) {
                // 在箭头中心上方显示
                x = center.x - panelSize.width / 2;
                y = center.y - panelSize.height - 20;
                
                // 边界检查
                if (x < 5) x = 5;
                if (x + panelSize.width > getWidth() - 5) x = getWidth() - panelSize.width - 5;
                if (y < 5) {
                    // 上方放不下，放到下方
                    y = center.y + 20;
                }
                
                colorPanel.setBounds(x, y, panelSize.width, panelSize.height);
                colorPanel.setVisible(!colorPanel.isVisible());
                colorPanel.repaint();
                return;
            }
        }
        
        // 默认：定位颜色面板在箭头按钮上方
        JButton arrowBtn = annotationButtons.get(AnnotationMode.ARROW);
        if (arrowBtn != null) {
            showColorPanelNearButton(arrowBtn);
        }
    }
    
    private void showColorPanelNearButton(JButton button) {
        if (colorPanel == null) {
            createColorPanel();
        }
        
        Dimension panelSize = colorPanel.getPreferredSize();
        Point btnLoc = button.getLocationOnScreen();
        Point panelLoc = capturePanel.getLocationOnScreen();
        int x = btnLoc.x - panelLoc.x;
        int y = btnLoc.y - panelLoc.y - panelSize.height - 5;
        
        // 如果上方放不下，放到下方
        if (y < 0) {
            y = btnLoc.y - panelLoc.y + button.getHeight() + 5;
        }
        
        colorPanel.setBounds(x, y, panelSize.width, panelSize.height);
        colorPanel.setVisible(true);
        colorPanel.repaint();
    }
    
    private void hideColorPanel() {
        if (colorPanel != null && colorPanel.isVisible()) {
            colorPanel.setVisible(false);
        }
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
        
        // 切换到非箭头模式时隐藏箭头选项面板
        if (mode != AnnotationMode.ARROW) {
            hideArrowOptionsPanel();
        }
        // 切换到非文字模式时隐藏文字选项面板
        if (mode != AnnotationMode.TEXT) {
            hideTextOptionsPanel();
        }
        
        // 更新按钮样式
        updateButtonStyles();
        
        if (mode == AnnotationMode.TEXT) {
            // 文字模式：先输入文字，然后点击放置
            String text = JOptionPane.showInputDialog(this, "请输入文字（然后点击选区内位置放置）：", "文字标注", JOptionPane.PLAIN_MESSAGE);
            if (text != null && !text.trim().isEmpty()) {
                pendingText = text;
                // 保持TEXT模式，等待用户点击放置
            } else {
                this.currentMode = AnnotationMode.NONE;
                updateButtonStyles();
            }
        } else if (mode == AnnotationMode.WATERMARK) {
            // 水印模式：先输入水印文字
            String text = JOptionPane.showInputDialog(this, "请输入水印文字：", "水印", JOptionPane.PLAIN_MESSAGE);
            if (text != null && !text.trim().isEmpty()) {
                // 直接在整个选区添加水印
                if (captureRect != null) {
                    annotations.add(new WatermarkAnnotation(captureRect, text));
                    repaint();
                }
                this.currentMode = AnnotationMode.NONE;
                updateButtonStyles();
            } else {
                this.currentMode = AnnotationMode.NONE;
                updateButtonStyles();
            }
        }
        // NUMBER模式不需要特殊处理，直接点击放置即可
    }
    
    private void updateButtonStyles() {
        Color normalBg = new Color(60, 60, 60);
        Color selectedBg = new Color(0, 120, 215); // 蓝色高亮
        
        for (java.util.Map.Entry<AnnotationMode, JButton> entry : annotationButtons.entrySet()) {
            JButton btn = entry.getValue();
            if (entry.getKey() == currentMode) {
                btn.setBackground(selectedBg);
            } else {
                btn.setBackground(normalBg);
            }
        }
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
                            annotations.add(new TextAnnotation(p.x, p.y, pendingText, annotationColor, currentTextSize));
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
                        
                        // 画笔模式：开始绘制路径
                        if (currentMode == AnnotationMode.PEN) {
                            currentPenPath.clear();
                            currentPenPath.add(p);
                            isAnnotating = true;
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
                        
                        // 检查是否点击了已选中标注的控制手柄（旋转/缩放）
                        if (captureRect != null && captureRect.contains(p) && currentMode == AnnotationMode.NONE) {
                            // 先检查已选中标注的控制手柄
                            if (selectedAnnotation != null && selectedAnnotation.supportsTransform()) {
                                int handle = selectedAnnotation.getHandleAt(p);
                                if (handle >= 0) {
                                    activeHandle = handle;
                                    dragStart = p;
                                    if (handle == 0) {
                                        // 旋转：记录初始角度
                                        Point center = selectedAnnotation.getCenter();
                                        lastRotateAngle = Math.atan2(p.y - center.y, p.x - center.x);
                                    }
                                    isDraggingAnnotation = true;
                                    repaint();
                                    return;
                                }
                            }
                            
                            // 检查是否点击了其他标注（用于选中和拖动）
                            for (int i = annotations.size() - 1; i >= 0; i--) {
                                Annotation ann = annotations.get(i);
                                // 先检查控制手柄
                                if (ann.supportsTransform()) {
                                    int handle = ann.getHandleAt(p);
                                    if (handle >= 0) {
                                        selectedAnnotation = ann;
                                        activeHandle = handle;
                                        dragStart = p;
                                        if (handle == 0) {
                                            Point center = ann.getCenter();
                                            lastRotateAngle = Math.atan2(p.y - center.y, p.x - center.x);
                                        }
                                        isDraggingAnnotation = true;
                                        repaint();
                                        return;
                                    }
                                }
                                // 再检查是否点击在标注上
                                if (ann.contains(p)) {
                                    selectedAnnotation = ann;
                                    activeHandle = -1;
                                    dragStart = p;
                                    isDraggingAnnotation = true;
                                    repaint();
                                    return;
                                }
                            }
                            
                            // 点击空白处，取消选中
                            if (selectedAnnotation != null) {
                                selectedAnnotation = null;
                                activeHandle = -1;
                                repaint();
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
                    hideArrowOptionsPanel();
                    hideTextOptionsPanel();
                    if (currentMode != AnnotationMode.NONE) {
                        currentMode = AnnotationMode.NONE;
                        updateButtonStyles();
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
                        activeHandle = -1;
                        dragStart = null;
                        capturePanel.setCursor(Cursor.getDefaultCursor());
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
                    if (activeHandle == 0) {
                        // 旋转操作
                        capturePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        Point center = selectedAnnotation.getCenter();
                        double currentAngle = Math.atan2(e.getY() - center.y, e.getX() - center.x);
                        double deltaAngle = currentAngle - lastRotateAngle;
                        selectedAnnotation.rotate(center, deltaAngle);
                        lastRotateAngle = currentAngle;
                    } else if (activeHandle == 1 || activeHandle == 2) {
                        // 缩放操作（移动端点）
                        capturePanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                        selectedAnnotation.scale(activeHandle, e.getPoint());
                    } else {
                        // 普通拖动
                        capturePanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        int dx = e.getX() - dragStart.x;
                        int dy = e.getY() - dragStart.y;
                        selectedAnnotation.move(dx, dy);
                        dragStart = e.getPoint();
                    }
                    repaint();
                } else if (isAnnotating) {
                    if (currentMode == AnnotationMode.PEN) {
                        currentPenPath.add(e.getPoint());
                    } else {
                        annotationEnd = e.getPoint();
                    }
                    repaint();
                } else if (isSelecting) {
                    endPoint = e.getPoint();
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                mousePoint = e.getPoint();
                
                // 检查鼠标是否悬停在标注上，修改鼠标样式
                if (selectionComplete && currentMode == AnnotationMode.NONE && captureRect != null && captureRect.contains(mousePoint)) {
                    // 先检查已选中标注的控制手柄
                    if (selectedAnnotation != null && selectedAnnotation.supportsTransform()) {
                        int handle = selectedAnnotation.getHandleAt(mousePoint);
                        if (handle == 0) {
                            capturePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                            repaint();
                            return;
                        } else if (handle == 1 || handle == 2) {
                            capturePanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                            repaint();
                            return;
                        }
                    }
                    
                    // 检查是否悬停在任意标注上
                    boolean overAnnotation = false;
                    for (int i = annotations.size() - 1; i >= 0; i--) {
                        Annotation ann = annotations.get(i);
                        // 检查控制手柄
                        if (ann.supportsTransform()) {
                            int handle = ann.getHandleAt(mousePoint);
                            if (handle == 0) {
                                capturePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                                repaint();
                                return;
                            } else if (handle == 1 || handle == 2) {
                                capturePanel.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                                repaint();
                                return;
                            }
                        }
                        if (ann.contains(mousePoint)) {
                            overAnnotation = true;
                            break;
                        }
                    }
                    if (overAnnotation) {
                        capturePanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    } else {
                        capturePanel.setCursor(Cursor.getDefaultCursor());
                    }
                } else {
                    capturePanel.setCursor(Cursor.getDefaultCursor());
                }
                
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
        if (captureRect == null) {
            return;
        }
        
        // 画笔模式单独处理
        if (currentMode == AnnotationMode.PEN) {
            if (currentPenPath.size() > 1) {
                annotations.add(new PenAnnotation(new java.util.ArrayList<>(currentPenPath), annotationColor));
            }
            currentPenPath.clear();
            repaint();
            return;
        }
        
        if (annotationStart == null || annotationEnd == null) {
            return;
        }
        
        if (currentMode == AnnotationMode.ARROW) {
            annotations.add(new ArrowAnnotation(annotationStart, annotationEnd, annotationColor, currentArrowStyle, currentArrowStroke));
        } else if (currentMode == AnnotationMode.MOSAIC) {
            int x = Math.min(annotationStart.x, annotationEnd.x);
            int y = Math.min(annotationStart.y, annotationEnd.y);
            int w = Math.abs(annotationEnd.x - annotationStart.x);
            int h = Math.abs(annotationEnd.y - annotationStart.y);
            if (w > 5 && h > 5) {
                annotations.add(new MosaicAnnotation(new Rectangle(x, y, w, h)));
            }
        } else if (currentMode == AnnotationMode.RECT) {
            int x = Math.min(annotationStart.x, annotationEnd.x);
            int y = Math.min(annotationStart.y, annotationEnd.y);
            int w = Math.abs(annotationEnd.x - annotationStart.x);
            int h = Math.abs(annotationEnd.y - annotationStart.y);
            if (w > 5 && h > 5) {
                annotations.add(new RectAnnotation(new Rectangle(x, y, w, h), annotationColor));
            }
        } else if (currentMode == AnnotationMode.CIRCLE) {
            int x = Math.min(annotationStart.x, annotationEnd.x);
            int y = Math.min(annotationStart.y, annotationEnd.y);
            int w = Math.abs(annotationEnd.x - annotationStart.x);
            int h = Math.abs(annotationEnd.y - annotationStart.y);
            if (w > 5 && h > 5) {
                annotations.add(new CircleAnnotation(new Rectangle(x, y, w, h), annotationColor));
            }
        } else if (currentMode == AnnotationMode.HIGHLIGHT) {
            int x = Math.min(annotationStart.x, annotationEnd.x);
            int y = Math.min(annotationStart.y, annotationEnd.y);
            int w = Math.abs(annotationEnd.x - annotationStart.x);
            int h = Math.abs(annotationEnd.y - annotationStart.y);
            if (w > 5 && h > 3) {
                annotations.add(new HighlightAnnotation(new Rectangle(x, y, w, h)));
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
            
            // 绘制选中标注的控制手柄
            if (selectedAnnotation != null && selectedAnnotation.supportsTransform() && currentMode == AnnotationMode.NONE) {
                selectedAnnotation.drawHandles(g2d, 0, 0);
            }
            
            // 绘制正在进行的标注预览
            if (isAnnotating) {
                if (currentMode == AnnotationMode.PEN && !currentPenPath.isEmpty()) {
                    drawPenPreview(g2d, currentPenPath);
                } else if (annotationStart != null && annotationEnd != null) {
                    if (currentMode == AnnotationMode.ARROW) {
                        drawArrowPreview(g2d, annotationStart, annotationEnd);
                    } else if (currentMode == AnnotationMode.MOSAIC) {
                        drawMosaicPreview(g2d, annotationStart, annotationEnd);
                    } else if (currentMode == AnnotationMode.RECT) {
                        drawRectPreview(g2d, annotationStart, annotationEnd);
                    } else if (currentMode == AnnotationMode.CIRCLE) {
                        drawCirclePreview(g2d, annotationStart, annotationEnd);
                    } else if (currentMode == AnnotationMode.HIGHLIGHT) {
                        drawHighlightPreview(g2d, annotationStart, annotationEnd);
                    }
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
        
        private void drawRectPreview(Graphics2D g2d, Point start, Point end) {
            int x = Math.min(start.x, end.x);
            int y = Math.min(start.y, end.y);
            int w = Math.abs(end.x - start.x);
            int h = Math.abs(end.y - start.y);
            
            if (w > 5 && h > 5) {
                g2d.setColor(annotationColor);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawRect(x, y, w, h);
            }
        }
        
        private void drawCirclePreview(Graphics2D g2d, Point start, Point end) {
            int x = Math.min(start.x, end.x);
            int y = Math.min(start.y, end.y);
            int w = Math.abs(end.x - start.x);
            int h = Math.abs(end.y - start.y);
            
            if (w > 5 && h > 5) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(annotationColor);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawOval(x, y, w, h);
            }
        }
        
        private void drawPenPreview(Graphics2D g2d, java.util.List<Point> path) {
            if (path.size() < 2) return;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(annotationColor);
            g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < path.size(); i++) {
                Point p1 = path.get(i - 1);
                Point p2 = path.get(i);
                g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }
        
        private void drawHighlightPreview(Graphics2D g2d, Point start, Point end) {
            int x = Math.min(start.x, end.x);
            int y = Math.min(start.y, end.y);
            int w = Math.abs(end.x - start.x);
            int h = Math.abs(end.y - start.y);
            
            if (w > 5 && h > 3 && captureRect != null) {
                // 预览：在高亮区域外绘制半透明黑色遮罩
                g2d.setColor(new Color(0, 0, 0, 100));
                // 上方
                if (y > captureRect.y) {
                    g2d.fillRect(captureRect.x, captureRect.y, captureRect.width, y - captureRect.y);
                }
                // 下方
                int bottomY = y + h;
                int captureBottom = captureRect.y + captureRect.height;
                if (bottomY < captureBottom) {
                    g2d.fillRect(captureRect.x, bottomY, captureRect.width, captureBottom - bottomY);
                }
                // 左侧
                if (x > captureRect.x) {
                    g2d.fillRect(captureRect.x, y, x - captureRect.x, h);
                }
                // 右侧
                int rightX = x + w;
                int captureRight = captureRect.x + captureRect.width;
                if (rightX < captureRight) {
                    g2d.fillRect(rightX, y, captureRight - rightX, h);
                }
                // 边框提示
                g2d.setColor(new Color(255, 255, 255, 200));
                g2d.setStroke(new BasicStroke(1));
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
     * 翻译选区文字
     */
    private void translateSelectedArea() {
        // 让用户输入要翻译的文字
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        
        JLabel textLabel = new JLabel("请输入要翻译的文字 (中文→英文，其他→中文):");
        JTextArea textArea = new JTextArea(3, 30);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane textScroll = new JScrollPane(textArea);
        
        inputPanel.add(textLabel);
        inputPanel.add(Box.createVerticalStrut(5));
        inputPanel.add(textScroll);
        
        int inputResult = JOptionPane.showConfirmDialog(this, inputPanel, "翻译 (微软翻译)", 
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        
        if (inputResult != JOptionPane.OK_OPTION) {
            return;
        }
        
        String text = textArea.getText();
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        // 执行翻译
        try {
            String result = com.example.service.TranslateService.autoTranslate(text.trim());
            
            // 显示翻译结果
            JTextArea resultArea = new JTextArea(result);
            resultArea.setEditable(false);
            resultArea.setLineWrap(true);
            resultArea.setWrapStyleWord(true);
            resultArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            
            JScrollPane scrollPane = new JScrollPane(resultArea);
            scrollPane.setPreferredSize(new java.awt.Dimension(400, 200));
            
            String[] options = {"复制结果", "关闭"};
            int choice = JOptionPane.showOptionDialog(this, scrollPane, "翻译结果",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE,
                null, options, options[0]);
            
            if (choice == 0) {
                // 复制到剪贴板
                java.awt.datatransfer.StringSelection selection = 
                    new java.awt.datatransfer.StringSelection(result);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
                JOptionPane.showMessageDialog(this, "翻译结果已复制到剪贴板", "成功", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "翻译失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
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
        
        // 可选：支持旋转和缩放的标注可以覆盖这些方法
        default boolean supportsTransform() { return false; }
        default int getHandleAt(Point p) { return -1; } // -1=无, 0=旋转, 1=缩放起点, 2=缩放终点
        default void rotate(Point center, double deltaAngle) {}
        default void scale(int handleType, Point newPos) {}
        default void drawHandles(Graphics2D g2d, int offsetX, int offsetY) {}
        default Point getCenter() { return null; }
        
        // 可选：支持颜色修改的标注可以覆盖这些方法
        default boolean supportsColorChange() { return false; }
        default void setColor(Color color) {}
        default Color getColor() { return null; }
    }

    /**
     * 文字标注
     */
    private static class TextAnnotation implements Annotation {
        private int x, y;
        private final String text;
        private final Color color;
        private final int fontSize;
        private Rectangle bounds;

        public TextAnnotation(int x, int y, String text, Color color, int fontSize) {
            this.x = x;
            this.y = y;
            this.text = text;
            this.color = color;
            this.fontSize = fontSize;
            updateBounds();
        }
        
        private void updateBounds() {
            // 估算文字边界（实际绘制时会更精确）
            int width = text.length() * (fontSize / 2) + 10;
            int height = fontSize + 4;
            bounds = new Rectangle(x - 2, y - height + 4, width, height);
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setFont(new Font("微软雅黑", Font.BOLD, fontSize));
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
     * 高亮标注（降低其他区域亮度，保持选中区域亮度）
     */
    private static class HighlightAnnotation implements Annotation {
        private Rectangle rect;

        public HighlightAnnotation(Rectangle rect) {
            this.rect = new Rectangle(rect);
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            // 在高亮区域外绘制半透明黑色遮罩
            // 上方区域
            if (rect.y > 0) {
                g2d.setColor(new Color(0, 0, 0, 100));
                g2d.fillRect(offsetX, offsetY, screenImage.getWidth(), rect.y);
            }
            // 下方区域
            int bottomY = rect.y + rect.height;
            if (bottomY < screenImage.getHeight()) {
                g2d.setColor(new Color(0, 0, 0, 100));
                g2d.fillRect(offsetX, bottomY + offsetY, screenImage.getWidth(), screenImage.getHeight() - bottomY);
            }
            // 左侧区域
            if (rect.x > 0) {
                g2d.setColor(new Color(0, 0, 0, 100));
                g2d.fillRect(offsetX, rect.y + offsetY, rect.x, rect.height);
            }
            // 右侧区域
            int rightX = rect.x + rect.width;
            if (rightX < screenImage.getWidth()) {
                g2d.setColor(new Color(0, 0, 0, 100));
                g2d.fillRect(rightX + offsetX, rect.y + offsetY, screenImage.getWidth() - rightX, rect.height);
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

    /**
     * 水印标注
     */
    private static class WatermarkAnnotation implements Annotation {
        private Rectangle rect;
        private final String text;

        public WatermarkAnnotation(Rectangle rect, String text) {
            this.rect = new Rectangle(rect);
            this.text = text;
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Font font = new Font("微软雅黑", Font.BOLD, 20);
            g2d.setFont(font);
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getHeight();
            
            // 设置裁剪区域，只在选区内绘制水印
            Shape oldClip = g2d.getClip();
            g2d.setClip(rect.x + offsetX, rect.y + offsetY, rect.width, rect.height);
            
            // 半透明灰色水印，斜向平铺
            g2d.setColor(new Color(128, 128, 128, 60));
            
            // 旋转绘制水印
            java.awt.geom.AffineTransform oldTransform = g2d.getTransform();
            
            int spacingX = textWidth + 80;
            int spacingY = textHeight + 60;
            
            for (int y = rect.y - rect.height; y < rect.y + rect.height * 2; y += spacingY) {
                for (int x = rect.x - rect.width; x < rect.x + rect.width * 2; x += spacingX) {
                    g2d.translate(x + offsetX, y + offsetY);
                    g2d.rotate(Math.toRadians(-30));
                    g2d.drawString(text, 0, 0);
                    g2d.setTransform(oldTransform);
                }
            }
            
            // 恢复裁剪区域
            g2d.setClip(oldClip);
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

    /**
     * 矩形标注
     */
    private static class RectAnnotation implements Annotation {
        private Rectangle rect;
        private final Color color;

        public RectAnnotation(Rectangle rect, Color color) {
            this.rect = new Rectangle(rect);
            this.color = color;
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawRect(rect.x + offsetX, rect.y + offsetY, rect.width, rect.height);
        }
        
        @Override
        public boolean contains(Point p) {
            // 检测点是否在矩形边框附近（容差8像素）
            int tolerance = 8;
            Rectangle outer = new Rectangle(rect.x - tolerance, rect.y - tolerance, 
                                           rect.width + 2 * tolerance, rect.height + 2 * tolerance);
            Rectangle inner = new Rectangle(rect.x + tolerance, rect.y + tolerance, 
                                           Math.max(0, rect.width - 2 * tolerance), 
                                           Math.max(0, rect.height - 2 * tolerance));
            return outer.contains(p) && !inner.contains(p);
        }
        
        @Override
        public void move(int dx, int dy) {
            rect.x += dx;
            rect.y += dy;
        }
    }

    /**
     * 圆形/椭圆标注
     */
    private static class CircleAnnotation implements Annotation {
        private Rectangle rect;
        private final Color color;

        public CircleAnnotation(Rectangle rect, Color color) {
            this.rect = new Rectangle(rect);
            this.color = color;
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawOval(rect.x + offsetX, rect.y + offsetY, rect.width, rect.height);
        }
        
        @Override
        public boolean contains(Point p) {
            // 检测点是否在椭圆边框附近
            int tolerance = 10;
            // 椭圆中心
            double cx = rect.x + rect.width / 2.0;
            double cy = rect.y + rect.height / 2.0;
            double a = rect.width / 2.0;
            double b = rect.height / 2.0;
            
            if (a <= 0 || b <= 0) return false;
            
            // 计算点到椭圆的归一化距离
            double dx = p.x - cx;
            double dy = p.y - cy;
            double dist = (dx * dx) / (a * a) + (dy * dy) / (b * b);
            
            // 在椭圆边框附近（0.7到1.3之间）
            return dist >= 0.7 && dist <= 1.3;
        }
        
        @Override
        public void move(int dx, int dy) {
            rect.x += dx;
            rect.y += dy;
        }
    }

    /**
     * 画笔标注
     */
    private static class PenAnnotation implements Annotation {
        private java.util.List<Point> path;
        private final Color color;
        private Rectangle bounds;

        public PenAnnotation(java.util.List<Point> path, Color color) {
            this.path = new java.util.ArrayList<>(path);
            this.color = color;
            updateBounds();
        }
        
        private void updateBounds() {
            if (path.isEmpty()) {
                bounds = new Rectangle(0, 0, 0, 0);
                return;
            }
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (Point p : path) {
                minX = Math.min(minX, p.x);
                minY = Math.min(minY, p.y);
                maxX = Math.max(maxX, p.x);
                maxY = Math.max(maxY, p.y);
            }
            bounds = new Rectangle(minX - 5, minY - 5, maxX - minX + 10, maxY - minY + 10);
        }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            if (path.size() < 2) return;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < path.size(); i++) {
                Point p1 = path.get(i - 1);
                Point p2 = path.get(i);
                g2d.drawLine(p1.x + offsetX, p1.y + offsetY, p2.x + offsetX, p2.y + offsetY);
            }
        }
        
        @Override
        public boolean contains(Point p) {
            // 检测点是否在路径附近
            for (int i = 1; i < path.size(); i++) {
                Point p1 = path.get(i - 1);
                Point p2 = path.get(i);
                double dist = pointToLineDistance(p.x, p.y, p1.x, p1.y, p2.x, p2.y);
                if (dist < 10) return true;
            }
            return false;
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
            for (Point p : path) {
                p.x += dx;
                p.y += dy;
            }
            updateBounds();
        }
    }

    /**
     * 箭头标注 - 支持拖动、旋转和缩放，支持多种样式
     */
    private static class ArrowAnnotation implements Annotation {
        private Point start, end;
        private Color color;
        private ArrowStyle style;
        private int strokeWidth;
        private static final int HANDLE_SIZE = 8;
        private static final int ROTATE_HANDLE_DISTANCE = 25;

        public ArrowAnnotation(Point start, Point end, Color color, ArrowStyle style, int strokeWidth) {
            this.start = new Point(start);
            this.end = new Point(end);
            this.color = color;
            this.style = style;
            this.strokeWidth = strokeWidth;
        }
        
        public ArrowStyle getStyle() { return style; }
        public void setStyle(ArrowStyle style) { this.style = style; }
        public int getStrokeWidth() { return strokeWidth; }
        public void setStrokeWidth(int strokeWidth) { this.strokeWidth = strokeWidth; }
        
        @Override
        public boolean supportsColorChange() { return true; }
        
        @Override
        public void setColor(Color color) { this.color = color; }
        
        @Override
        public Color getColor() { return this.color; }

        @Override
        public void draw(Graphics2D g2d, int offsetX, int offsetY, BufferedImage screenImage) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            
            int x1 = start.x + offsetX;
            int y1 = start.y + offsetY;
            int x2 = end.x + offsetX;
            int y2 = end.y + offsetY;
            double angle = Math.atan2(y2 - y1, x2 - x1);
            
            int arrowHeadSize = 8 + strokeWidth * 2; // 箭头大小随粗细变化
            
            switch (style) {
                case LINE:
                    // 直线（无箭头）
                    g2d.setStroke(new BasicStroke(strokeWidth));
                    g2d.drawLine(x1, y1, x2, y2);
                    break;
                    
                case WAVY:
                    // 波浪线
                    g2d.setStroke(new BasicStroke(strokeWidth));
                    drawWavyLine(g2d, x1, y1, x2, y2);
                    break;
                    
                case DASHED:
                    // 虚线箭头
                    g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{8, 4}, 0));
                    g2d.drawLine(x1, y1, x2, y2);
                    g2d.setStroke(new BasicStroke(strokeWidth));
                    drawArrowHead(g2d, x2, y2, angle, arrowHeadSize);
                    break;
                    
                case DOUBLE_ARROW:
                    // 双向箭头
                    g2d.setStroke(new BasicStroke(strokeWidth));
                    g2d.drawLine(x1, y1, x2, y2);
                    drawArrowHead(g2d, x2, y2, angle, arrowHeadSize);
                    drawArrowHead(g2d, x1, y1, angle + Math.PI, arrowHeadSize);
                    break;
                    
                case ARROW:
                default:
                    // 普通箭头
                    g2d.setStroke(new BasicStroke(strokeWidth));
                    g2d.drawLine(x1, y1, x2, y2);
                    drawArrowHead(g2d, x2, y2, angle, arrowHeadSize);
                    break;
            }
        }
        
        private void drawArrowHead(Graphics2D g2d, int x, int y, double angle, int size) {
            int ax1 = (int) (x - size * Math.cos(angle - Math.PI / 6));
            int ay1 = (int) (y - size * Math.sin(angle - Math.PI / 6));
            int ax2 = (int) (x - size * Math.cos(angle + Math.PI / 6));
            int ay2 = (int) (y - size * Math.sin(angle + Math.PI / 6));
            int[] xPoints = {x, ax1, ax2};
            int[] yPoints = {y, ay1, ay2};
            g2d.fillPolygon(xPoints, yPoints, 3);
        }
        
        private void drawWavyLine(Graphics2D g2d, int x1, int y1, int x2, int y2) {
            double length = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
            double angle = Math.atan2(y2 - y1, x2 - x1);
            double waveHeight = 6;
            double waveLength = 12;
            int segments = (int) (length / waveLength);
            if (segments < 1) segments = 1;
            
            double perpAngle = angle + Math.PI / 2;
            double segLen = length / segments;
            
            int prevX = x1, prevY = y1;
            for (int i = 1; i <= segments; i++) {
                double t = (double) i / segments;
                int baseX = (int) (x1 + (x2 - x1) * t);
                int baseY = (int) (y1 + (y2 - y1) * t);
                
                // 中点偏移
                double midT = (i - 0.5) / segments;
                int midBaseX = (int) (x1 + (x2 - x1) * midT);
                int midBaseY = (int) (y1 + (y2 - y1) * midT);
                double offset = (i % 2 == 0) ? waveHeight : -waveHeight;
                int midX = (int) (midBaseX + offset * Math.cos(perpAngle));
                int midY = (int) (midBaseY + offset * Math.sin(perpAngle));
                
                // 绘制贝塞尔曲线近似
                g2d.drawLine(prevX, prevY, midX, midY);
                g2d.drawLine(midX, midY, baseX, baseY);
                prevX = baseX;
                prevY = baseY;
            }
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
        
        @Override
        public boolean supportsTransform() { return true; }
        
        @Override
        public Point getCenter() {
            return new Point((start.x + end.x) / 2, (start.y + end.y) / 2);
        }
        
        private Point getRotateHandlePos() {
            Point center = getCenter();
            double angle = Math.atan2(end.y - start.y, end.x - start.x);
            // 旋转手柄在箭头中心的垂直方向上方
            double perpAngle = angle - Math.PI / 2;
            return new Point(
                (int)(center.x + ROTATE_HANDLE_DISTANCE * Math.cos(perpAngle)),
                (int)(center.y + ROTATE_HANDLE_DISTANCE * Math.sin(perpAngle))
            );
        }
        
        @Override
        public int getHandleAt(Point p) {
            // 检查旋转手柄 (0)
            Point rotateHandle = getRotateHandlePos();
            if (Math.abs(p.x - rotateHandle.x) <= HANDLE_SIZE && Math.abs(p.y - rotateHandle.y) <= HANDLE_SIZE) {
                return 0;
            }
            // 检查起点缩放手柄 (1)
            if (Math.abs(p.x - start.x) <= HANDLE_SIZE && Math.abs(p.y - start.y) <= HANDLE_SIZE) {
                return 1;
            }
            // 检查终点缩放手柄 (2)
            if (Math.abs(p.x - end.x) <= HANDLE_SIZE && Math.abs(p.y - end.y) <= HANDLE_SIZE) {
                return 2;
            }
            return -1;
        }
        
        @Override
        public void rotate(Point center, double deltaAngle) {
            // 绕中心点旋转起点和终点
            start = rotatePoint(start, center, deltaAngle);
            end = rotatePoint(end, center, deltaAngle);
        }
        
        private Point rotatePoint(Point p, Point center, double angle) {
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int dx = p.x - center.x;
            int dy = p.y - center.y;
            return new Point(
                (int)(center.x + dx * cos - dy * sin),
                (int)(center.y + dx * sin + dy * cos)
            );
        }
        
        @Override
        public void scale(int handleType, Point newPos) {
            if (handleType == 1) {
                // 移动起点
                start = new Point(newPos);
            } else if (handleType == 2) {
                // 移动终点
                end = new Point(newPos);
            }
        }
        
        @Override
        public void drawHandles(Graphics2D g2d, int offsetX, int offsetY) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 绘制起点手柄（蓝色方块 - 缩放）
            g2d.setColor(new Color(0, 120, 215));
            int sx = start.x + offsetX;
            int sy = start.y + offsetY;
            g2d.fillRect(sx - HANDLE_SIZE/2, sy - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE);
            g2d.setColor(Color.WHITE);
            g2d.drawRect(sx - HANDLE_SIZE/2, sy - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE);
            
            // 绘制终点手柄（蓝色方块 - 缩放）
            g2d.setColor(new Color(0, 120, 215));
            int ex = end.x + offsetX;
            int ey = end.y + offsetY;
            g2d.fillRect(ex - HANDLE_SIZE/2, ey - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE);
            g2d.setColor(Color.WHITE);
            g2d.drawRect(ex - HANDLE_SIZE/2, ey - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE);
            
            // 绘制旋转手柄（绿色圆形）
            Point rotateHandle = getRotateHandlePos();
            int rx = rotateHandle.x + offsetX;
            int ry = rotateHandle.y + offsetY;
            Point center = getCenter();
            int cx = center.x + offsetX;
            int cy = center.y + offsetY;
            
            // 绘制连接线
            g2d.setColor(new Color(100, 100, 100, 150));
            g2d.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 3}, 0));
            g2d.drawLine(cx, cy, rx, ry);
            
            // 绘制旋转手柄圆形
            g2d.setColor(new Color(76, 175, 80));
            g2d.fillOval(rx - HANDLE_SIZE/2, ry - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE);
            g2d.setColor(Color.WHITE);
            g2d.drawOval(rx - HANDLE_SIZE/2, ry - HANDLE_SIZE/2, HANDLE_SIZE, HANDLE_SIZE);
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
