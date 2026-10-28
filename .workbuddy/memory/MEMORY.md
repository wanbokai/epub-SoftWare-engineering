# 项目长期记忆

## Git 同步约定（重要）
- 用户要求：本项目的所有更新都必须同步提交并推送到 GitHub 仓库。
- 远程仓库：https://github.com/wanbokai/epub-SoftWare-engineering.git
- 本地已关联 origin，分支 main。
- 每次改动后：`git add -A` → `git commit`（中文提交信息）→ `git push`。
- 注意：git 已配置全局代理 http://127.0.0.1:7897（否则 GitHub 直连失败）。

## 技术栈与项目结构（已确定）
- 技术栈：Vue 3 + TypeScript + Vite（前端）/ Java + Spring Boot 3 + MyBatis + Spring Security + JWT（后端）/ MySQL 8.0 + SeaweedFS（存储）。
- 后端项目：`reader-api/`，包结构 `com.reader`，按功能分 auth/file/bookshelf/sync/parser 包。
- 数据库：reader_db，四张表 user / file_asset / bookshelf_item / reader_config（见 reader-api/src/main/resources/schema.sql）。
- 本机环境：JDK 26、Maven 3.9.16（D:\apache-maven-3.9.16）、MySQL 8.0.46（root/123456）、SeaweedFS 4.51（bin\seaweedfs\weed.exe）。

## 关键坑位（务必注意）
- 本机 shell 读不到 `mvn`（旧会话环境变量），需用完整路径 `D:/apache-maven-3.9.16/bin/mvn.cmd`。
- 环境有 `SERVER__PORT=59031` 会覆盖 application.yml 的 8080，启动必须显式加 `--server.port=8080`。
- 后端启动：`java -jar target/reader-api-0.0.1-SNAPSHOT.jar --server.port=8080`。
- **SeaweedFS 4.51 S3 认证**：默认 access key `any_access_key_id` 会被拒（403，InvalidAccessKeyId），必须用 `weed shell -master=127.0.0.1:19333` 执行 `s3.configure` 注册真实 identity（已注册 reader-access-key / reader-secret-key-0055，对 reader-books 有 Admin 权限）。SeaweedFS 启动参数：`weed server -dir ... -filer -s3 -s3.port 8333 -ip 127.0.0.1 -volume.port 18080 -master.port 19333`（volume 必须避开 8080，否则与 Spring Boot 冲突）。

## 进度
- 已完成：需求规格说明书(SRS V1.0)、软件设计文档(V1.2)、运行环境清单（均改为 Vue+SpringBoot 技术栈）。
- 已完成：后端骨架（统一响应/异常/安全配置/DDL）+ M1 鉴权模块（注册/登录/刷新/登出 + JWT）。
- 已完成：M2 文件模块（上传 + SHA-256 秒传预检 + SeaweedFS 存储 + 引用计数），冒烟测试通过。
- 已完成：M3 书架模块（入架/列表检索/重命名/移除 + 引用计数回收），冒烟测试通过。
- 已完成：M4 同步模块（进度/配置乐观锁上行 + 409冲突检测 + 下行拉取 + 冲突消解），冒烟测试通过。
- 已完成：M5 解析引擎（后端 Java 版：TXT 编码识别+正则分章、EPUB 解压+OPF/NCX 解析，输出统一书对象 Book），冒烟测试通过。
- 已完成：前端 Vue3 项目（reader-web/，Vite8 + Vue3 + TS + Pinia + Vue Router + Axios），含登录/注册、书架（搜索/重命名/移除/上传秒传预检）、阅读器（章节渲染/目录/排版配置/进度上报/409冲突消解）、设置页，`npm run build` 通过并推送 GitHub（commit c7a1e89）。

## 前端关键约定（reader-web）
- 开发端口 5173，`/api` 经 vite.config.ts 代理转发到后端 8080。
- 统一响应 `ApiResponse{code,message,data}`，http.ts 里 `request<T>()` 解包 code!=0 抛错，401 自动 refresh token 换新。
- 后端接口路径：auth(/api/auth)、files(/api/files)、bookshelf(/api/bookshelf)、sync(/api/v1/sync)、parser(/api/parser)。
- 类型定义全部对齐后端 DTO，集中在 src/types/api.ts。
- TS6 已弃用 baseUrl，路径别名用 `"paths":{"@/*":["./src/*"]}`（不配 baseUrl）。
- 前端本地内存还存了用户偏好：登录态用 localStorage（reader_access_token / reader_refresh_token）。

## 单端口部署 + 公网访问（重要）
- 前端已打包进后端：`npm run build` 后复制 `dist/*` 到 `reader-api/src/main/resources/static/`，后端单端口 8080 同时提供网页+接口，无需再跑 Vite。
- 为此新增 `WebConfig.java`（SPA 路由 forward 到 index.html）+ SecurityConfig 放行静态资源与 `/login` `/register` `/settings` `/reader/**`。
- 打包前须先 taskkill 占用 8080 的 java 进程，否则 `mvn clean` 删不掉旧 jar。
- 公网访问：cloudflared Quick Tunnel，命令 `cloudflared tunnel --url http://localhost:8080`，免域名免授权，生成 `*.trycloudflare.com` 链接（每次重启会变）。电脑须保持开机。

## 工作目录已改名 + 一键启动脚本（重要）
- **工作目录已从 `E:\software-阅读器` 改名为 `E:\software-reader`**（英文，避免中文路径编码坑）。
- 一键启动脚本在 `bin/scripts/`：`启动阅读器.bat`（双击启动三件套）、`查看公网链接.bat`（双击看当前链接，自动复制到剪贴板）。
- 开机自启：`autostart.bat` 已复制到 Windows 启动文件夹（Startup 里的「云端阅读器自启动.bat」），开机自动静默启动。
- 脚本核心：`start-reader.ps1` 依次启动 SeaweedFS(19333/18080/8333) → 后端(8080) → cloudflared 隧道。
- **踩坑**：环境有 HTTP_PROXY/http_proxy 大小写重复（WorkBuddy 注入的 49704 代理），会让 PowerShell 5.1 Start-Process 抛「已添加相同键」异常，脚本开头已清理小写变体。
