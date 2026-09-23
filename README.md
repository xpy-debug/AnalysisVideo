<div align="center">
  <h2>AnalysisVideo</h2>
  <p>
    <img src="https://img.shields.io/badge/Java-21-E76F00?style=flat-square" alt="Java 21">
    <img src="https://img.shields.io/badge/Spring%20Boot-3.5.9-6DB33F?style=flat-square" alt="Spring Boot 3.5.9">
    <img src="https://img.shields.io/badge/Vue-3-42B883?style=flat-square" alt="Vue 3">
    <img src="https://img.shields.io/badge/MySQL-8-4479A1?style=flat-square" alt="MySQL 8">
    <img src="https://img.shields.io/badge/Redis-7-DC382D?style=flat-square" alt="Redis 7">
    <img src="https://img.shields.io/badge/RabbitMQ-4-FF6600?style=flat-square" alt="RabbitMQ 4">
    <img src="https://img.shields.io/badge/LangChain4j-Agent-20232A?style=flat-square" alt="LangChain4j">
    <a href="./LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="MIT License"></a>
  </p>
</div>

<div align="center">
面向音频视频内容理解和总结分析的 <strong>AI工作台</strong>。
</div>

## 项目预览

**上传页面** — 本地视频、音频文件与视频链接三个入口；左侧是可收起的滑动导航。

![AnalysisVideo上传页面](docs/images/上传页面.png)

**数据库页面** — 管理已上传的音视频，卡片上直接发起下载音频、提取文字、智能分析与历史分析。

![AnalysisVideo 数据库页面](docs/images/数据库页面.png)

**智能分析** — 居中窗口，左栏播放原片，右栏是分析模式、目标输入与生成结果，两栏各自独立滚动。

![AnalysisVideo 智能分析](docs/images/分析页面.png)

**历史分析** — 回看同一视频下每次分析的目标、时间与完整结论。

![AnalysisVideo 历史分析](docs/images/历史结论查看.png)

用户完成登录后，可以上传视频或音频，并在「数据库」页管理解析任务；选择媒体并输入分析目标后，可以手动选择分析模式，也可以交给 Agent 自动判断。智能分析窗口左侧播放原片、右侧展示结构化结论、时间戳证据、执行计划、阶段轨迹与质量评估，并支持基于同一媒体继续追问。


### 🎬 可靠的音视频任务链路

> 把大文件上传与耗时的媒体解析从请求主链路中剥离，提交即返回，不阻塞。

- **分片上传 + 断点续传** — 前端按 5 MB 分片，Redis 记录已完成分片，MinIO 保存合并后的媒体文件，弱网中断后可从断点续传。
- **音视频双链路** — 媒体类型在上传初始化时即钉死并随记录持久化：视频投递到 `video-analysis` 队列（抽帧 + OCR + ASR），音频投递到独立的 `audio-analysis` 队列（仅 ASR），两条链路各持有一套独立的交换机、队列与死信队列，互不阻塞。
- **异步削峰** — RabbitMQ 将媒体解析移出请求线程，提交后立即返回任务 ID；Redisson 按「内容指纹 + 分析目标」加锁，拦截并发与重复消费。
- **成本护栏** — 用户级与全局令牌桶限制 AI 请求速率；ASR 与模型调用采用有限次数的指数退避重试，兜底第三方网络抖动。

### 🧩 时序多模态 VideoContext

> 把语音、画面文字与时间戳融合成一份可检索、可校验的统一上下文。

- **双分支抽取** — FFmpeg 将音频按 60 秒切片，同时通过场景变化检测抽取关键帧，并以 30 秒保底采样避免遗漏静态板书。
- **音频只走语音分支** — 音频媒体跳过关键帧抽取与 OCR，只构建 ASR 上下文，不为没有画面的内容支付视觉计算成本。
- **并行与容错** — ASR 与 OCR 使用独立有界线程池并行执行；相邻画面通过感知哈希去重，单路失败时仍保留另一条有效信息。
- **统一结构** — 语音区间、OCR 文本、关键帧与时间戳被合并为统一的 `VideoSegment`，后续检索与校验不再依赖底层模型格式。



### 🔁 有证据约束的 AgentLoop（前端呈现为「智能分析」）

> 每条结论都必须绑定可在原始视频中核验的时间戳证据，拒绝模型自由发挥。

- **角色分工** — Planner 将用户目标拆成可执行任务，Executor 生成固定结构的结论、证据与建议。
- **闭环校验** — Critic 检查目标覆盖、结构完整性与时间戳证据；不通过时依据缺失内容和时间范围定向重新检索。
- **自动模式路由** — 根据用户目标自动选择通用、学习、审查或创作模式；路由不可用时回退通用模式，不阻断分析任务。
- **四类结构化产物** — 通用模式生成结论与建议，学习模式生成大纲、自测题与易错点，审查模式定位逻辑漏洞与存疑结论，创作模式提取爆点、标题与口播脚本。
- **成本可控** — AgentLoop 最多执行两轮，既允许定向修正，也通过轮次上限约束延迟与 Token 成本。

