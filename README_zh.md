# Lancet (AGP8)

> Fork 自 [AndrewTseZhou/lancet](https://github.com/AndrewTseZhou/lancet)，其本身 fork 自 [eleme/lancet](https://github.com/eleme/lancet)。
>
> 本仓库将 AGP8 兼容版本重新发布到 JitPack，方便通过 Maven 依赖使用。

## 与原版 Lancet 的区别

原版 `eleme/lancet` 使用旧的 `BaseExtension.registerTransform()` API，该 API 在 **AGP 8.0 中已被移除**。本 fork（最初由 AndrewTseZhou 适配，此处重新发布）迁移到了新的 Artifacts API：

- 用 `LancetTransformTask` 替代了 `LancetTransform`（旧 Transform API），通过 `AndroidComponentsExtension.onVariants()` + `ScopedArtifacts.Scope.ALL` 注册类转换。
- ASM 升级到 9.6，Guava 升级到 32.1.3-jre，Android tools 升级到 8.x。
- Java 17 源码兼容。

Lancet 是一个轻量级 Android AOP 框架。

+ 编译速度快，并且支持增量编译。
+ 简洁的 API，几行 Java 代码完成注入需求。
+ 没有任何多余代码插入 apk。
+ 支持用于 SDK，可以在 SDK 编写注入代码来修改依赖 SDK 的 App。

## 接入方式

### 1. 添加 JitPack 仓库和插件 classpath

在根目录 `build.gradle` 中：

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

### 2. 应用插件并添加 lancet-base

在 app 模块的 `build.gradle` 中：

```groovy
apply plugin: 'me.ele.lancet'

dependencies {
    compileOnly 'com.github.nullcen:lancet-base:1.0.7'
}
```

完成，接下来可以按照下面的教程使用。

## 使用说明

### 示例

Lancet 使用注解来指定代码织入的规则与位置。

```java
@Proxy("i")
@TargetClass("android.util.Log")
public static int anyName(String tag, String msg){
    msg = msg + "lancet";
    return (int) Origin.call();
}
```

关键点：

* `@TargetClass` 指定目标类 `android.util.Log`
* `@Proxy` 指定目标方法 `i`
* `Origin.call()` 代表原始的 `Log.i()` 方法
* 效果：代码中所有 `Log.i(tag, msg)` 的第二个参数都会加上 **"lancet"** 后缀

### 匹配目标类

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
    SELF, DIRECT, ALL, LEAF
}
```

#### @TargetClass

1. `value` 是类的全限定名。
2. `Scope.SELF`：仅匹配 `value` 指定的类。
3. `Scope.DIRECT`：匹配 `value` 的直接子类。
4. `Scope.ALL`：匹配 `value` 的所有子类。
5. `Scope.LEAF`：匹配 `value` 的所有最终子类（叶子节点）。

#### @ImplementedInterface

1. `value` 可填多个接口全名。
2. `Scope.SELF`：直接实现所有指定接口的类。
3. `Scope.DIRECT`：直接实现指定接口及其子接口的类。
4. `Scope.ALL`：`Scope.DIRECT` 中的类及其所有子类。
5. `Scope.LEAF`：`Scope.ALL` 中的所有叶子节点。

![scope](media/14948409810841/scope.png)

使用 `@ImplementedInterface(value = "I", scope = ...)` 时，目标类为：

* Scope.SELF -> A
* Scope.DIRECT -> A C
* Scope.ALL -> A B C D
* Scope.LEAF -> B D

### 匹配目标方法

#### @Proxy

```java
public @interface Proxy {
    String value();
}
```

`@Proxy` 替换代码中所有对目标方法的调用。比如代码里有 10 处调用了 `Dog.bark()`，代理后所有 10 处都会变为 `_Lancet.xxxx.bark()`。

通常用于系统 API 的劫持——虽然不能注入代码到系统库中，但可以劫持所有调用系统 API 的地方。

#### @Insert

```java
public @interface Insert {
    String value();
    boolean mayCreateSuper() default false;
}
```

`@Insert` 将新代码插入到目标方法原有代码前后。常用于操作 App 与 library 的类，可通过 `This` 操作目标类的私有属性。

`mayCreateSuper`：当目标方法不存在时自动创建。

```java
@TargetClass(value = "android.support.v7.app.AppCompatActivity", scope = Scope.LEAF)
@Insert(value = "onStop", mayCreateSuper = true)
protected void onStop(){
    System.out.println("hello world");
    Origin.callVoid();
}
```

如果 `MyActivity` 没有重写 `onStop`，会自动创建：

```java
protected void onStop() {
    System.out.println("hello world");
    super.onStop();
}
```

注意：public/protected/private 修饰符会完全照搬 Hook 方法。

#### @TryCatchHandler

```java
@TryCatchHandler
@NameRegex("(?!me/ele/).*")
public static Throwable catches(Throwable t){
    return t;
}
```

Hook 每一个 try-catch 块。`@NameRegex` 可按类名限制范围。

#### @NameRegex

仅用于 `@Proxy` 和 `@TryCatchHandler`，按类名正则过滤（包名中的 `.` 会替换为 `/`）。

#### 方法描述符匹配

Hook 方法必须与目标方法的描述符（参数类型、返回类型）和 static 标志一致。泛型类型和异常声明可忽略。

##### @ClassOf

当无法直接引用参数类型时使用：

```java
public class A {
    protected int execute(B b){ return b.call(); }
    private class B { int call() { return 0; } }
}

@TargetClass("com.dieyidezui.demo.A")
@Insert("execute")
public int hookExecute(@ClassOf("com.dieyidezui.demo.A$B") Object o) {
    System.out.println(o);
    return (int) Origin.call();
}
```

`@ClassOf` value 格式：`(package_name.)(outer_class_name$)class_name([]...)`，如 `java.lang.Object`、`A$B`。

### API

#### Origin

调用原目标方法，可多次调用。

- `Origin.call()` / `callThrowOne/Two/Three()`：有返回值的方法。
- `Origin.callVoid()` / `callVoidThrowOne/Two/Three()`：无返回值的方法。

`ThrowOne/Two/Three` 用于欺骗编译器以捕获特定异常：

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

仅用于 `@Insert` 非静态方法。

- `This.get()`：返回目标实例。
- `This.putField(Object, String)` / `This.getField(String)`：存取目标类任意属性（包括 private），不存在则自动创建。

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

结果：
```
E/debug: me.ele.Main
E/debug: a = 3
```

限制：
* `@Proxy` 不能使用 `This`。
* 不能存取父类属性——会创建新属性。

## Tips
1. 内部类命名为 `package.outer_class$inner_class`。
2. SDK 开发者不需要 apply 插件，只需 `compileOnly 'com.github.nullcen:lancet-base:1.0.7'`。
3. 支持增量编译，但使用 `Scope.LEAF` / `Scope.ALL` 覆盖的类有变动或修改 Hook 类时，会触发全量编译。

## 致谢

- 原始项目：[eleme/lancet](https://github.com/eleme/lancet)
- AGP8 适配：[AndrewTseZhou/lancet](https://github.com/AndrewTseZhou/lancet)
- JitPack 重新发布：[nullcen/lancet](https://github.com/nullcen/lancet)

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
