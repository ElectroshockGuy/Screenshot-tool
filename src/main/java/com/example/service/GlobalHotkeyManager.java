package com.example.service;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局快捷键管理器 - 使用JNativeHook实现系统级全局热键
 * 支持在程序后台运行时也能响应快捷键
 */
@Component
public class GlobalHotkeyManager implements NativeKeyListener {

    @Autowired
    private ScreenCaptureService screenCaptureService;

    private boolean ctrlPressed = false;
    private boolean shiftPressed = false;

    @PostConstruct
    public void init() {
        try {
            // 禁用JNativeHook的日志输出
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);
            logger.setUseParentHandlers(false);

            // 注册全局键盘钩子
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
            
            System.out.println("全局快捷键已注册:");
            System.out.println("  Ctrl+Shift+A - 全屏截图");
            System.out.println("  Ctrl+Shift+S - 选区截图");
        } catch (NativeHookException e) {
            System.err.println("注册全局快捷键失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            GlobalScreen.removeNativeKeyListener(this);
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        // 检测修饰键
        if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) {
            ctrlPressed = true;
        }
        if (e.getKeyCode() == NativeKeyEvent.VC_SHIFT) {
            shiftPressed = true;
        }

        // Ctrl+Shift+A: 全屏截图
        if (ctrlPressed && shiftPressed && e.getKeyCode() == NativeKeyEvent.VC_A) {
            SwingUtilities.invokeLater(() -> {
                screenCaptureService.captureFullScreen();
            });
        }

        // Ctrl+Shift+S: 选区截图
        if (ctrlPressed && shiftPressed && e.getKeyCode() == NativeKeyEvent.VC_S) {
            SwingUtilities.invokeLater(() -> {
                screenCaptureService.captureSelectedArea();
            });
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        // 释放修饰键
        if (e.getKeyCode() == NativeKeyEvent.VC_CONTROL) {
            ctrlPressed = false;
        }
        if (e.getKeyCode() == NativeKeyEvent.VC_SHIFT) {
            shiftPressed = false;
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // 不需要处理
    }
}