### 🔍 长音视频检索与断点恢复

> 面向数小时长音视频的分段检索，以及分阶段可恢复的任务状态机。

- **混合检索** — 每 5 分钟生成片段摘要、关键词与 Embedding，通过关键词匹配与 Qdrant 语义召回选出 TopK 原始证据。
- **优雅降级** — Qdrant 或 Embedding 服务不可用时，退化到本地关键词与已有向量排序，不阻断主分析链路。
- **断点恢复** — Checkpoint 以 MySQL 为恢复真源、Redis 为热缓存，持久化 `VideoContext`、分块、计划、Critic 状态与最终结果。
- **状态可观测** — 前端通过 SSE 接收任务阶段；失败消息写入独立失败主题与失败任务表，可由管理接口重新投递。

## 系统流程

```mermaid
sequenceDiagram
    autonumber
    actor User as 用户
    participant Web as Vue 工作台
    participant API as Spring Boot API
    participant MQ as RabbitMQ
    participant Worker as 分析消费者
    participant Context as VideoContext
    participant Search as Qdrant 检索
    participant Agent as AgentLoop
    participant State as MySQL + Redis

    User->>Web: 上传音视频并填写分析目标
    Web->>API: 分片上传与合并
    API->>MQ: 按媒体类型投递分析任务
    API-->>Web: 返回 202 Accepted
    MQ->>Worker: 异步消费
    Worker->>State: 查询幂等结果与 Checkpoint

    alt 已存在可恢复结果
        State-->>Worker: 返回最近成功阶段
    else 首次解析（视频）
        par 语音分支
            Worker->>Context: FFmpeg 分段 + ASR
        and 视觉分支
            Worker->>Context: 关键帧抽取 + OCR
        end
        Context->>State: 保存时序多模态上下文
    else 首次解析（音频）
        Worker->>Context: FFmpeg 分段 + ASR（不抽帧、不 OCR）
        Context->>State: 保存语音上下文
    end

    Worker->>Search: 摘要、关键词与 Embedding 混合检索
    Search-->>Agent: 返回相关原始证据
    loop Critic 未通过且未达到两轮
        Agent->>Agent: Planner -> Executor -> Critic
        Agent->>Search: 按反馈定向补充证据
    end
    Agent->>State: 保存结构化结果与 Checkpoint
    Worker-->>Web: SSE 推送阶段与最终结果
    Web-->>User: 展示结论、证据与后续追问
```

## 技术栈

| 层次 | 技术 | 用途 |
| :--- | :--- | :--- |
| Web | Vue 3、Vite、SSE、Marked | 上传页面、数据库、智能分析窗口、实时进度与安全 Markdown 展示 |
| API | Java 21、Spring Boot 3.5.9、Undertow、MyBatis-Plus | 鉴权、媒体管理、任务编排与 REST API |
| 异步与缓存 | RabbitMQ 4、Redis 7.4、Redisson | 异步削峰、状态缓存、限流、锁与消费幂等 |
| 数据与存储 | MySQL 8、MinIO、Qdrant | 业务数据、媒体对象、Checkpoint 与向量检索 |
| 视频与 AI | FFmpeg、PaddleOCR、Tesseract（Tess4J）、LangChain4j、DeepSeek、TeleSpeechASR、BGE-M3 | 音视频处理、多模态解析、Agent 推理与 Embedding |
| 部署 | Docker Compose | 本地中间件与 OCR 服务编排 |

## 本地运行

### 环境要求

| 组件 | 要求 | 说明 |
| :--- | :--- | :--- |
| JDK | 21 | 后端运行环境 |
| Node.js | 22 | Vue 与 Vite 构建环境 |
| Docker | Compose v2 | 启动 MySQL、Redis、MinIO、Qdrant、RabbitMQ 与 PaddleOCR |
| FFmpeg | 可在终端调用 | 音频切分与关键帧抽取 |
| PaddleOCR | 由 Compose 启动 | 关键帧 OCR 主引擎（预构建镜像），模型首次调用时自动下载并缓存 |
| Tesseract 语言包 | 可选，需自备 `chi_sim` 与 `eng` 的 traineddata | PaddleOCR 服务不可用时的进程内 OCR 回退（Tess4J；Windows 引擎随依赖自带，Linux 需 libtesseract） |
| yt-dlp | 可选 | 仅解析在线视频链接时需要 |

建议先确认命令均可用：

```bash
java -version
node --version
docker compose version
ffmpeg -version
```

