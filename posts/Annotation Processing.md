# Annotation Processing

*2017-10-13*

JDK1.5 推出注解（Annotations）后，[JSR269](https://www.jcp.org/en/jsr/detail?id=269) 也相继提出，目的是可以在编译期获取并处理源码中定义的注解，也就是我们说的 annotation processing，全称是：Pluggable Annotation Processing API。Annotation Processing 一个通常的用法是根据定义的注解来生成 java 代码以减少样板代码：众所都知，java 语法实在繁琐。

一个著名的例子就是 [AutoValue](https://github.com/google/auto/blob/master/value/userguide/index.md)：

```java
@AutoValue
abstract class Animal {
  static Animal create(String name, int numberOfLegs) {
    // See "How do I...?" below for nested classes.
    return new AutoValue_Animal(name, numberOfLegs);
  }

  abstract String name();
  abstract int numberOfLegs();
}
```

在编译期 annotation processor 会生成一个 `Animal` 的[子类](https://github.com/google/auto/blob/master/value/userguide/generated-example.md)，包括 `getters`, `equals`, `hashCode`，及 `Animal` 中抽象方法的实现。需要注意的是，这里生成的是 `Animal` 的子类，而不是在 `Animal` 的基础上修改，这是因为 annotation processor 只能生成新的代码，而不能修改现有的，这也是 `Animal` 为什么是 abstract的原因。

当然，Annotation Processing 的主要用途是生成代码，包括样板代码但并不限于此。另一个例子便是 [Dagger(2)](https://github.com/google/dagger)

Dagger 是一个[依赖注入](https://martinfowler.com/articles/injection.html)（Dependency Injection）框架。像早期的依赖注入框架 [Guice](https://github.com/google/guice) 都是在运行时通过反射的方式处理注解，这会导致几个问题：

1. 资源消耗
2. 运行时报错

因为在运行时处理程序的依赖关系，应用的依赖关系越复杂，那么依赖关系的处理就会消耗越多的 CPU 及 RAM；如果依赖关系配置错误（比如，dependency not satisfied）那只能在运行时报错。可以想象一下如果我们在 Android 工程里使用这种运行时依赖注入框架会是什么结果：应用启动太慢，卡顿，甚至是 crash。所以，在 Android 的官方文档里是避免使用这种框架的：

>Avoid dependency injection frameworks

>Using a dependency injection framework such as Guice or RoboGuice may be attractive because they can simplify the code you write and provide an adaptive environment that's useful for testing and other configuration changes. However, these frameworks tend to perform a lot of process initialization by scanning your code for annotations, which can require significant amounts of your code to be mapped into RAM even though you don't need it. These mapped pages are allocated into clean memory so Android can drop them, but that won't happen until the pages have been left in memory for a long period of time.

*Note：原文已经修改为 [Use Dagger 2 for dependency injection](https://developer.android.com/topic/performance/memory.html)*

Dagger(2) 的做法是借助于 annotation processing，在编译期静态分析整个应用的依赖关系，如果有错误的配置（用法）在编译时抛出，并生成相应的『注入』代码，让整个依赖注入过程看上去像是某种 magic，但实际上相当于手写了所有依赖注入的代码。当然，实际的过程要复杂的多，感兴趣的话可以看一下 Dagger 的[文档](https://google.github.io/dagger/users-guide)

上面说到 Dagger 的做法是把本来在运行时的操作前置到了编译期。这种把运行时转换为编译时的做法，个人认为，是Annotation Processing 这种技术的魅力所在。

而 Annotation Processing 这种既可以避免写样板代码，又可以减少运行时损耗提高性能的特性在 Android 社区也越来越流行。像早期的 [ButterKnife](https://github.com/JakeWharton/butterknife)，及 Android 官方最近（2017年 Google I/O）推出的 [Room](https://android.googlesource.com/platform/frameworks/support/+/master/room)，[Lifecycle](https://android.googlesource.com/platform/frameworks/support/+/master/lifecycle/) 都是基于 annotation processing。
