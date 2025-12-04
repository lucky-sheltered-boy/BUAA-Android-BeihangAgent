我们将采用 **Java + MVVM (ViewModel + LiveData) + ViewBinding + Retrofit** 的经典技术栈来构建这款应用。

下面是基于 **Java 语言** 定制的 **北航教学智能体开发 TODO List**。

---

# ☕ 北航教学智能体 (Beihang Teaching Agent) 开发 TODO List - Java 版

## 第一阶段：项目初始化与架构搭建 (Project Setup & Architecture)
> **技术栈**：Java 8+, MVVM, ViewBinding, Retrofit, Room

- [x] **项目配置**
    - [x] 创建 Android Studio 项目，语言选择 **Java**。
    - [x] 配置 `build.gradle`：
        - [x] 开启 `viewBinding { enabled = true }`（替代 findViewById）。
        - [x] 添加依赖：
            - [x] 网络：`Retrofit2`, `OkHttp3`, `Gson` converter。
            - [x] 架构组件：`Lifecycle`, `ViewModel`, `LiveData`。
            - [x] 数据库：`Room` (需要配置 annotationProcessor)。
            - [x] UI库：`Material Design`, `MarkdownView` (用于渲染 AI 回复的代码块)。
- [x] **基础架构设计 (MVVM)**
    - [x] 搭建包结构：`model`, `view` (activity/fragment), `viewmodel`, `repository`, `utils`, `network`。
    - [x] 封装 **BaseActivity** 和 **BaseFragment**，统一处理 ViewBinding 的初始化和销毁。
    - [x] 创建 **RetrofitClient** 单例类，配置 OkHttpClient 超时时间和 Header 拦截器。

## 第二阶段：用户体系与数据库 (User System & Database)
> **重点**：使用 Room 的 Java 接口与 LiveData 结合。

- [x] **本地数据库 (Room)**
    - [x] 定义 `User` 实体类 (`@Entity`)，包含字段：`uid`, `username`, `password`, `role` (int 类型: 0=学生, 1=教师)。
    - [x] 编写 `UserDao` 接口：
        - [x] `@Query` 查询用户是否存在。
        - [x] `@Insert` 注册新用户。
    - [x] 创建 `AppDatabase` 抽象类（单例模式）。
- [x] **登录与注册功能**
    - [x] **UI 实现 (XML)**：
        - [x] `activity_login.xml`：包含账号、密码输入框，登录按钮。
        - [x] `activity_register.xml`：增加 Spinner 下拉框选择“学生/教师”身份。
    - [x] **业务逻辑**：
        - [x] 使用 `ExecutorService` 或 `new Thread()` 在后台线程执行数据库查询（Java 中不能在主线程查库）。
        - [x] 使用 `SharedPreferences` 存储当前登录的 `userId` 和 `role`。
- [x] **主界面 (MainActivity)**
    - [x] 使用 `FragmentContainerView` + `BottomNavigationView`。
    - [x] **动态菜单逻辑**：
        - [x] 在 `onCreate` 中读取用户 Role。
        - [x] 如果是教师，隐藏“AI 辅导”Tab，显示“教学统计”Tab。

## 第三阶段：大模型网络通信 (LLM Integration)
> **重点**：Java 中的异步回调处理 (Callback)。

- [x] **网络层封装**
    - [x] 定义请求体实体类：`ChatRequest` (包含 `model`, `messages` 列表)。
    - [x] 定义响应体实体类：`ChatResponse` (嵌套类结构需与 API JSON 严格对应)。
    - [x] 定义 Retrofit 接口：
      ```java
      @POST("v1/chat/completions")
      Call<ChatResponse> sendMessage(@Body ChatRequest request);
      ```
    - [x] 编写 `AuthorizationInterceptor` 类，在 Header 中注入 API Key。
- [x] **聊天数据模型**
    - [x] 创建 `ChatMessage` 类：
        - [x] `content` (String)
        - [x] `role` (String: "user" / "assistant")
        - [x] `type` (int: 文本/代码)
- [x] **聊天界面 UI**
    - [x] 编写 `activity_chat.xml` 或 `fragment_chat.xml`。
    - [x] 实现 `ChatAdapter` (继承 `RecyclerView.Adapter`)：
        - [x] 创建两个 ViewHolder：`UserMessageHolder` (右侧布局) 和 `AiMessageHolder` (左侧布局)。
        - [x] 引入 Markdown 渲染库（如 `markwon`）来优雅地显示 AI 返回的代码块。

## 第四阶段：核心业务与智能体实现 (Core Features)
> **重点**：Prompt 工程与 ViewModel 状态管理。

- [x] **ViewModel 开发**
    - [x] 创建 `ChatViewModel` 类，继承 `AndroidViewModel`。
    - [x] 定义 `MutableLiveData<List<ChatMessage>>` 用于观察消息列表变化。
    - [x] 定义 `sendToAi(String msg, String systemPrompt)` 方法：
        - [x] 构造消息历史 List。
        - [x] 调用 Retrofit 的 `call.enqueue(new Callback<...>())` 进行异步请求。
        - [x] 在 `onResponse` 回调中更新 LiveData。
