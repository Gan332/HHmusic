---
name: ci-auto-fix
description: 自动 CI/CD 修复循环 — 推送后监控 GitHub Actions 构建结果，失败则分析错误、修复代码、重新推送，成功则停止。
---

# CI Auto-Fix

自动修复 GitHub Actions 构建失败的循环流程。

## 使用方式

当需要修复 CI 时，激活此 skill 并提供一个初始消息（例如 "fix ci"）。

## 工作流程

### 1. 推送代码
```bash
git add -A && git commit -m "<描述性提交信息>" && git push
```

### 2. 获取当前 Action Run ID

使用 GitHub CLI 获取最近一次 workflow run 的 ID：

```bash
gh run list --workflow build-apk.yml --branch main --limit 1 --json databaseId,status,conclusion --jq '.[0]'
```

取返回的 `databaseId` 作为 run ID。

### 3. 轮询构建状态

每隔 30-60 秒查询一次构建状态：

```bash
gh run view <run-id> --json status,conclusion --jq '{status, conclusion}'
```

- `status` 变为 `completed` 表示构建结束
- `conclusion` 为 `success` → 停止流程
- `conclusion` 为 `failure` → 进入修复步骤

### 4. 获取失败日志

```bash
gh run view <run-id> --log-failed
```

### 5. 分析错误并修复

- 解析日志中的错误信息
- 定位出错的源文件
- 用 `edit` / `write` 工具修复代码

### 6. 循环

修复完成后回到步骤 1，重新推送并监控，直到 `conclusion` 为 `success`。

## 注意事项

- 确保 `gh` (GitHub CLI) 已认证并有权限访问仓库
- 使用 `gh run list` 时注意筛选正确的 workflow 文件名
- 如果连续 3 次修复后仍未成功，请向用户报告当前进展并请求指导
- 如果错误与本地环境相关（如需要本地编译验证），通知用户无法自动修复
