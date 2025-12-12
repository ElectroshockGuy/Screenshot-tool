package com.example.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.swing.*;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局快捷键管理器 - 使用JNativeHook实现系统级全局热键
 * 支持在程序后台运行时也能响应快捷键
 * 注意：从JAR/EXE运行时，全局热键功能会被禁用
 */
@Component
public class GlobalHotkeyManager {

    @Autowired
    private ScreenCaptureService screenCaptureService;

    private boolean hotkeyEnabled = false;
    private Object nativeKeyListener = null;

    @PostConstruct
    public void init() {
        // 检查是否从JAR运行
        if (isRunningFromJar()) {
            System.out.println("Running from JAR/EXE, global hotkey disabled. Use menu or buttons instead.");
            return;
        }

        // 尝试初始化全局热键（使用反射避免类加载问题）
        try {
            initGlobalHotkeyWithReflection();
        } catch (Throwable e) {
            System.err.println("Warning: Global hotkey disabled - " + e.getMessage());
        }
    }

    private boolean isRunningFromJar() {
        try {
            // 方法1: 检查代码源位置
            URL location = getClass().getProtectionDomain().getCodeSource().getLocation();
            String path = location.toString();
            if (path.endsWith(".jar") || path.contains(".jar!") || path.contains("!BOOT-INF")) {
                return true;
            }
            
            // 方法2: 检查类加载器
            String classPath = getClass().getResource(getClass().getSimpleName() + ".class").toString();
            if (classPath.startsWith("jar:") || classPath.contains("!")) {
                return true;
            }
            
            return false;
        } catch (Exception e) {
            // 如果检测失败，假设是从JAR运行（更安全）
            return true;
        }
    }

    private void initGlobalHotkeyWithReflection() throws Exception {
        // 禁用JNativeHook的日志输出
        Logger logger = Logger.getLogger("com.github.kwhat.jnativehook");
        logger.setLevel(Level.OFF);
        logger.setUseParentHandlers(false);

        // 使用反射加载和注册
        Class<?> globalScreenClass = Class.forName("com.github.kwhat.jnativehook.GlobalScreen");
        
        // 注册钩子
        globalScreenClass.getMethod("registerNativeHook").invoke(null);
        
        // 创建监听器
        nativeKeyListener = createNativeKeyListener();
        
        // 添加监听器
        globalScreenClass.getMethod("addNativeKeyListener", 
            Class.forName("com.github.kwhat.jnativehook.keyboard.NativeKeyListener"))
            .invoke(null, nativeKeyListener);
        
        hotkeyEnabled = true;
        System.out.println("Global hotkey registered successfully (Ctrl+Shift+A: fullscreen, Ctrl+Shift+S: selection)");
    }

    private Object createNativeKeyListener() throws Exception {
        Class<?> listenerClass = Class.forName("com.github.kwhat.jnativehook.keyboard.NativeKeyListener");
        Class<?> eventClass = Class.forName("com.github.kwhat.jnativehook.keyboard.NativeKeyEvent");
        
        return java.lang.reflect.Proxy.newProxyInstance(
            listenerClass.getClassLoader(),
            new Class<?>[] { listenerClass },
            (proxy, method, args) -> {
                if ("nativeKeyPressed".equals(method.getName()) && args != null && args.length > 0) {
                    handleKeyPressed(args[0], eventClass);
                } else if ("nativeKeyReleased".equals(method.getName()) && args != null && args.length > 0) {
                    handleKeyReleased(args[0], eventClass);
                }
                return null;
            }
        );
    }

    private boolean ctrlPressed = false;
    private boolean shiftPressed = false;

    private void handleKeyPressed(Object event, Class<?> eventClass) throws Exception {
        int keyCode = (int) eventClass.getMethod("getKeyCode").invoke(event);
        int VC_CONTROL = eventClass.getField("VC_CONTROL").getInt(null);
        int VC_SHIFT = eventClass.getField("VC_SHIFT").getInt(null);
        int VC_A = eventClass.getField("VC_A").getInt(null);
        int VC_S = eventClass.getField("VC_S").getInt(null);

        if (keyCode == VC_CONTROL) ctrlPressed = true;
        if (keyCode == VC_SHIFT) shiftPressed = true;

        // Ctrl+Shift+A: 全屏截图
        if (ctrlPressed && shiftPressed && keyCode == VC_A) {
            SwingUtilities.invokeLater(() -> screenCaptureService.captureFullScreen());
        }
        // Ctrl+Shift+S: 选区截图
        if (ctrlPressed && shiftPressed && keyCode == VC_S) {
            SwingUtilities.invokeLater(() -> screenCaptureService.captureSelectedArea());
        }
    }

    private void handleKeyReleased(Object event, Class<?> eventClass) throws Exception {
        int keyCode = (int) eventClass.getMethod("getKeyCode").invoke(event);
        int VC_CONTROL = eventClass.getField("VC_CONTROL").getInt(null);
        int VC_SHIFT = eventClass.getField("VC_SHIFT").getInt(null);

        if (keyCode == VC_CONTROL) ctrlPressed = false;
        if (keyCode == VC_SHIFT) shiftPressed = false;
    }

    @PreDestroy
    public void destroy() {
        if (!hotkeyEnabled || nativeKeyListener == null) return;
        
        try {
            Class<?> globalScreenClass = Class.forName("com.github.kwhat.jnativehook.GlobalScreen");
            Class<?> listenerClass = Class.forName("com.github.kwhat.jnativehook.keyboard.NativeKeyListener");
            
            globalScreenClass.getMethod("removeNativeKeyListener", listenerClass)
                .invoke(null, nativeKeyListener);
            globalScreenClass.getMethod("unregisterNativeHook").invoke(null);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }
}
