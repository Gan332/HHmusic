<div align="center">

# dsh-Fonts

**DeepSeek Harness 的字体系统插件** — 内置 OFL 开源字体离线分发、自定义字体导入、以及可供其他插件扩展的 `ctx.fonts` 注册表

[![GitHub stars](https://img.shields.io/github/stars/zhijun-dai/dsh-Fonts)](https://github.com/zhijun-dai/dsh-Fonts/stargazers)
[![GitHub issues](https://img.shields.io/github/issues/zhijun-dai/dsh-Fonts)](https://github.com/zhijun-dai/dsh-Fonts/issues)
[![GitHub contributors](https://img.shields.io/github/contributors/zhijun-dai/dsh-Fonts)](https://github.com/zhijun-dai/dsh-Fonts/graphs/contributors)
[![License](https://img.shields.io/github/license/zhijun-dai/dsh-Fonts)](./LICENSE)

[English](./README.en.md) | 中文

</div>

## 简介

DeepSeek Harness 的 Web UI 默认使用系统字体栈，没有任何内置 webfont。dsh-Fonts 把字体变成可组合的资源：

- **内置预设**：JetBrains Mono + Inter、Fira Code + IBM Plex Sans、Cascadia Code 三套 OFL 授权字体搭配，随插件包离线分发（无需联网、无 CORS 问题）
- **自定义导入**：在设置面板里填入字体名 + woff2 链接，即可导入你自己的界面/代码字体，选择持久化在本机
- **插件 API**：提供 `ctx.fonts` 注册表服务，其他插件可以用 `ctx.get("fonts")` 注册或消费字体预设

## 预览

<details>
<summary>设置 → 常规 → 字体（点击展开截图）</summary>

![字体设置行](./assets/fonts-settings.webp)

</details>

<details>
<summary>JetBrains Mono + Inter 应用效果</summary>

![JetBrains Mono + Inter](./assets/fonts-jetbrains-inter.webp)

</details>

## 使用

本插件为 DeepSeek Harness 的双面插件（host 半边分发字体文件，浏览器半边提供注册表与设置行），安装后即可在 **设置 → 通用 → 字体** 中切换。

**安装方式：**

```sh
# GitHub
dsh plugin --profile web add github:zhijun-dai/dsh-Fonts

# 本地目录（profile 目录是 pnpm workspace 根，必须加 -w）
dsh plugin --profile web add -w /path/to/dsh-Fonts

# npm
dsh plugin --profile web add dsh-fonts
```

然后重启 `dsh web`。

**切换字体：** 设置 → 通用 → 字体行，点击预设卡片实时应用；选择"默认"恢复系统字体栈。

**导入自定义字体：** 在字体行的编辑区（界面字体 / 代码字体）填写三项后点"添加"：

| 字段 | 说明 |
| --- | --- |
| 字体名 | 任意名称，用于在字体栈中引用（如 `Inter`） |
| woff2 链接 | 字体文件的 http(s) 直链，要求允许跨域访问（CDN 一般都可以） |
| 字重 | 400 常规 · 500 中等 · 600 半粗 · 700 粗体，**选与文件实际粗细一致的字重**。下载页面通常会标注：Regular=400、Bold=700。选错不会报错，只是界面里的加粗效果会变成浏览器模拟的"假粗体" |

例子——从 Fontsource CDN 导入 Inter 常规字重作为界面字体：

```
字体名：Inter
woff2 链接：https://cdn.jsdelivr.net/npm/@fontsource/inter@5.3.0/files/inter-latin-400-normal.woff2
字重：400 常规
```

点"添加"后立即应用，刷新页面后仍生效；同一字体名 + 同一字重重复添加会覆盖旧条目，点"移除"删除。

## 插件 API

其他插件通过 `ctx.get("fonts")` 懒加载消费字体服务（跨插件的 `require` 是构建错误，不能用模块导入）：

```ts
// 你的插件浏览器半边
import type { FontPreset, FontRegistry } from 'dsh-fonts/client'

// 注册一套自己的字体预设
const dispose = ctx.get('fonts')?.register({
  id: 'my-preset',
  ui: ['My Font', '-apple-system', 'sans-serif'],
  code: ['My Mono', 'monospace'],
  faces: [{ family: 'My Font', weight: '400', src: ['https://example.com/my-font.woff2'] }],
})

// 订阅快照
const off = ctx.get('fonts')?.subscribe((snapshot) => {
  console.log(snapshot.activeId, snapshot.revision)
})
```

完整的 `FontPreset` / `FontSnapshot` / `FontRegistry` 类型见 `lib/types/client/index.d.ts`。

## 工作原理

- 浏览器半边在 `apply` 时通过 `ctx.provide("fonts", registry)` 把字体注册表挂到根上下文
- 选择变更时重写注入的 `<style id="dsh-fonts">`：`@font-face` 规则 + `:root` 上的 `--dsw-font-family` / `--ds-font-family-code` 覆盖——这两个 CSS 变量是 DSH UI 的字体入口（`@deepseek-ai/dsh-client-ui-theme` 的 `base.css` 定义，全部组件经 `var()` 消费）
- host 半边通过 `ctx.webServer` 注册 `/plugins/dsh-fonts/fonts/*` 路由，分发 `data/fonts/` 里随包打包的 woff2
- 选择持久化在 `localStorage`（`dsh-fonts:prefs`），启动时恢复；插件注册的预设被选中后若该插件缺席，回退到默认

## 已知限制

- 部分组件硬编码了 `Inter, var(--dsw-font-family)` 优先栈（如工作区浏览器），这些位置装了 Inter 的系统上仍显示 Inter
- 捆绑字体为 latin 子集（体积 ~280KB）；中文按字体栈尾部回退到系统字体（PingFang SC / Microsoft YaHei）
- 自定义导入仅支持 woff2 直链（http/https）

## 字体致谢

捆绑字体均以 SIL Open Font License 1.1 授权，许可证文本随包分发于 `data/fonts/LICENSE-*.txt`：

- [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono) — JetBrains s.r.o.
- [Inter](https://github.com/rsms/inter) — Rasmus Andersson
- [Fira Code](https://github.com/tonsky/FiraCode) — The Fira Code Project Authors
- [IBM Plex Sans](https://github.com/IBM/plex) — IBM Corp.
- [Cascadia Code](https://github.com/microsoft/cascadia-code) — Microsoft

## 💝 致谢

- [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness) — 插件化架构与 `dsh.client` 双面插件机制
- [Fontsource](https://fontsource.org) — webfont 分发
- [dsh-Catppuccin](https://github.com/zhijun-dai/Catppuccin-dsh-theme) / [dsh-Solarized](https://github.com/zhijun-dai/Solarized-dsh-theme) — 本插件参照的仓库结构

<div align="center">

**dsh-Fonts** · Copyright 2026-present [zhijun-dai](https://github.com/zhijun-dai) · [MIT License](./LICENSE)

</div>
