# opt-chain-idea

IntelliJ IDEA 插件：让 `java-opt-chain` 工程里的 Java 源码可以直接写 `?.`，并获得消红线、高亮、补全与跳转。

> 编译与空安全语义仍由 Maven `preprocess` + `weave` 完成；本插件只改善编辑器体验。

## 要求

- IntelliJ IDEA 2024.1+ … 2025.2+（build `241`–`252+`，IU / IC）
- JDK 17（构建插件时）

## 构建

```bash
./gradlew buildPlugin
```

产物：`build/distributions/opt-chain-idea-0.1.1-SNAPSHOT.zip`

轻量单测（不启 IDE UI）：

```bash
./gradlew test
```

## 安装 / 升级

1. Settings → Plugins → 已安装里若有 **Opt Chain**，先 Disable / Uninstall
2. 重启 IDEA
3. Settings → Plugins → ⚙️ → Install Plugin from Disk… → 选新 zip
4. 再重启，打开带 `?.` 的 `.java`，红线应消失（`?.` 仍会有特殊高亮）

## 扩展点

- `highlightErrorFilter` → `OptChainHighlightErrorFilter`
- `daemon.highlightInfoFilter` → `OptChainHighlightInfoFilter`
- `annotator` (JAVA) → `OptChainAnnotator`
- `completion.contributor` (JAVA) → `OptChainCompletionContributor`
- `gotoDeclarationHandler` → `OptChainGotoDeclarationHandler`
- `colorSettingsPage` → `OptChainColorSettingsPage`

## 若仍然标红

- Plugins 里确认 **Opt Chain** 已启用，版本为 `0.1.1-SNAPSHOT`
- File → Invalidate Caches… → Invalidate and Restart
- 确认文件是普通 `.java`（不是注入片段 / Scratch 未关联 Java）
- Settings → Editor → Inspections：看 Problems 工具窗具体报的是哪条（语法 Error 应变少）
- 本插件不能改 Java 官方语法；若 `?.` 所在行还有其它真实错误，仍可能残留

## 限制

见仓库根 README 的 “IDEA plugin (`opt-chain-idea`)” 一节。
