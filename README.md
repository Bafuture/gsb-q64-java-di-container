# 轻量依赖注入容器

一个零依赖（仅 JDK 17）的轻量 DI 容器，位于 `com.example.di` 包下，不依赖 Spring、Guice 等任何现成容器框架。测试仅使用 JUnit 5 与 AssertJ。

## 快速开始

```java
DiContainer container = new DiContainer();
container.register(App.class, UserRepository.class);
container.start();                       // 启动阶段：解析并校验整个依赖图
App app = container.getBean(App.class);  // 按类型获取
container.close();                       // 关闭：按依赖逆序销毁单例
```

`DiContainer` 实现了 `AutoCloseable`，可直接用于 try-with-resources。`getBean` 在容器未启动时会自动触发 `start()`。

## 注解一览

| 注解 | 目标 | 语义 |
| --- | --- | --- |
| `@Component(value, scope)` | 类 | 注册为容器管理的 Bean；`value` 指定名称（缺省为首字母小写的简单类名），`scope` 指定作用域 |
| `@Inject` | 构造器 / 字段 | 标记注入点；构造器至多一个，字段可多个（含父类字段，支持 private） |
| `@Named("name")` | 构造器参数 / 字段 | 按名称限定候选 Bean，用于多实现消歧 |
| `@PostConstruct` | 无参方法 | 构造与字段注入完成后回调 |
| `@PreDestroy` | 无参方法 | 容器关闭时回调（仅单例） |

## 容器语义

- **注册与启动分离**：`register(...)` 只解析类的元数据；`start()` 一次性解析所有注入点（构造器参数与字段），并完成两项启动期校验：
  - **循环依赖检测**：对构造器注入图做 DFS，发现环时抛出 `CircularDependencyException`，消息包含完整依赖路径，例如 `Circular dependency detected in constructor injection: A -> B -> C -> A`。
  - **多实现冲突检测**：某注入点的类型匹配到多个候选且未用 `@Named` 限定时，抛出 `AmbiguousBeanException`，消息列出所有候选 Bean 名称。
- **按类型 / 按名称解析**：注入点类型为接口或抽象类时，在所有已注册类中查找可赋值候选；`@Named` 先按名称过滤再按类型匹配。`getBean(Class)`、`getBean(String)`、`getBean(String, Class)` 三种查找方式语义一致。
- **作用域**：
  - `SINGLETON`（默认）：每个容器一个共享实例，缓存后复用。
  - `PROTOTYPE`：每次查找、每个注入点都创建新实例。
- **生命周期**：
  - `@PostConstruct` 在构造器与字段注入全部完成后调用，因此可以安全地使用注入的依赖。
  - `close()` 按**依赖逆序**销毁单例（先创建的后销毁，即依赖者先于被依赖者销毁），逐个调用 `@PreDestroy` 方法；单个销毁失败不会中断其余销毁，异常以 suppressed 形式聚合抛出。
- **字段注入环**：单例之间的字段注入环是允许的（实例先放入早期缓存再注入字段）；原型作用域的字段注入环无法支持，运行期抛出 `BeanCreationException` 并给出链路。

## 已知限制

- **非线程安全**：容器面向单线程的命令行场景，未做并发控制。
- **构造器注入环无法绕过**：不像字段注入可以用早期引用解决，构造器环一律在启动期报错。
- **原型 Bean 不跟踪销毁**：`@PreDestroy` 只对单例生效，原型实例的生命周期由调用方负责。
- **无懒加载 / 条件注册 / 工厂方法**：只支持"注册类 → 反射构造"一种装配方式，没有 `@Configuration`、Provider 延迟注入、泛型类型匹配等高级特性。
- **依赖解析基于注册表**：接口的具体实现必须显式 `register`，容器不做类路径扫描。

## 构建与测试

```bash
mvn -q verify
```

测试覆盖：构造器注入（按类型 / 按名称）、字段注入（含私有字段与父类字段）、单例与原型作用域、构造器循环依赖检测（含自依赖）、多实现冲突（启动报错与 `@Named` 消歧）、初始化与按依赖逆序销毁。
