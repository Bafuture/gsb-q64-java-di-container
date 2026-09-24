# 轻量依赖注入容器

Pair-wise GSB 标注任务仓库（第 6 批 / 64）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们要在一个命令行小工具里做依赖注入，但不想引入 Spring 这么重的框架。请从零实现一个轻量依赖注入容器，**不允许依赖 Spring、Guice 等现成容器**。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 支持构造器注入与字段注入，按类型或按名称解析依赖；2) 支持单例与原型两种作用域；3) 构造器注入形成循环依赖时必须在启动阶段报错，并给出完整依赖路径；4) 支持初始化与销毁方法，容器关闭时按依赖顺序销毁；5) 同一接口存在多个实现且未指定名称时必须在启动时报错。测试覆盖两种注入方式、两种作用域、循环依赖检测与多实现冲突，`mvn -q verify` 一条命令跑通，README 说明容器语义与已知限制。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

---

## 容器实现说明（分支 A 交付物）

实现位于 `src/main/java/com/example/di/`，零第三方运行时依赖，仅使用 JDK 反射。

### 快速上手

```java
try (Container container = new Container()) {
    container.register(PetrolEngine.class, Car.class);
    container.start();                 // 校验全部依赖并 eagerly 创建单例
    Car car = container.getBean(Car.class);
}                                      // close() 时按依赖逆序销毁单例
```

### 注解与 API

| 注解 / 方法 | 语义 |
|------|------|
| `@Component(name, scope)` | 标记受管 Bean；`name` 缺省为首字母小写的简单类名，`scope` 缺省 `SINGLETON` |
| `@Inject` | 标在构造器或字段上；构造器注入优先，字段注入在构造完成后进行（含父类字段） |
| `@Qualifier("name")` | 标在构造器参数或字段上，按名称选择候选 Bean |
| `@PostConstruct` | 无参实例方法，依赖注入完成后调用（父类方法先执行） |
| `@PreDestroy` | 无参实例方法，容器 `close()` 时调用 |
| `Container.register(...)` | 注册组件类，必须在 `start()` 之前调用 |
| `Container.start()` | 启动：校验依赖、检测构造器循环依赖、创建全部单例 |
| `Container.getBean(Class)` / `getBean(String, Class)` | 按类型 / 按名称查找 |
| `Container.close()` | 按创建顺序的逆序（即依赖逆序，先销毁依赖方）执行所有 `@PreDestroy` |

### 容器语义

- **解析规则**：依赖按声明类型匹配所有可赋值（assignable）的已注册 Bean；恰好一个候选直接注入，零个候选启动报错，多个候选且未加 `@Qualifier` 时 `start()` 抛出 `ContainerException` 并列出全部候选名称。
- **构造器选择**：有且仅有一个 `@Inject` 构造器时使用它；否则若只有一个构造器则使用它；多个构造器且未标注时启动报错。
- **作用域**：`SINGLETON` 在 `start()` 时 eagerly 创建并缓存；`PROTOTYPE` 在每次注入点解析和每次 `getBean` 时新建实例。
- **循环依赖**：`start()` 阶段对构造器注入边做 DFS，发现环时抛出 `CircularDependencyException`，消息含完整路径（如 `A -> B -> C -> A`），包括自依赖。
- **启动即失败（fail-fast）**：缺失依赖、多实现冲突、重复 Bean 名、重复注册、非法 `@Inject` 字段（static/final）、多个 `@Inject` 构造器等配置错误全部在 `start()` 时报出，不会延迟到首次查找。
- **销毁顺序**：单例按创建顺序记录，关闭时逆序销毁，保证依赖方先于被依赖方销毁；销毁过程中的异常不会中断后续销毁，会以 suppressed 形式聚合抛出。

### 已知限制

- **字段注入循环**：字段注入形成的循环（A、B 互相字段依赖）对单例可以解析，但对 `PROTOTYPE` 会在运行期抛 `CircularDependencyException`（无法构造出完整对象图），请改用构造器注入或单例。
- **原型不追踪销毁**：`PROTOTYPE` Bean 的 `@PreDestroy` 不会被容器调用（容器不持有其引用），需调用方自行管理生命周期。
- **无泛型 / 集合注入**：依赖按原始类型匹配，不区分 `List<String>` 与 `List<Integer>`，也不支持注入"某类型的全部 Bean"集合。
- **无包扫描**：组件必须显式 `register(...)`，不提供 classpath 扫描。
- **非线程安全**：`register`/`start`/`getBean`/`close` 假定单线程使用（典型 CLI 场景），未做并发控制。
- **无 AOP / 条件装配**：不支持代理、Profile、`@Conditional` 等高级特性。

### 测试

`src/test/java/com/example/di/` 下 6 个测试类共 12 个用例，覆盖：构造器注入（按类型/按名称）、字段注入（含私有字段与 `@Qualifier`）、单例与原型作用域、构造器循环依赖（含自依赖）启动报错与完整路径、多实现冲突启动报错与按名消解、生命周期回调与销毁顺序。运行：

```bash
mvn -q verify
```
