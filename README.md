# Lancet (AGP8)

[Chinese README](README_zh.md)

> Forked from [AndrewTseZhou/lancet](https://github.com/AndrewTseZhou/lancet), which itself is a fork of [eleme/lancet](https://github.com/eleme/lancet).
>
> This fork republishes the AGP8-compatible build to JitPack for easy dependency management.

## What changed from the original Lancet

The original `eleme/lancet` uses the legacy `BaseExtension.registerTransform()` API, which was **removed in AGP 8.0**. This fork (originally adapted by AndrewTseZhou, republished here) migrates to the modern Artifacts API:

- Replaced `LancetTransform` (legacy Transform API) with `LancetTransformTask`, registered via `AndroidComponentsExtension.onVariants()` + `ScopedArtifacts.Scope.ALL` to transform classes.
- Upgraded ASM to 9.6, Guava to 32.1.3-jre, and Android tools to 8.x.
- Java 17 source compatibility.

Lancet is a lightweight AOP framework for Android.

+ Fast compilation with incremental build support.
+ Concise API — a few lines of Java to complete injection.
+ No runtime jar inserted into apk.
+ Usable from SDKs to modify dependent Apps.

## Installation

### 1. Add JitPack repository and plugin classpath

In your root `build.gradle`:

```groovy
buildscript {
    repositories {
        mavenCentral()
        google()
        maven { url "https://jitpack.io" }
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:8.1.4'
        classpath 'com.github.nullcen:lancet-plugin:1.0.7'
    }
}
```

### 2. Apply plugin and add lancet-base

In your application module's `build.gradle`:

```groovy
apply plugin: 'me.ele.lancet'

dependencies {
    compileOnly 'com.github.nullcen:lancet-base:1.0.7'
}
```

That's it. Now you can follow the tutorial below to learn how to use it.

### Tutorial

Lancet uses annotations to specify where to weave code, focusing on interacting with origin class methods and fields.

A quick example:

```java
@Proxy("i")
@TargetClass("android.util.Log")
public static int anyName(String tag, String msg){
    msg = msg + "lancet";
    return (int) Origin.call();
}
```

Key points:

* `@TargetClass` locates the target `android.util.Log`
* `@Proxy` locates the method name `i`
* `Origin.call()` will be replaced by `Log.i()` as explained above
* The effect: every `Log.i`'s second parameter `msg` will have a trailing **"lancet"**

### Choose target class

```java
public @interface TargetClass {
    String value();
    Scope scope() default Scope.SELF;
}

public @interface ImplementedInterface {
    String[] value();
    Scope scope() default Scope.SELF;
}

public enum Scope {
    SELF,
    DIRECT,
    ALL,
    LEAF
}
```

#### @TargetClass

1. **value** in `@TargetClass` should be a full class name.
2. `Scope.SELF` means the target is the class named by **value**.
3. `Scope.DIRECT` locates the direct subclasses of **value**.
4. `Scope.ALL` indicates all subclasses of **value**.
5. `Scope.LEAF` means all leaf subclasses of **value**. For example: `A <- B <- C, B <- D`, the leaf children of A are C and D.

#### @ImplementedInterface

1. **value** is a string array of full interface names; classes satisfying all conditions are chosen.
2. `Scope.SELF`: all classes implementing interfaces **literally**.
3. `Scope.DIRECT`: all classes implementing interfaces or their child interfaces **literally**.
4. `Scope.ALL`: all classes in *Scope.DIRECT* and their subclasses.
5. `Scope.LEAF`: all classes in *Scope.ALL* with no children.

![scope](media/14948409810841/scope.png)

When using `@ImplementedInterface(value = "I", scope = ...)`, targets are:

* Scope.SELF -> A
* Scope.DIRECT -> A C
* Scope.ALL -> A B C D
* Scope.LEAF -> B D

### Choose target method

#### @Proxy and @Insert

```java
public @interface Insert {
    String value();
    boolean mayCreateSuper() default false;
}

public @interface Proxy {
    String value();
}
```

1. **value** in `@Proxy` and `@Insert` is the target method name.
2. `@Proxy` hooks every invoke point of the target method.
3. `@Insert` hooks the code inside the method body.
4. `@Proxy` can be combined with `@NameRegex` to control scope; `@Insert` cannot.
5. ROM classes can't be touched at compile time, so `@Insert` won't work on them — but `@Proxy` can intercept all call sites.

`@Insert` has a `mayCreateSuper` parameter:

```java
@TargetClass(value = "android.support.v7.app.AppCompatActivity", scope = Scope.LEAF)
@Insert(value = "onStop", mayCreateSuper = true)
protected void onStop(){
    System.out.println("hello world");
    Origin.callVoid();
}
```

If `MyActivity extends AppCompatActivity` doesn't override `onStop`, Lancet creates:

```java
protected void onStop() {
    super.onStop();
}
```

then hooks it.

#### @TryCatchHandler

```java
@TryCatchHandler
@NameRegex("(?!me/ele/).*")
public static Throwable catches(Throwable t){
    return t;
}
```

Hooks every try-catch handler. `@NameRegex` restricts by class name pattern.

#### @NameRegex

Used with `@Proxy` or `@TryCatchHandler` to filter by class name (dots replaced by slashes). Only matches get hooked.

#### Method descriptor matching

The hook method must have the same descriptor and static flag as the target. Generic types and exception declarations are ignored.

##### @ClassOf

When you can't directly reference a parameter type:

```java
public class A {
    protected int execute(B b){
        return b.call();
    }
    private class B {
        int call() { return 0; }
    }
}

@TargetClass("com.dieyidezui.demo.A")
@Insert("execute")
public int hookExecute(@ClassOf("com.dieyidezui.demo.A$B") Object o) {
    System.out.println(o);
    return (int) Origin.call();
}
```

`@ClassOf` value format: `(package_name.)(outer_class_name$)class_name([]...)`, e.g. `java.lang.Object`, `java.lang.Integer[][]`, `A$B`.

### API

#### Origin

Calls the original method. Can be invoked multiple times.

- `Origin.call()` / `callThrowOne/Two/Three()` — for methods with return value.
- `Origin.callVoid()` / `callVoidThrowOne/Two/Three()` — for void methods.

`ThrowOne/Two/Three` are for deceiving the compiler to catch specific exceptions:

```java
@TargetClass("java.io.InputStream")
@Proxy("read")
public int read(byte[] bytes) throws IOException {
    try {
        return (int) Origin.<IOException>callThrowOne();
    } catch (IOException e) {
        e.printStackTrace();
        throw e;
    }
}
```

#### This

For non-static `@Insert` hooks only.

- `This.get()` — returns the target instance.
- `This.putField(Object, String)` / `This.getField(String)` — get/set any field (even private). Auto-creates if not exists.

```java
package me.ele;
public class Main {
    private int a = 1;
    public void nothing(){}
    public int getA(){ return a; }
}

@TargetClass("me.ele.Main")
@Insert("nothing")
public void testThis() {
    Log.e("debug", This.get().getClass().getName());
    This.putField(3, "a");
    Origin.callVoid();
}
```

Result:
```
E/debug: me.ele.Main
E/debug: a = 3
```

Restrictions:
* `This` cannot be used with `@Proxy`.
* Cannot access superclass fields — a new field will be created instead.

## Tips
1. Inner classes should be named `package.outer_class$inner_class`.
2. SDK developers don't need to apply the plugin, just `compileOnly 'com.github.nullcen:lancet-base:1.0.7'`.
3. Incremental compilation is supported, but `Scope.LEAF` / `Scope.ALL` or hook class edits may trigger full compilation.

## Credits

- Original project: [eleme/lancet](https://github.com/eleme/lancet)
- AGP8 adaptation: [AndrewTseZhou/lancet](https://github.com/AndrewTseZhou/lancet)
- JitPack republish: [nullcen/lancet](https://github.com/nullcen/lancet)

## License

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
