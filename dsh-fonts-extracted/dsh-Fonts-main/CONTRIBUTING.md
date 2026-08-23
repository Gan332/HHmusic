# Contributing

## 再生成流程

`lib/client.js` 是从 `lib/client.tpl.js` + `data/presets.json` 生成的，**不要手改 `lib/client.js`**。

修改预设数据（`data/presets.json`）或模板后，在仓库根目录运行：

```sh
node scripts/gen-client.mjs
```

同时提交生成器改动与再生成的 `lib/client.js`。

## 新增一个字体预设

1. 将 woff2 文件放入 `data/fonts/`，命名沿用 Fontsource 约定：`<family>-latin-<weight>-normal.woff2`
2. **字体必须使用 OFL / Apache-2.0 等允许再分发的许可证**；在 `data/fonts/` 中随字体附带其许可证文本（`LICENSE-<family>-OFL.txt`），并在仓库根 `LICENSE` 的字体归属段补一条
3. 在 `data/presets.json` 的 `presets` 数组中新增条目：`id`、`ui`/`code` 字体栈（栈尾保留 `'PingFang SC'` / `'Microsoft YaHei'` 中文回退）、`faces`（`family` + `weight` + `file`）
4. 在 `lib/client.tpl.js` 的 zh/en 字典中补 `"font.<id>"` 标签（如无翻译需求，两侧写相同的值）
5. 运行 `node scripts/gen-client.mjs`

## 上游字体同步

捆绑字体来自 [Fontsource](https://fontsource.org)（`@fontsource/<family>@5.3.0` 的 `files/<family>-latin-<weight>-normal.woff2`），许可证文本来自各字体的上游仓库。更新字体时同步三处：字体文件、`LICENSE-*.txt`、根 `LICENSE` 归属段。

## 代码风格

- 浏览器半边保持手写的 `window.__ModuleLoader__` CJS 格式（无构建步骤），与随包发布的 ui-* 包同构
- 命名即文档，只在 WHY 不明显处加注释
