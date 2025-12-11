package com.example;

import com.example.ui.MainFrame;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

/**
 * SpringBoot + Swing 应用主启动类
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        // 设置FlatLaf外观
        try {
            FlatLightLaf.setup();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 启动SpringBoot，关闭headless模式以支持Swing GUI
        ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
                .headless(false)
                .run(args);

        // 在EDT线程中启动Swing界面
        EventQueue.invokeLater(() -> {
            // 从Spring容器中获取主窗口Bean
            MainFrame mainFrame = context.getBean(MainFrame.class);
            mainFrame.setVisible(true);
        });
    }
}
