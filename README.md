# SpringBoot 2 + Swing 示例项目

这是一个演示如何在 Swing 桌面应用中集成 SpringBoot 2 的示例项目。

## 技术栈

- **JDK**: 1.8
- **SpringBoot**: 2.7.18
- **GUI**: Java Swing

## 项目结构

```
springboot-swing/
├── pom.xml                          # Maven配置文件
├── src/main/java/com/example/
│   ├── Application.java             # SpringBoot主启动类
│   ├── service/
│   │   └── HelloService.java        # 示例服务类
│   └── ui/
│       └── MainFrame.java           # Swing主窗口
└── src/main/resources/
    └── application.yml              # SpringBoot配置文件
```

## 核心特性

1. **Spring依赖注入**: Swing组件作为Spring Bean管理，可以注入其他Spring组件
2. **非Web模式**: 配置 `spring.main.web-application-type=none` 关闭Web容器
3. **GUI支持**: 配置 `spring.main.headless=false` 支持Swing GUI

## 运行方式

### 方式一：IDE运行
直接运行 `Application.java` 的 `main` 方法

### 方式二：Maven命令行
```bash
mvn spring-boot:run
```

### 方式三：打包运行
```bash
mvn clean package
java -jar target/springboot-swing-1.0.0.jar
```

## 关键代码说明

### 1. 启动类配置
```java
// 关闭headless模式以支持Swing GUI
ConfigurableApplicationContext context = new SpringApplicationBuilder(Application.class)
        .headless(false)
        .run(args);

// 在EDT线程中启动Swing界面
EventQueue.invokeLater(() -> {
    MainFrame mainFrame = context.getBean(MainFrame.class);
    mainFrame.setVisible(true);
});
```

### 2. Swing组件注入Spring服务
```java
@Component
public class MainFrame extends JFrame {
    
    private final HelloService helloService;
    
    @Autowired
    public MainFrame(HelloService helloService) {
        this.helloService = helloService;
    }
}
```

## 扩展建议

- 添加 `spring-boot-starter-data-jpa` 实现数据库访问
- 添加 `spring-boot-starter-validation` 实现参数校验
- 使用 `@Async` 实现异步操作，避免阻塞UI线程
