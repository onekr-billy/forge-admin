# Forge项目用户偏好

## 1. 前端 Node 版本

**记录日期**: 2026-05-07

运行前端相关命令（如 `pnpm install`、`pnpm exec eslint`、`pnpm build`）前，先执行：

```bash
source ~/.nvm/nvm.sh && nvm use v20.19.0
```

避免使用系统默认 Node 12 导致 pnpm 版本不兼容。

## 2. 前端样式变更偏好

**记录日期**: 2026-05-14

用户对“看起来像 AI 生成”的后台页面较敏感，尤其反感只改 CSS 但视觉无明显变化的结果。涉及页面美化时需要做可感知的结构、布局和交互改动，并通过 lint/build 或本地预览验证后再反馈。
