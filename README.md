# java-opt-chain

Compile-time optional chaining for Java, inspired by JavaScript's `?.` operator.

Standard `javac` does not accept `?.`. This project adds a **two-step Maven pipeline** so you can write source that looks like JS optional chaining, then turn it into null-safe bytecode:

1. **`opt-chain:preprocess`** (`generate-sources` / `generate-test-sources`) — If a source root contains `?.`, mirror the tree into `target/generated-sources/opt-chain`, rewrite `?.` → `.`, inject `@OptChain` (and its import) when needed, and **replace** the compile source root so `javac` never sees illegal syntax.
2. **`opt-chain:weave`** (`process-classes`) — Use ASM to rewrite methods marked `@OptChain`: `GETFIELD` / `INVOKEVIRTUAL` / `INVOKEINTERFACE` short-circuit when the receiver is `null` (reference → `null`, primitives → `0` / `false`).

Comments and string literals are left alone by the preprocessor.

## Motivation

In JavaScript you can write:

```js
user?.address?.city?.name
```

If any link is `null`/`undefined`, the expression short-circuits instead of throwing. Java forces either nested null checks or `Optional` plumbing. This project aims for a **JS-like authoring experience** in Java source (`?.`), with safety applied at **build time** rather than only via a runtime helper.

## Modules

| Module | Role |
|--------|------|
| `opt-chain-annotations` | `@OptChain` annotation (`RetentionPolicy.CLASS`) |
| `opt-chain-maven-plugin` | Goals: `preprocess`, `weave` |
| `opt-chain-demo` | `User → Address → City` sample with `?.`, JUnit 5, and a runnable `main` |
| `opt-chain-idea` | IntelliJ IDEA plugin (separate Gradle project): suppress red squiggles, highlight, completion, go-to-declaration |

- **groupId:** `io.ch.java.opt.chain`
- **version:** `0.1.0-SNAPSHOT`
- **Java:** 17+

The IDEA plugin is **not** part of the Maven reactor; `mvn test` only builds the annotation, plugin, and demo modules.

## `@OptChain`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface OptChain {}
```

Methods annotated with `@OptChain` are candidates for ASM weaving. You usually do **not** write the annotation by hand: when preprocess finds `?.` inside a method, it adds `@OptChain` automatically.

## Source preprocess (`?.` → `.`)

- Goal: `opt-chain:preprocess`
- Phases: `generate-sources` (main), `generate-test-sources` (tests, with `processTestSources=true`)
- Behavior: text-level rewrite of `?.` to `.` outside comments/strings; ensure `@OptChain` on affected methods; swap compile roots to the generated tree
- Skip: `-Doptchain.preprocess.skip=true`

Example authoring style (demo):

```java
public String cityOf(User u) {
    return u?.getAddress()?.getCity()?.getName();
}
```

## ASM weave

- Goal: `opt-chain:weave`
- Phase: `process-classes`
- Behavior: for `@OptChain` methods, insert null checks before instance field gets and instance method invokes; short-circuit with typed defaults
- Skip: `-Doptchain.skip=true`

Does **not** rewrite `INVOKESTATIC`, `INVOKESPECIAL` (including constructors), or `INVOKEDYNAMIC`. Argument expressions are still evaluated in Java order; only the call/get itself is skipped when the receiver is null.

## Maven usage

1. Depend on the annotation:

```xml
<dependency>
  <groupId>io.ch.java.opt.chain</groupId>
  <artifactId>opt-chain-annotations</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

2. Bind the plugin (preprocess + weave):

```xml
<plugin>
  <groupId>io.ch.java.opt.chain</groupId>
  <artifactId>opt-chain-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions>
    <execution>
      <id>preprocess-main</id>
      <goals><goal>preprocess</goal></goals>
      <phase>generate-sources</phase>
    </execution>
    <execution>
      <id>weave</id>
      <goals><goal>weave</goal></goals>
      <phase>process-classes</phase>
    </execution>
  </executions>
</plugin>
```

3. Write `?.` in source (demo already does this).