- [x] **必做功能 1：代码审查 (Code Review)**
    - [x] 设置 System Prompt 常量：
      `public static final String PROMPT_REVIEW = "你是一个严格的代码审查员，请检查代码的规范性、安全性和性能...";`
    - [x] 界面逻辑：用户输入代码 -> 调用 `sendToAi(code, PROMPT_REVIEW)`。
- [x] **必做功能 2：AI 辅导 (AI Tutor)**
    - [x] 设置 System Prompt：
      `public static final String PROMPT_TUTOR = "你是北航计算机学院的助教，请引导学生思考，不要直接给出答案...";`
    - [x] 实现上下文对话：在构建请求时，将最近 5-10 条历史记录一起发送。
- [x] **必做功能 3：代码注释与优化**
    - [x] 设置 System Prompt：
      `public static final String PROMPT_OPTIMIZE = "请为代码添加中文注释并进行优化...";`
    - [x] 结果展示：在 RecyclerView 中单独渲染优化后的代码块，并提供“复制”按钮。

## 第五阶段：选做任务扩展 (Optional Features)
- [ ] **选做 1：自适应辅导**
    - [ ] 在 `User` 表中增加 `preference` 字段。
    - [ ] 在 Java 代码中编写逻辑：如果检测到用户代码包含 "NullPointerException"，自动在 Prompt 后追加 *"请重点讲解空指针防御"*。
- [ ] **选做 2：教学分析面板 (教师端)**
    - [ ] 集成 `MPAndroidChart` 库。
    - [ ] 创建 `AnalysisFragment`。
    - [ ] 模拟数据生成：使用 Java 的 `Random` 类生成模拟的学生提问数据，并绘制饼图或柱状图。
- [ ] **选做 3：多模型对比**
    - [ ] 在设置页增加 `Switch` 控件：切换 "DeepSeek" 或 "Qwen"。
    - [ ] 在 `RetrofitClient` 中根据设置动态切换 `baseUrl` 或 `modelName` 参数。

## 第六阶段：测试与打包 (Testing & Deployment)
- [ ] **UI 美化 (北航特色)**
    - [ ] `colors.xml`：定义北航蓝 (`#005193` 为主色调)。
    - [ ] 在 `mipmap` 中添加北航校徽作为 App 图标。
    - [ ] 给 RecyclerView 添加背景，可以使用淡色的北航地标水印。
- [ ] **异常处理**
    - [ ] 在 Retrofit 的 `onFailure` 中捕获 `SocketTimeoutException`。
    - [ ] 使用 `Toast` 或 `Snackbar` 提示网络错误。
- [ ] **APK 打包**
    - [ ] 配置 `signingConfigs` (签名文件)。
    - [ ] 执行 `Build -> Generate Signed Bundle / APK`。

---

### 💡 专家建议 (Java 特别版)
由于使用 Java 开发，特别注意以下几点以避免常见的 Android 问题：
1.  **内存泄漏**：在 Activity/Fragment 销毁时，如果 Retrofit 请求还没结束，记得在 `onDestroy` 中取消请求 (`call.cancel()`)，防止回调更新已销毁的 View 导致 Crash。
2.  **主线程阻塞**：绝对不要在主线程（UI线程）操作数据库，请务必使用 `new Thread()` 或 `ExecutorService` 线程池。
3.  **Null安全**：Java 没有 Kotlin 的空安全机制，解析 API 返回的 JSON 时，务必进行非空判断 (`if (response.body() != null)`)。

### 目前存在的问题
- [x] 对话输入不能输入中文 —— 聊天输入框改为 TextInputLayout + 多行输入，支持中文与换行。
- [x] UI 太丑了，没有北航特色 —— 登录、注册、聊天界面统一使用北航蓝渐变与 Card 风格。
- [x] 所有窗口都没有返回键 —— 新增 MaterialToolbar，登录/注册/主界面均提供导航与退出逻辑。
- [x] 密码默认不可见但缺少小眼睛 —— TextInputLayout 添加密码切换按钮。
- [x] 登录后没有记住登录状态 —— SharedPreferences 保存会话，支持退出登录。
- [x] 代码没有语法高亮 —— Markwon + Prism4j 驱动代码块着色。
- [x] 代码没有复制按钮 —— AI 回复提供复制按钮，一键复制代码段。
- [x] 教学统计数据是假的 —— Room 记录真实提问分类，图表实时展示。
- [x] 没有做安全处理，API Key 明文存储在代码中 —— RetrofitClient 从 BuildConfig 读取 Key 与 BaseUrl，请在 `local.properties` 中配置：
    ```properties
    ai.api.key=sk-xxx
    ai.api.baseUrl=https://api.your-endpoint.com/
    ```

1.中文字符可以使用，但无法键入
2.UI布局进一步调整
3.教师端功能设计与实现