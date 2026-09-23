# DoVideoAI Web

DoVideoAI 的 Vue 3 前端：上传页面（视频 / 音频 / 链接）、资料库、原片与结果左右联动的智能分析窗口，以及历史结论回看。

```bash
npm ci
npm run dev
```

开发服务器默认通过 Vite 代理访问 `http://localhost:9090`。后端地址不同时，在项目根目录 `.env` 中设置 `VITE_DEV_PROXY_TARGET`；前后端分开部署时设置 `VITE_API_BASE_URL`。
