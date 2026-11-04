# java-opt-chain

Compile-time optional chaining for Java, inspired by JavaScript's `?.` operator.

Standard `javac` does not accept `?.`. This project adds a **two-step Maven pipeline** so you can write source that looks like JS optional chaining, then turn it into null-safe bytecode:

1. **`opt-chain:preprocess`** (`generate-sources` / `generate-test-sources`) — If a source root contains `?.`, mirror the tree into `target/generated-sources/opt-chain`, wrap each `?.` receiver in `OptChainOps.opt(...)`, inject `@OptChain(precise = true)` when needed, and **replace** the compile source root so `javac` never sees illegal syntax.
2. **`opt-chain:weave`** (`process-classes`) — Use ASM to null-check only those marked receivers (`GETFIELD` / `ARRAYLENGTH` / instance invokes). A plain `.` in the same method still throws `NullPointerException` (reference → `null`, primitives → typed zero / `false`; see [Short-circuit defaults](#short-circuit-defaults)).

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

- **groupId:** `io.github.chensirj`
- **version:** `0.2.0-SNAPSHOT`
- **Java:** 17+
- **License:** Apache-2.0 (`LICENSE`). Publishing steps are in [RELEASING.md](RELEASING.md). The demo module is not published.

The IDEA plugin is **not** part of the Maven reactor; `mvn test` only builds the annotation, plugin, and demo modules.

## `@OptChain`

```java
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface OptChain {
    boolean precise() default false;
}
```

You usually do **not** write the annotation by hand. When preprocess finds `?.` inside a method or constructor, it adds `@OptChain(precise = true)`.

- **`precise = true`** (what preprocess writes): null-check only receivers wrapped in `OptChainOps.opt`. If this method's bytecode has no marker — the `?.` moved into a lambda or another class — the method is left unchanged.
- **`precise = false`** (the default, including a hand-written `@OptChain` with no attribute and no markers): legacy mode. Every instance field read and instance call in that method is null-checked, which is what 0.1.x did.

If a method contains `OptChainOps.opt` calls, those markers win and weaving is precise even when the annotation says `precise = false`.

## Source preprocess

- Goal: `opt-chain:preprocess`
- Phases: `generate-sources` (main), `generate-test-sources` (tests, with `processTestSources=true`)
- Behavior: outside comments and string/char literals, `a?.b` becomes `io.github.chensirj.optchain.OptChainOps.opt(a).b` (calls and fields). Methods and constructors whose bodies contain a marker get `@OptChain(precise = true)` and the import. The generated tree replaces the compile root.
- Skip: `-Doptchain.preprocess.skip=true`
- A source root with no `?.` is left unchanged (debug log). A read, write, or transform failure names the file path relative to that root.

`OptChainOps.opt` is an identity method so the generated source compiles. Weave deletes the call. If weave is skipped, the call returns its argument and a later plain dereference still throws.

Example authoring style (demo). Getter chains and field chains are the same rewrite:

```java
public String cityOf(User u) {
    return u?.getAddress()?.getCity()?.getName();
}

public String fieldCityName(FieldUser u) {
    return u?.address?.city?.name;
}
```

## How a `?.` site reaches the weaver

Preprocess does **not** rely on line and column numbers. It wraps the receiver, which is a real `invokestatic OptChainOps.opt` in whatever method `javac` emits. Weave runs an ASM analyzer, remembers values that came from `opt`, null-checks the `GETFIELD` / `ARRAYLENGTH` / instance call that consumes that value (including through `checkcast` and through arguments that are evaluated first), then **removes** the `opt` call.

That survives cases a line-number table cannot name reliably:

| Approach | What goes wrong |
|----------|-----------------|
| **Chosen: `OptChainOps.opt(receiver)`** | An extra static call exists only until weave. The marker sits in the method that contains the expression, so lambdas, anonymous classes, constructors, and `<clinit>` keep their own sites. Argument order does not matter: `a?.foo(b.foo())` evaluates the inner call first, and only `a`'s call is marked. |
| Line + member-name ordinal in `@OptChain(sites=...)` | `LineNumberTable` has no column. The same name on one line is reordered when an argument calls it (`a?.foo(b.foo())`). One source line can also be split across the enclosing method and a `lambda$...` method, so a file-wide ordinal does not match either method's local count. |
| Null-check every instance access in the method | This is the 0.1.x behavior. A plain `.` in an `@OptChain` method would not throw. It remains available for a hand-written `@OptChain` with no markers. |

## ASM weave

- Goal: `opt-chain:weave`
- Phase: `process-classes`
- Precise behavior: a null **marked** receiver skips that access or call and pushes a typed default. Unmarked reads and calls are unchanged, so a plain `.` throws `NullPointerException`.
- Legacy behavior: a hand-written `@OptChain` with no markers still wraps every `GETFIELD`, `INVOKEVIRTUAL`, and `INVOKEINTERFACE` in that method.
- Skip: `-Doptchain.skip=true`
- Classes with nothing to rewrite are left unchanged (debug log). The info log reports how many class files were scanned and how many were rewritten. An ASM or I/O failure names the class path relative to the output directory.

`INVOKESTATIC` is not a `?.` site (`obj?.staticMethod()` is compiled as "evaluate and discard the receiver, then `invokestatic`", so it does not short-circuit). `INVOKEDYNAMIC` (string concat, the lambda bootstrap) is not rewritten; the lambda **body** is a real method and is woven when it contains a marker. Precise mode does rewrite a marked `INVOKESPECIAL` that is not `<init>` (a private instance call). Legacy mode still skips `INVOKESPECIAL`, matching 0.1.x.

Argument expressions are still evaluated in Java order, before the receiver null check. `a?.child(bump())` calls `bump()` even when `a` is null. JavaScript would skip the arguments.

### Chain semantics (not full JavaScript)

Each `?.` null-checks only its own receiver. The rest of the expression still runs.

| Source | `a == null` |
|--------|-------------|
| `a?.b()?.c()` | result is `null` (or `0` / `false`); nothing throws |
| `a?.b().c()` | `b()` is skipped and yields `null`, then the plain `.c()` **throws** `NullPointerException` |
| `a.b()` in the same method | **throws** `NullPointerException` |

JavaScript would skip the entire chain in `a?.b().c()`. This project does not: a plain `.` means "this receiver must be non-null". Write `?.` on every link that should short-circuit. `a?.b()?.c()` is the form that matches the older all-sites demos.

The same rule applies to fields (`u?.address.city` throws if `address` is null) and to several chains on one line (`a?.name() + b?.name()` short-circuits each chain on its own).

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
  <groupId>io.github.chensirj</groupId>
  <artifactId>opt-chain-annotations</artifactId>
  <version>0.2.0-SNAPSHOT</version>
</dependency>
```

2. Bind the plugin (preprocess + weave):

```xml
<plugin>
  <groupId>io.github.chensirj</groupId>
  <artifactId>opt-chain-maven-plugin</artifactId>
  <version>0.2.0-SNAPSHOT</version>
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

`mvn test` runs `opt-chain-demo` (woven `?.` behavior for getters, public fields, precise vs plain dots, static methods, lambdas, nested classes, constructors, and a hand-written legacy `@OptChain`) and `opt-chain-maven-plugin` unit tests (source rewrite, plus an in-memory compile/weave harness). The IDEA plugin is a separate Gradle build.

To build source and javadoc jars without signing or uploading, see [RELEASING.md](RELEASING.md):

```bash
mvn -B -P release -Dgpg.skip package
```

Runnable main (`OptChainDemoMain`) prints four city/name cases (full chain, null address, null city, null user), the primitive short-circuit defaults, the matching public-field chains, and a precise-site check (`plainCity(null)` throws, optional chains do not):

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

`mainClass=io.github.chensirj.optchain.demo.OptChainDemoMain`

Alternative after `package`/`test`:

```bash
java -cp opt-chain-demo/target/classes:opt-chain-annotations/target/classes \
  io.github.chensirj.optchain.demo.OptChainDemoMain
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

There is no prebuilt zip in the repository. Build one with `./gradlew buildPlugin`; the archive is `opt-chain-idea/build/distributions/opt-chain-idea-0.2.0-SNAPSHOT.zip` (that directory is Gradle output and is not committed). Plugin id is `io.github.chensirj.optchain.idea`.

### Install

1. IntelliJ IDEA **2024.1+** (sinceBuild 241); verified path includes **2025.2** (IU-252)
2. If an older Opt Chain is installed, uninstall it first. The previous id was `io.ch.java.opt.chain.idea`; IDEA treats that as a different plugin, so leaving it installed keeps the old highlighters loaded.
3. Settings → Plugins → ⚙️ → **Install Plugin from Disk…** → select the zip from `build/distributions/`
4. Restart IDE
5. If `?.` still shows red: **File → Invalidate Caches → Invalidate and Restart**

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
- **0.2.0** changes the plugin id and Java package to `io.github.chensirj.optchain.idea`. Uninstall id `io.ch.java.opt.chain.idea` before installing this build. Editor behavior matches 0.1.2 (shared field/method completion and navigation).
- Requires the Java plugin (`com.intellij.modules.java`)
- Eclipse / VS Code are out of scope

## Where `?.` is woven

Markers travel with the bytecode, so the annotation on the enclosing method is not required for the synthetic method itself:

| Location | Works? |
|----------|--------|
| Instance methods | yes |
| Static methods | yes |
| Lambdas (`lambda$...` methods) | yes |
| Anonymous classes and inner / local classes | yes |
| Constructors | yes |
| Instance field initializers and instance initializer blocks (copied into each `<init>`) | yes |
| Static field initializers (`<clinit>`) | yes |

`new Foo()?.bar` optional-checks `bar` only. `new` itself never yields null, and `<init>` is not rewritten.

## Known limitations (compiler pipeline)

- Without the IDEA plugin, the editor still flags `?.` as illegal syntax. Maven preprocess is what `javac` compiles.
- Preprocess is a text scan, not a Java parser. `?.` outside line comments, block comments, and string/char literals is rewritten, whether the next token is a field (`u?.address?.city?.name`) or a call; those spots are covered by `OptChainSourceTransformerTest`. There is no separate text-block mode, so a `"` inside a `"""` block can desync the scan.
- The receiver walk understands calls, indexes, parenthesized expressions, generic `name<...>`, qualified names, and `new`. It can miss a receiver that is not a postfix expression (for example a lambda used as the receiver).
- Method detection for inserting `@OptChain` is heuristic. Javadoc such as `{@code @OptChain}` is not treated as the annotation. A missed annotation is still safe when the marker is present: weave keys off `OptChainOps.opt`, not only the annotation.
- `a?.b().c()` does **not** skip `.c()` (see the table above).
- `obj?.staticMethod()` does not short-circuit.
- Call arguments are still evaluated left-to-right before the receiver null check.
- No `??`, Elvis operator, or configurable defaults.
- Maven Central publishing is prepared (`-P release`) but this tree is not uploaded by the default build. See [RELEASING.md](RELEASING.md).

## Key types

- `io.github.chensirj.optchain.OptChain`
- `io.github.chensirj.optchain.OptChainOps`
- `io.github.chensirj.optchain.plugin.OptChainPreprocessMojo`
- `io.github.chensirj.optchain.plugin.OptChainSourceTransformer`
- `io.github.chensirj.optchain.plugin.OptChainWeaveMojo`
- `io.github.chensirj.optchain.plugin.OptChainWeaver`
- `io.github.chensirj.optchain.demo.OptChainExamples`
- `io.github.chensirj.optchain.demo.OptChainPreciseExamples`
- `io.github.chensirj.optchain.demo.OptChainDemoMain`
- `io.github.chensirj.optchain.idea.*` (IDEA extension points)
