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
- 待做：M5 解析引擎 → 前端 Vue 项目。
