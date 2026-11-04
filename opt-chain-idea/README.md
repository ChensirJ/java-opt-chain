# opt-chain-idea

IntelliJ IDEA 插件：让 `java-opt-chain` 工程里的 Java 源码可以直接写 `?.`，并获得消红线、高亮、补全、跳转，以及在 IDEA 里直接编译和 Run/Debug。

IDEA 的 Java 编译器不会跑 Maven `opt-chain:preprocess`。从 **0.2.1** 起，插件在 JPS 编译阶段把 `?.` 改写成与 Maven 相同的 `OptChainOps.opt(receiver)`，再用项目 JDK 编译并 weave 字节码。命令行 `mvn test` 仍以 Maven preprocess + weave 为准。作者源码里的 `?.` 不会删掉。

## 要求

- IntelliJ IDEA 2024.1+ … 2025.2+（build `241`–`252+`，IU / IC）
- JDK 17（构建插件时）

## 构建

```bash
./gradlew buildPlugin
```

产物在 `build/distributions/opt-chain-idea-0.2.1-SNAPSHOT.zip`（Gradle 输出，不要提交到仓库根目录）。插件 id 为 `io.github.chensirj.optchain.idea`。

轻量单测（不启 IDE UI）：

```bash
./gradlew test
```

## 安装 / 升级

仓库里不再附带预编译 zip。用上面的 `./gradlew buildPlugin` 生成 `build/distributions/` 下的 zip，再从磁盘安装。

0.2.0 更换了插件 id。旧插件 id 是 `io.ch.java.opt.chain.idea`，必须先卸载，否则两套高亮会同时生效。

1. Settings → Plugins → 已安装里若有 **Opt Chain**（尤其是 id `io.ch.java.opt.chain.idea`），先 Uninstall
2. 重启 IDEA
3. Settings → Plugins → ⚙️ → Install Plugin from Disk… → 选 `opt-chain-idea/build/distributions/` 里的新 zip
4. 再重启。**File → Invalidate Caches… → Invalidate and Restart**
5. Maven 工具窗口里 **Reload** 工程。`src/main/java` 仍是编辑 `?.` 的唯一源码根。不要把 `target/opt-chain/sources` 或旧的 `target/generated-sources/opt-chain` 标成 Source Root。若 Project Structure 里还挂着旧目录，删掉它。需要的话先跑一次 `mvn -pl opt-chain-demo -am generate-sources`，让旧的 `generated-sources/opt-chain` 从磁盘上消失，然后再 Reload
6. 打开带 `?.` 的 `.java`，红线应消失（`?.` 仍会有特殊高亮）。Run **OptChainDemoMain**（仓库里的运行配置，或 `main` 旁的绿色按钮）。构建日志里应有 `Opt Chain compiled …`。`OptChainExamples` 没有 `main`，通过 `OptChainDemoMain` 或 JUnit `OptChainExamplesTest` 运行

## 扩展点

编译（JPS，不经过编辑器 PSI）：

- `buildProcess.parametersProvider` → `OptChainBuildProcessParametersProvider`（把插件 jar 和 ASM 放进构建进程 classpath）
- `META-INF/services/...BuilderService` → `OptChainJpsBuilder`（`?.` → `OptChainOps.opt`，再 weave）
- `META-INF/services/...ExcludedJavaSourceRootProvider` → 含 `?.` 的源码根不交给 IDEA 自带 javac
- `postStartupActivity` → 去掉已经被标成源码根的 opt-chain 镜像目录
- 可选的 Maven `workspaceConfigurator` → import 时排除 `target/opt-chain/**` 和旧的 `target/generated-sources/opt-chain`

编辑器：

- `highlightErrorFilter` → `OptChainHighlightErrorFilter`
- `daemon.highlightInfoFilter` → `OptChainHighlightInfoFilter`
- `annotator` (JAVA) → `OptChainAnnotator`
- `completion.contributor` (JAVA) → `OptChainCompletionContributor`
- `gotoDeclarationHandler` → `OptChainGotoDeclarationHandler`
- `colorSettingsPage` → `OptChainColorSettingsPage`

## 若仍然标红

- Plugins 里确认 **Opt Chain** 已启用，版本为 `0.2.1-SNAPSHOT`，且旧 id `io.ch.java.opt.chain.idea` 已卸载
- File → Invalidate Caches… → Invalidate and Restart
- 确认文件是普通 `.java`（不是注入片段 / Scratch 未关联 Java）
- Settings → Editor → Inspections：看 Problems 工具窗具体报的是哪条（语法 Error 应变少）
- 本插件不能改 Java 官方语法；若 `?.` 所在行还有其它真实错误，仍可能残留

## 0.1.2 字段与方法共用同一套 `?.`

`obj?.field`（例如 `u?.address?.city?.name`）和 `obj?.method()` 不是两套 PSI。补全与跳转都把 `?.` 临时改成 `.`，再按左侧类型列字段和方法、或 `resolve`。

- 补全：实例字段与方法一起出现；跳过继承来的 `private` 字段，以及内部类的 `this$0`。高亮消红过滤器没改。
- 跳转：`resolve()` 为空时，普通访问优先按限定符类型的**字段名**回退，调用（后面有 `(`）则回退到方法。字段名中间或光标停在 `?.` 上时，会再探测到标识符末尾。

Maven `preprocess` + `weave` 仍是命令行编译的语义。IDEA 0.2.1 在构建进程里做同一套 rewrite + weave，这样不必先跑 Maven 才能 Run。`GETFIELD` 的空检查由 weave 写入字节码。

## 限制

见仓库根 README 的 “IDEA plugin (`opt-chain-idea`)” 一节。
