<div align="center">

# legadoInk

**为墨水屏而生的开源 Android 阅读器**

聚焦电子墨水屏设备上的沉浸式阅读体验：可自定义书源、多格式本地阅读、深度排版与朗读能力。

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%2021%2B-brightgreen.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple.svg)](https://kotlinlang.org)

**简体中文** | [English](README_EN.md)

</div>

---

## ⚠️ 先读这一节：本仓库不包含任何内容

**这是一个纯粹的阅读器客户端代码仓库。**

| 本仓库**不包含** | 说明 |
|---|---|
| 书源 / 订阅源 | 不内置、不推荐、不提供任何指向第三方站点的规则数据 |
| 图书内容 | 不含任何小说、漫画等受版权保护的作品 |
| 第三方在线服务配置 | 不内置 TTS 服务地址、词典规则、网盘直传配置 |
| 字体文件 | 出于版权考虑不打包字体，见[字体说明](#四字体说明) |
| 个人数据 | 不含阅读记录、书架、账号、密钥等任何个人数据 |

软件本身不提供内容，所有内容需由用户自行添加。使用本软件获取或传播受版权保护的内容，
相关法律责任由使用者自行承担，请尊重内容创作者的合法权益。

---

## 一、核心特色

### 🖥️ 墨水屏深度适配

针对电子墨水屏设备的显示特性做了系统性设计，全部开关集中在应用设置中：

- **独立的墨水屏 UI 主题**：`EInk` 作为与亮色 / 暗色并列的第五种主题模式，默认启用；
  界面采用纯色平面风格，避免墨水屏上难以呈现的渐变与阴影
- **全局动画归零**：墨水屏模式下所有界面动画时长置为 0，消除墨水屏残影与拖影
- **独立阅读背景**：阅读界面拥有独立的墨水屏背景配置（默认纯白 `#FFFFFF`），
  与日间 / 夜间背景互不干扰，保证翻页时对比度最大、刷新最快
- **自动翻页适配**：自动翻页在墨水屏模式下自动切换为整页刷新策略，不做渐变滚动
- **漫画二值化**：内置漫画阅读器提供墨水屏专用处理，可调节二值化阈值，
  将灰阶图片转为适合墨水屏显示的高对比度图像
- **功能聚焦阅读**：不包含视频播放等墨水屏场景无意义的能力，减小体积、降低功耗

### 📖 强大的书源规则引擎

内容获取完全由用户定义，通过规则描述「如何搜索、如何取目录、如何取正文」：

- 支持 **JSONPath / CSS 选择器 / XPath / JS** 多种规则语法，可混合使用
- 内置 **Rhino JavaScript 引擎**，规则中可执行 JS 完成加解密、接口调用等复杂逻辑
- 书源可导入导出、分享、订阅，完全由用户掌控

### 📚 多格式本地阅读

无需任何网络，直接打开本地文件：
**TXT · EPUB · MOBI · AZW3 · AZW · UMD · PDF**（自动识别编码、生成目录）

### 🔊 灵活的朗读能力

- 系统内置 TTS 引擎直接朗读
- 支持自定义 **HTTP TTS 引擎**：以规则形式接入任意在线语音合成服务，
  可为不同语言配置不同引擎

### 🎨 深度排版与主题自定义

字体、字号、字重、行距、段距、缩进、边距、翻页方式、亮度、点击区域、简繁转换、
替换净化（导入第三方替换规则）；书架、主题色、阅读背景全部可调。

### ☁️ 备份与协作

- **WebDav 云备份**：书架、阅读进度、设置一键备份 / 恢复，兼容坚果云等标准 WebDav 服务
- **内置 Web 服务**：手机与电脑处于同一网络时，可通过浏览器管理书架、传书、编辑源

### 📡 订阅与漫画

支持 RSS 订阅源聚合阅读，内置漫画阅读器（含上述墨水屏优化）。

---

## 二、快速开始

### 环境要求

| 项 | 版本 |
|---|---|
| JDK | 17 |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.10 |
| Gradle | 8.14.4（使用仓库自带 wrapper，无需手动安装） |
| compileSdk | 36（minSdk 21） |

### 构建

```bash
# 1. 配置本地 SDK 路径（该文件已被 .gitignore 忽略，不会入库）
echo "sdk.dir=/path/to/your/android-sdk" > local.properties

# 2.（可选）获取开源字体，见「四、字体说明」
./scripts/download-fonts.sh

# 3. 构建正式包
./gradlew assembleAppRelease
```

产物位于 `app/build/outputs/apk/app/release/`。

> **版本号说明**：`versionName` 在 `app/build.gradle` 中定义（当前 `1.0.0`），
> `versionCode = 10000 + git 提交数`，发版时手动递增即可。

### 签名

本仓库**不包含任何签名密钥**。release 构建通过 Gradle 属性读取签名配置，请自行生成密钥：

```bash
keytool -genkey -v -keystore your-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias your-alias
```

在 `~/.gradle/gradle.properties` 中配置（与 `app/build.gradle` 的属性名一一对应）：

```properties
RELEASE_STORE_FILE=/absolute/path/to/your-key.jks
RELEASE_KEY_ALIAS=your-alias
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_PASSWORD=your_key_password
```

> ⚠️ 签名密钥决定应用能否覆盖升级，发布后**不可更换**，请异地备份；
> 密码切勿硬编码或提交到仓库。

## 三、目录结构

```
.
├── app/                     主模块（应用）
│   └── src/main/
│       ├── assets/
│       │   ├── defaultData/ 应用默认配置（主题、阅读排版、基础规则）
│       │   ├── fonts/       阅读字体（空，需自行获取）
│       │   └── web/         内置 Web 服务与帮助文档
│       └── java/            Kotlin 源码
├── modules/
│   ├── book/                书籍解析模块（epub 等）
│   ├── rhino/               JavaScript 引擎模块
│   └── web/                 配套 Web 前端（独立 Vue 工程，未纳入 Gradle 构建）
├── scripts/
│   └── download-fonts.sh    开源字体下载脚本
├── NOTICE                   第三方版权与许可证说明
└── LICENSE                  GPL-3.0 全文
```

## 四、字体说明

出于版权考虑，仓库**不打包任何字体文件**：

- **霞鹜文楷（LXGW WenKai）**：SIL OFL 1.1 开源授权，可通过脚本下载用于构建：
  ```bash
  ./scripts/download-fonts.sh
  ```
- **专有字体**（如京华老宋体等保留所有权利的字体）：不允许随开源项目再分发，
  请自行获取后通过应用内「导入字体」使用

也可以把自行获得授权的 `.ttf` 放进 `app/src/main/assets/fonts/` 一并构建。
详见 [`app/src/main/assets/fonts/README.md`](app/src/main/assets/fonts/README.md)。

## 五、许可证

本项目采用 **GNU General Public License v3.0**，完整条款见 [LICENSE](LICENSE)。

- 第三方库与资源的署名、授权信息见 [NOTICE](NOTICE)
- 字体授权由使用者自行负责，本项目不对任何字体的授权状态作保证

## 六、致谢

本项目从以下优秀的开源项目中学习并受益，谨致谢意：

- [gedoor/legado](https://github.com/gedoor/legado)（开源阅读）
- [阅读 Sigma（legado-E）](https://gitee.com/lyc486/legado)

感谢所有上游贡献者的工作。

本项目的定位是**阅读器工具**，不提供、不内置、不推荐任何书源与内容站点。
