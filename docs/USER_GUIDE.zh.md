# 用户手册 — Java 自动修 Bug Agent

> 安装细节、网络排错见 `docs/RUN.md`；本手册讲**怎么用**。
> 实现内幕见 `docs/PHASE*.md`。

---

## 1. 这是什么

一个能**自主修复 Java 项目里 bug** 的 AI agent：你把项目指给它，它自己跑测试、
定位出错代码、改代码、再跑测试验证，直到测试变绿或达到迭代上限。

底层是「reason → act → observe」循环：模型思考下一步 → 调工具（读文件/搜代码/
改文件/跑测试）→ 看结果 → 再思考，如此往复。

```
            ┌─────────────────────────────────────────────┐
            │                  你 (User)                   │
            │   ./gradlew :agent-cli:run --args="<项目>"   │
            └───────────────────────┬─────────────────────┘
                                    │  项目路径 + 提示词
                                    ▼
            ┌─────────────────────────────────────────────┐
            │                  Agent 循环                  │
            │                                             │
            │   ┌──────────┐   调工具    ┌─────────────┐   │
            │   │  Chat    │ ─────────► │   工具集     │   │
            │   │  Model   │            │ searchCode  │   │
            │   │(qwen2.5) │ ◄───────── │ readFile    │   │
            │   └──────────┘   返回结果  │ writeFile   │   │
            │        │                  │ runTests    │   │
            │        │                  │ listFiles   │   │
            │        ▼                  └─────────────┘   │
            │   ┌──────────┐                              │
            │   │  Critic  │  改完先审一遍再收尾(可选)     │
            │   │ (完成门) │                              │
            │   └──────────┘                              │
            └───────────────────────┬─────────────────────┘
                                    ▼
            ┌─────────────────────────────────────────────┐
            │  结果: stopReason + 迭代数 + 改了什么的说明  │
            └─────────────────────────────────────────────┘
```

---

## 2. 快速开始（3 步）

**前提**：本机已装 Ollama 并拉好模型（详见 `docs/RUN.md` 第 1–2 节）。

```bash
# ① 确认 Ollama 在跑、模型在位（应能看到 qwen2.5:7b）
curl http://localhost:11434/api/tags

# ② 构建项目（首次会下载依赖，用了阿里云镜像）
./gradlew build

# ③ 让 agent 修你的项目（换成你自己的项目路径）
./gradlew :agent-cli:run --args="D:/path/to/your-java-project"
```

跑起来后，终端会实时打印 agent 每一步在干什么，最后给出结果。

> ⚠️ 模型必须用 **instruct** 版（`qwen2.5:7b`），**别用** `qwen2.5-coder` ——
> coder 版会把工具调用当普通文本返回，agent 循环会断掉。

---

## 3. 三种使用方式

| 我想… | 命令 | 说明 |
|-------|------|------|
| **修我自己项目的 bug** | `./gradlew :agent-cli:run --args="<项目路径>"` | 最常用。agent 在你的项目上自主修复 |
| **修 + 自定义指令** | `./gradlew :agent-cli:run --args="<项目路径> "你的提示词""` | 第二个参数覆盖默认提示词 |
| **跑内置评测基准** | `./gradlew :benchmark:runner:runEval` | 在内置的种子 bug（calc01/gcd03/str02）上量修复率 |
| **跑消融实验矩阵** | `./gradlew :benchmark:runner:runAblation` | 对比 RAG×critic 各开关组合的贡献（见 `docs/PHASE4-ablation.md`） |
| **把工具暴露给外部 MCP 客户端** | `./gradlew :agent-mcp:run --args="<项目路径>"` | 启 stdio MCP server，让 IDE / Claude Desktop / 别的 agent 用上这套工具（见 `docs/PHASE5-mcp.md`） |

### 对你自己的项目要求
- 是个 **Gradle 或 Maven** 项目（agent 靠 `build.gradle(.kts)` / `settings.gradle` / `pom.xml` 识别）
- 有**能跑的测试**——agent 靠"测试由红转绿"判断是否修好。没测试它就没有判据
- Windows 上测试运行器会自动用 `gradlew.bat` / `mvn.cmd`

---

## 4. Agent 是怎么工作的（一次典型修复）

```
迭代 0:  runTests()                  ← 先看哪些测试在挂
         └─► 3 个测试失败: averageOfThreeNumbers ...

迭代 1:  searchCode("Average compute")  ← 按符号检索定位代码 (RAG)
         └─► 命中 Average.compute() @ src/.../Average.java 行 8-14

迭代 2:  readFile("src/.../Average.java")  ← 读出来看
         └─► 发现分母写成 xs.length - 1（应是 xs.length）

迭代 3:  writeFile("src/.../Average.java", <修正后的全文>)  ← 改

迭代 4:  runTests()                  ← 验证
         └─► 全部通过 ✓

         [critic 审核] APPROVE       ← 若开了 critic，先审再收尾

结果:    stopReason=COMPLETED, iterations=5, 说明改了什么
```

