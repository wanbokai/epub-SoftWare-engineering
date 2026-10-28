# reader-web

云端 EPUB/TXT 图书阅读器 —— 前端（Vue 3 + TypeScript + Vite）。

## 技术栈

- Vue 3（Composition API + `<script setup>`）
- TypeScript
- Vite 8
- Vue Router 4（路由 + 登录守卫）
- Pinia（认证 / 阅读器配置状态）
- Axios（统一请求封装 + JWT 自动刷新）

## 目录结构

```
src/
├── api/          # 后端接口封装（auth/file/bookshelf/sync/parser + http 统一封装）
├── components/   # 通用组件（TopBar）
├── router/       # 路由与登录守卫
├── stores/       # Pinia 状态（auth / reader）
├── types/        # 与后端 DTO 对齐的 TypeScript 类型
├── utils/        # 工具函数（SHA-256）
└── views/        # 页面（Login/Register/Bookshelf/Reader/Settings）
```

## 开发运行

```bash
npm install
npm run dev
```

开发服务器默认运行在 http://localhost:5173，`/api` 请求经 Vite 代理转发到后端 Spring Boot（http://127.0.0.1:8080）。

> 后端须先启动：`java -jar reader-api/target/reader-api-0.0.1-SNAPSHOT.jar --server.port=8080`

## 构建

```bash
npm run build   # 产物输出到 dist/
```
