# java-opt-chain

Compile-time optional chaining for Java, inspired by JavaScript's `?.` operator.

Standard `javac` does not accept `?.`. This project adds a **two-step Maven pipeline** so you can write source that looks like JS optional chaining, then turn it into null-safe bytecode:

1. **`opt-chain:preprocess`** (`generate-sources` / `generate-test-sources`) — If a source root contains `?.`, mirror the tree into `target/generated-sources/opt-chain`, rewrite `?.` → `.`, inject `@OptChain` (and its import) when needed, and **replace** the compile source root so `javac` never sees illegal syntax.
2. **`opt-chain:weave`** (`process-classes`) — Use ASM to rewrite methods marked `@OptChain`: `GETFIELD` / `INVOKEVIRTUAL` / `INVOKEINTERFACE` short-circuit when the receiver is `null` (reference → `null`, primitives → typed zero / `false`; see [Short-circuit defaults](#short-circuit-defaults)).

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
- Behavior: text-level rewrite of `?.` to `.` outside comments/strings, for both calls (`obj?.method()`) and fields (`obj?.field`); ensure `@OptChain` on affected methods; swap compile roots to the generated tree
- Skip: `-Doptchain.preprocess.skip=true`
- A source root with no `?.` is left unchanged (debug log). A read, write, or transform failure names the file path relative to that root.

Example authoring style (demo). Getter chains and field chains are the same rewrite:

```java
public String cityOf(User u) {
    return u?.getAddress()?.getCity()?.getName();
}

public String fieldCityName(FieldUser u) {
    return u?.address?.city?.name;
}
```

## ASM weave

- Goal: `opt-chain:weave`
- Phase: `process-classes`
- Behavior: for `@OptChain` methods, insert null checks before instance field gets (`GETFIELD`, including `a?.field` after preprocess) and instance method invokes; short-circuit with typed defaults
- Skip: `-Doptchain.skip=true`
- Classes with no `@OptChain` method are left unchanged (debug log). The info log reports how many class files were scanned and how many were rewritten. An ASM or I/O failure names the class path relative to the output directory.

Does **not** rewrite `INVOKESTATIC`, `INVOKESPECIAL` (including constructors), or `INVOKEDYNAMIC`. Argument expressions are still evaluated in Java order; only the call/get itself is skipped when the receiver is null.

## Short-circuit defaults

Defaults are fixed (not configurable). A null receiver skips that access or call, and the weaver pushes:

| Result type | Value |
|-------------|--------|
| `boolean` | `false` |
| `byte`, `char`, `short`, `int` | `0` (`char` is `'\0'`) |
| `long` | `0L` |
| `float` | `0.0f` |
| `double` | `0.0` |
| reference or array | `null` |
| `void` | no value; the call is skipped |

`opt-chain-demo` exercises `int`, `boolean`, `long`, `double`, and reference results with `?.` on `User` → `Address` → `City` (`OptChainExamples`), and the same shapes on public fields (`FieldUser` → `FieldAddress` → `FieldCity`):

| Method | When the chain is present | When a link is null |
|--------|---------------------------|---------------------|
| `nameLength` | `String.length()` | `int` `0` |
| `primaryAddress` | `Address.isPrimary()` | `boolean` `false` |
| `cityId` | `City.getId()` | `long` `0L` |
| `cityLatitude` | `City.getLatitude()` | `double` `0.0` |
| `city` | that `City` instance | `null` |
| `cityOf` | the city name | `null` |

Root null (`user == null`) and mid-chain null (`address` or `city` null) both short-circuit from the missing link. A prefix that is still non-null keeps its real value: `primaryAddress` is the address flag even when `city` is null. `city` is non-null when `user`, `address`, and `city` are all present. A null city name short-circuits `nameLength` to `0` while `city` itself stays non-null.

Field reads use that same preprocess rule and the same `GETFIELD` null-check. Parallel methods on the public-field model:

| Method | Chain | When a link is null |
|--------|-------|---------------------|
| `fieldCityName` | `u?.address?.city?.name` | `null` |
| `fieldNameLength` | `…?.name?.length()` | `int` `0` |
| `fieldPrimary` | `u?.address?.primary` | `boolean` `false` |
| `fieldCityId` | `…?.city?.id` | `long` `0L` |
| `fieldCityLatitude` | `…?.city?.latitude` | `double` `0.0` |
| `fieldCity` | `u?.address?.city` | `null` |

A null `address` or `city` field short-circuits from that link. `fieldPrimary` stays the real flag when `city` is null. A null `name` short-circuits `fieldNameLength` to `0` while `fieldCity` stays non-null.

`byte`, `char`, `short`, and `float` use the same weaver table. The demo model does not call them.

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

From the repo root. GitHub Actions (`.github/workflows/ci.yml`) runs the same `mvn -B clean test` on Java 17 for pushes to `main` and for pull requests.

```bash
# Full reactor tests (annotations, plugin, demo)
mvn -B clean test

# Windows example checkout
mvn -f D:\java-opt-chain\pom.xml clean test

# Demo module only (builds dependencies)
mvn -pl opt-chain-demo -am test
```

`mvn test` runs `opt-chain-demo` (woven `?.` behavior for getters and public fields) and `opt-chain-maven-plugin` unit tests of `OptChainSourceTransformer` (comments/strings, field and call rewrite, `@OptChain` and import injection; no Maven lifecycle). The IDEA plugin is a separate Gradle build.

Runnable main (`OptChainDemoMain`) prints four city/name cases (full chain, null address, null city, null user), the primitive short-circuit defaults, and the matching public-field chains:

```bash
# Build/install the reactor first (needed once / after clean), then run main on the demo module only.
# Do not use `-am exec:java` from the root — exec would run on the parent POM and miss mainClass.
mvn -pl opt-chain-demo -am package
mvn -pl opt-chain-demo exec:java

# Windows:
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

Prebuilt zips may also sit at the repo root (e.g. `opt-chain-idea-0.1.1-SNAPSHOT.zip`). Plugin sources are **0.1.2-SNAPSHOT** (field completion / navigation notes below).

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
| Completion | Treat `?.` as `.` temporarily, resolve left-hand type, contribute fields and methods (one heuristic) |
| Go to declaration | Same rewrite-then-`resolve`. If `resolve()` is empty, fall back to a field (plain `obj?.field`) or method (call) of the qualifier type by name |

### Plugin limitations

- Not a real Java grammar extension; PSI may still be incomplete
- Completion / navigation are heuristics shared by fields and methods (`?.` is not a separate field grammar); Maven compile is authoritative
- **0.1.2** documents that sharing and adds the qualifier-type fallback for go-to-declaration. Highlight error filters are unchanged from 0.1.1
- Requires the Java plugin (`com.intellij.modules.java`)
- Eclipse / VS Code are out of scope

## Known limitations (compiler pipeline)

- Without the IDEA plugin, the editor still flags `?.` as illegal syntax. Maven preprocess is what `javac` compiles.
- Preprocess is a text scan, not a Java parser. `?.` outside line comments, block comments, and string/char literals is rewritten, whether the next token is a field (`u?.address?.city?.name`) or a call; those spots are covered by `OptChainSourceTransformerTest`. There is no separate text-block mode, so a `"` inside a `"""` block can desync the scan.
- Method detection and `@OptChain` insertion are heuristic. Javadoc such as `{@code @OptChain}` is not treated as the annotation. Unusual syntax may still need a handwritten `@OptChain`.
- Weaving null-checks every instance field read and instance invoke in an `@OptChain` method, not only the `?.` chain.
- No rewrite of `INVOKESTATIC`, `INVOKESPECIAL` (constructors, `super`, private calls), or `INVOKEDYNAMIC`.
- Call arguments are still evaluated left-to-right before the receiver null check.
- No `??`, Elvis operator, or configurable defaults.
- Not published to Maven Central.

## Key types

- `io.ch.java.opt.chain.OptChain`
- `io.ch.java.opt.chain.plugin.OptChainPreprocessMojo`
- `io.ch.java.opt.chain.plugin.OptChainSourceTransformer`
- `io.ch.java.opt.chain.plugin.OptChainWeaveMojo`
- `io.ch.java.opt.chain.plugin.OptChainWeaver`
- `io.ch.java.opt.chain.demo.OptChainExamples`
- `io.ch.java.opt.chain.demo.OptChainDemoMain`
- `io.ch.java.opt.chain.idea.*` (IDEA extension points)