**关键点**：是否"修好"由**独立的测试裁判**判定（测试退出码），**不是**听 agent
自己说修好了。所以"agent 自称完成但其实没修对"（假阳性）能被识别出来——这正是
可选的 **Critic 完成门**要拦截的（见 `docs/PHASE4-critic.md`）。

---

## 5. 配置（环境变量）

全部通过环境变量配置，不用改代码。

| 变量 | 默认值 | 作用 |
|------|--------|------|
| `AGENT_PROVIDER` | `ollama` | 模型提供方：`ollama`（本地）或 `openai` |
| `AGENT_MODEL` | `qwen2.5:7b` | 模型名（**用 instruct 版，别用 coder**） |
| `AGENT_TEMPERATURE` | `0.2` | 采样温度，越低越稳 |
| `AGENT_MAX_ITERATIONS` | `10` | 循环上限，到顶仍没修好就停 |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama 地址 |
| `OPENAI_API_KEY` | （无） | `AGENT_PROVIDER=openai` 时必填 |
| `AGENT_CRITIQUE` | `none` | 完成门模式：`none` / `critic` |
| `AGENT_CRITIC_MODEL` | 同 `AGENT_MODEL` | critic 单独用的模型 |
| `BENCHMARK_TRACE` | `false` | 评测时把每步打到控制台 |
| `AGENT_ABLATION_REPEATS` | `1` | 消融每格重复跑几次取平均 |

### 设置环境变量的写法
```powershell
# PowerShell（Windows）
$env:AGENT_CRITIQUE = "critic"
./gradlew :agent-cli:run --args="D:/path/to/project"
```
```bash
# bash / git-bash
AGENT_CRITIQUE=critic ./gradlew :agent-cli:run --args="/path/to/project"
```

---

## 5b. 用更强的模型（OpenAI / 兼容端点）

本地 7B 只能修简单、定位明确的 bug。要更强的能力，切到 `openai` provider：

```bash
# 标准 OpenAI
AGENT_PROVIDER=openai \
AGENT_MODEL=gpt-4o \
OPENAI_API_KEY=sk-xxxxx \
./gradlew :agent-cli:run --args="D:/path/to/project"
```

```bash
# 任意 OpenAI 兼容端点（DeepSeek / Moonshot / Azure / 本地代理）
AGENT_PROVIDER=openai \
AGENT_MODEL=deepseek-chat \
OPENAI_API_KEY=sk-xxxxx \
OPENAI_BASE_URL=https://api.deepseek.com/v1 \
./gradlew :agent-cli:run --args="D:/path/to/project"
```

| 变量 | 说明 |
|------|------|
| `AGENT_PROVIDER=openai` | 切到 OpenAI 路径 |
| `OPENAI_API_KEY` | **必填**，否则启动报错 |
| `OPENAI_BASE_URL` | 选填；不填走 `api.openai.com`。填了可指向任意兼容端点 |

> 注意：所选模型仍**必须支持 tool calling**（function calling），否则 agent 循环
> 会断（和本地 `qwen2.5-coder` 同理）。gpt-4o、deepseek-chat 等都支持。

---

## 6. 输出怎么看

CLI 启动时打印配置和初始化信息：
```
[cfg] provider=ollama model=qwen2.5:7b temp=0.2 maxIter=10
[init] indexed 12 symbols in 340ms
[init] test runner: GradleTestRunner
```
结束时给结果：
```
=== Result ===
stopReason: COMPLETED
iterations: 5
answer:
  把 Average.compute 的分母由 xs.length - 1 改为 xs.length …
```

**stopReason 三种终态**：

| stopReason | 含义 |
|------------|------|
| `COMPLETED` | agent 主动收尾（自认为修完）。**是否真修好仍以测试为准** |
| `MAX_ITERATIONS` | 到迭代上限还没收尾，被强制停 |
| `ERROR` | 模型调用出错（如长对话导致单次超时）。可重试或调高超时 |

---

## 7. 常见问题

| 现象 | 处理 |
|------|------|
| 卡住不动 / 工具调用变成文本 | 模型用错了——换成 `qwen2.5:7b` instruct 版 |
| `[init] no test runner` | 项目没识别到 Gradle/Maven，或缺 wrapper。确认项目根有构建文件 |
| 一直 `MAX_ITERATIONS` | 调高 `AGENT_MAX_ITERATIONS`，或 bug 太难（小模型修不动） |
| 偶发 `stop=ERROR` 且耗时很长 | 长对话触发单次调用超时；重试，或减小问题规模 |
| 连不上 Ollama | 确认 `ollama serve` 在跑、`OLLAMA_BASE_URL` 正确 |

更多安装/网络排错见 **`docs/RUN.md`**。
