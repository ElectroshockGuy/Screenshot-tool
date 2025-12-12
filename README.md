# 截图工具 - Screenshot Tool

一款功能丰富的桌面截图工具，基于 SpringBoot 2 + Java Swing 开发，支持全局快捷键、选区截图、图片标注等功能。

## ✨ 功能特性

### 📸 截图功能
- **全屏截图** - 一键截取整个屏幕
- **选区截图** - 自由拖拽选择截图区域
- **选区调整** - 支持8个方向拖拽调整选区大小
- **高DPI适配** - 自动适配系统缩放，IDE和打包后都能正常工作

### ⌨️ 全局快捷键
- `Ctrl+Shift+A` - 全屏截图
- `Ctrl+Shift+S` - 选区截图
- 程序后台运行时快捷键也能生效（类似微信、QQ截图）

### 🎨 标注功能
- **箭头** - 多种箭头样式（单箭头、双箭头、直线、波浪线、虚线）
- **文字** - 添加文字标注，支持多种字号
- **形状** - 矩形、圆形绘制
- **马赛克** - 隐私区域打码
- **序号** - 自动递增的序号标注
- **画笔** - 自由绘制
- **高亮** - 半透明高亮标记
- 支持多种颜色和粗细选择
- 标注可拖动、旋转、缩放

### 📌 图片置顶
- 将截图置顶显示在桌面
- 支持强制置顶（Win+D时不消失）和普通置顶
- 支持缩放、拖动、右键菜单

### 💾 保存功能
- 复制到剪贴板
- 另存为文件
- 保存成功后自动消失的Toast提示，无需点击确认

### 🎯 取色器
- 截图时按 `C` 键复制当前像素颜色值
- 放大镜显示像素网格
- 支持HEX和RGB格式

## 🛠️ 技术栈

- **JDK**: 1.8
- **SpringBoot**: 2.7.18
- **GUI**: Java Swing + FlatLaf 现代化主题
- **全局快捷键**: JNativeHook 2.2.2

## 📁 项目结构

```
springboot-swing/
├── pom.xml
├── src/main/java/com/example/
│   ├── Application.java                 # 启动类
│   ├── service/
│   │   ├── ScreenCaptureService.java    # 截图服务
│   │   └── GlobalHotkeyManager.java     # 全局快捷键管理
│   └── ui/
│       ├── MainFrame.java               # 主窗口（系统托盘）
│       ├── ScreenCaptureWindow.java     # 截图窗口
│       └── ImageTopWindow.java          # 置顶图片窗口
└── src/main/resources/
    └── application.yml
```

## 🚀 运行方式

### IDE运行
直接运行 `Application.java` 的 `main` 方法

### Maven命令行
```bash
mvn spring-boot:run
```

### 打包运行
```bash
mvn clean package
java -jar target/springboot-swing-1.0.0.jar
```

## 📖 使用说明

1. 启动程序后，会在系统托盘显示图标
2. 使用全局快捷键或托盘菜单触发截图
3. 拖拽选择截图区域，可通过8个手柄调整选区大小
4. 使用工具栏添加标注
5. 双击选区复制到剪贴板，或使用工具栏按钮保存/置顶

## ⚠️ 注意事项

- 首次运行需要下载依赖，请确保网络畅通
- 某些安全软件可能会拦截全局键盘钩子，需要允许
- Windows系统需要管理员权限才能使用全局快捷键功能