## Build, test, and run the demo

From the repo root:

```bash
# Full reactor tests
mvn -f /workspace/java-opt-chain/pom.xml clean test
# Windows:
mvn -f D:\java-opt-chain\pom.xml clean test

# Demo module only (builds dependencies)
mvn -f D:\java-opt-chain\pom.xml -pl opt-chain-demo -am test
```

Runnable main (`OptChainDemoMain`) prints four cases: full chain, null address, null city, null user:

```bash
# Build/install the reactor first (needed once / after clean), then run main on the demo module only.
# Do not use `-am exec:java` from the root — exec would run on the parent POM and miss mainClass.
mvn -f D:\java-opt-chain\pom.xml -pl opt-chain-demo -am package
mvn -f D:\java-opt-chain\pom.xml -pl opt-chain-demo exec:java
```

The demo `pom.xml` configures `exec-maven-plugin` with:

`mainClass=io.ch.java.opt.chain.demo.OptChainDemoMain`

Alternative after `package`/`test`:

```bash
java -cp opt-chain-demo/target/classes:opt-chain-annotations/target/classes \
  io.ch.java.opt.chain.demo.OptChainDemoMain
```

(On Windows, use `;` instead of `:` in the classpath.)

## IDEA plugin (`opt-chain-idea`)

Independent Gradle project. It does **not** change the Java language for the compiler; Maven preprocess + weave remain the source of truth for builds.

### Build

```bash
cd opt-chain-idea
./gradlew buildPlugin
# zip under opt-chain-idea/build/distributions/
```

Lightweight unit tests (no IDE UI):

```bash
./gradlew test
```

Prebuilt zips may also sit at the repo root (e.g. `opt-chain-idea-0.1.1-SNAPSHOT.zip`).

### Install

1. IntelliJ IDEA **2024.1+** (sinceBuild 241); verified path includes **2025.2** (IU-252) with plugin **0.1.1+**
2. Settings → Plugins → ⚙️ → **Install Plugin from Disk…** → select the zip
3. Restart IDE
4. If `?.` still shows red: uninstall old Opt Chain, install 0.1.1+, then **File → Invalidate Caches → Invalidate and Restart**

### Features

| Feature | Mechanism |
|---------|-----------|
| Fewer red squiggles on `?.` | `HighlightErrorFilter` / highlight filters for related `PsiErrorElement`s |
| Highlighting | `Annotator` + color settings page |
| Completion | Treat `?.` as `.` temporarily, resolve left-hand type, contribute members |
| Go to declaration | Same rewrite-then-`resolve` approach |

### Plugin limitations

- Not a real Java grammar extension; PSI may still be incomplete
- Completion / navigation are heuristics; Maven compile is authoritative
- Requires the Java plugin (`com.intellij.modules.java`)
- Eclipse / VS Code are out of scope

## Known limitations (compiler pipeline)

- Without the IDEA plugin, the editor will still flag `?.` as illegal syntax; always build with Maven.
- Preprocess is text rewrite + heuristics, not a full Java frontend; exotic nesting or unusual annotations may need a hand-written `@OptChain` or avoiding stray `?.` outside comments.
- Weaving null-checks **all** instance field reads and instance invokes inside an `@OptChain` method, not a precise chain-only dataflow.
- No rewrite of static invokes, constructors/`INVOKESPECIAL`, or `INVOKEDYNAMIC`.
- Arguments are still evaluated left-to-right before the null short-circuit on the receiver.

## Key types

- `io.ch.java.opt.chain.OptChain`
- `io.ch.java.opt.chain.plugin.OptChainPreprocessMojo`
- `io.ch.java.opt.chain.plugin.OptChainSourceTransformer`
- `io.ch.java.opt.chain.plugin.OptChainWeaveMojo`
- `io.ch.java.opt.chain.plugin.OptChainWeaver`
- `io.ch.java.opt.chain.demo.OptChainExamples`
- `io.ch.java.opt.chain.demo.OptChainDemoMain`
- `io.ch.java.opt.chain.idea.*` (IDEA extension points)
