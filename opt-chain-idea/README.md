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

产物：`build/distributions/opt-chain-idea-0.1.2-SNAPSHOT.zip`

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

- Plugins 里确认 **Opt Chain** 已启用，版本为 `0.1.2-SNAPSHOT`
- File → Invalidate Caches… → Invalidate and Restart
- 确认文件是普通 `.java`（不是注入片段 / Scratch 未关联 Java）
- Settings → Editor → Inspections：看 Problems 工具窗具体报的是哪条（语法 Error 应变少）
- 本插件不能改 Java 官方语法；若 `?.` 所在行还有其它真实错误，仍可能残留

## 0.1.2 字段与方法共用同一套 `?.`

`obj?.field`（例如 `u?.address?.city?.name`）和 `obj?.method()` 不是两套 PSI。补全与跳转都把 `?.` 临时改成 `.`，再按左侧类型列字段和方法、或 `resolve`。

- 补全：实例字段与方法一起出现；跳过继承来的 `private` 字段，以及内部类的 `this$0`。高亮消红过滤器没改。
- 跳转：`resolve()` 为空时，普通访问优先按限定符类型的**字段名**回退，调用（后面有 `(`）则回退到方法。字段名中间或光标停在 `?.` 上时，会再探测到标识符末尾。

Maven `preprocess` + `weave` 仍是编译语义；`GETFIELD` 的空检查本来就有，插件只改善编辑器。

## 限制

见仓库根 README 的 “IDEA plugin (`opt-chain-idea`)” 一节。