### 1. 准备配置

```bash
cp .env.example .env
```

编辑 `.env`，至少替换数据库、Redis、MinIO、Qdrant 的示例密码并设置 `SILICONFLOW_API_KEY`。全新数据库中 `DB_USERNAME` 与 `MYSQL_APP_USER` 应保持一致；`MYSQL_ROOT_PASSWORD` 仅供数据库初始化使用。密钥只保存在本地 `.env`，不要提交到仓库。



### 2. 启动中间件与 OCR 服务

```bash
./scripts/dev-up.sh
```

脚本会检查本机命令与版本、校验 Compose 配置，并等待 MySQL、Redis、MinIO、Qdrant、RabbitMQ 和 PaddleOCR 启动。PaddleOCR 使用预构建镜像，首次拉取镜像和首次 OCR 调用（自动下载模型到 `paddleocr/data`）耗时较长属正常。中间件与后端默认只监听 `127.0.0.1`，不会直接暴露到局域网；远程部署时再显式修改 `SERVER_ADDRESS` 并配置反向代理。RabbitMQ 管理界面位于 `http://localhost:15672`。

PaddleOCR 是主引擎；进程内 Tesseract（Tess4J）只在它不可用时兜底，需要自备语言包。默认语言是 `chi_sim+eng`，Tess4J 会校验声明的语言**全部**可用，因此两个文件都要放进去：

```bash
mkdir -p server/tessdata
curl -L -o server/tessdata/chi_sim.traineddata https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/chi_sim.traineddata
curl -L -o server/tessdata/eng.traineddata https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata
```

目录由 `OCR_TESSDATA_PATH` 指定，默认 `./tessdata`（相对后端进程工作目录）。上面用的是 `tessdata_fast`，体积小、速度快，适合字幕与 PPT 这类清晰文字；追求识别率可换成 `tessdata_best`。不做这一步时主链路不受影响，只是回退会逐帧失败并在日志里打 warn。

### 3. 启动后端

```bash
set -a
source .env
set +a

cd server
./mvnw spring-boot:run
```

后端默认地址为 `http://localhost:9090`，启动时会初始化项目所需数据表。另开终端确认服务可用：

```bash
curl http://localhost:9090/health
```

成功时返回 `{"code":0,"message":"success","data":"UP"}`。

### 4. 启动前端

```bash
set -a
source .env
set +a

cd client
npm ci
npm run dev
```

浏览器访问 `http://localhost:5173`。开发环境默认通过 Vite 代理访问后端；后端地址不同时修改 `VITE_DEV_PROXY_TARGET`，前后端分开部署时再设置 `VITE_API_BASE_URL`。

只查看前端界面时，可以打开 `http://localhost:5173/?demo`。Demo 模式使用内置示例数据，不依赖后端服务。

### 常见问题

| 现象 | 处理方式 |
| :--- | :--- |
| 后端无法连接 MySQL、Redis 或 RabbitMQ | 运行 `docker compose --env-file .env ps`，确认服务健康且 `.env` 密码一致 |
| 页面提示无法连接后端 | 先访问 `/health`；再检查 `VITE_DEV_PROXY_TARGET` 或 `VITE_API_BASE_URL` |
| 视频解析提示命令不存在 | 确认 `ffmpeg` 可在终端执行、PaddleOCR 服务健康（`curl http://127.0.0.1:8868/health`），必要时配置 `FFMPEG_DIR`、`OCR_URL`；回退时报 Tesseract 相关错误则检查 `OCR_TESSDATA_PATH` 下的语言包 |
| AI 接口返回 401 或模型不可用 | 检查 `SILICONFLOW_API_KEY` 与模型名称，修改后重启后端 |
| Maven 提示 `maven-default-http-blocker` | 在 `server` 目录执行 `./mvnw -s .mvn/central-settings.xml spring-boot:run`，临时绕过失效的用户级镜像 |

停止本地中间件：

```bash
docker compose --env-file .env down
```

该命令不会删除 `mysql/data`、`redis/data`、`minio/data`、`qdrant/data`、`rabbitmq/data` 或 `paddleocr/data`。需要完全重置时请先备份，再使用 `docker compose --env-file .env down --volumes` 并手动清理这些数据目录。

## 目录结构

```text
AnalysisVideo
├── client/              # Vue 3 前端（上传页面 / 数据库 / 智能分析窗口）
├── server/              # Spring Boot API、任务编排与智能分析 Agent
├── docs/                # 项目文档与页面截图
├── scripts/             # dev-up.sh 等本地脚本
├── mysql/  redis/  minio/  qdrant/  rabbitmq/  paddleocr/   # 中间件数据目录（容器挂载）
├── docker-compose.yml   # 中间件与 OCR 服务编排
└── .env.example         # 本地配置模板
```


