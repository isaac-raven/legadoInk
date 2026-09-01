# 默认数据（defaultData）

本目录下的 JSON 由 `io.legado.app.help.DefaultData` 在应用升级时读取并写入本地数据库。

## 关于空占位

**本仓库刻意不内置任何第三方内容。**下列文件为空数组 `[]` 或禁用占位，并非数据缺失：

| 文件 | 状态 | 原内容（已移除） | 移除原因 |
|---|---|---|---|
| `bookSources.json` | **已删除** | 1 条书源（听书站点） | 第三方书源，存在版权与合规风险；代码中无任何引用 |
| `rssSources.json` | 空数组 | 4 条订阅源 | 指向第三方站点 |
| `httpTTS.json` | 空数组 | 3 个第三方 TTS 引擎 | 含 13 个第三方 TTS 服务器 IP 地址 |
| `dictRules.json` | 空数组 | 百度汉语等词典规则 | 抓取第三方站点 |
| `directLinkUpload.json` | 空数组 | 第三方网盘直传配置 | 依赖第三方服务 |
| `coverRule.json` | 禁用占位 | 第三方封面搜索规则 | 抓取第三方站点；`BookCover.kt` 会短路返回 `null` |

## 保留的本地配置

以下为纯本地功能配置，不含任何第三方内容，故原样保留：

- `txtTocRule.json` — TXT 目录识别正则规则
- `keyboardAssists.json` — 源编辑器键盘辅助符号
- `readConfig.json` — 阅读排版预设（字号、行距、配色等）
- `themeConfig.json` — 主题配色预设

## 如何添加自己的数据

应用内「书源管理 / 订阅源管理」支持导入 JSON 或 URL，也可通过「网络导入」从公开仓库订阅。
本项目的定位是**阅读器工具**，不提供、不内置、不推荐任何具体书源或内容站点。
