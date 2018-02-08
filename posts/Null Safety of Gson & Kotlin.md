# Null Safety of Gson & Kotlin

2017-11-07

因为 kotlin 对 java 的友好支持（编译为 java bytecode），我们可以很方便的在 kotlin 代码中使用 [Gson](https://github.com/google/gson/)：

```kotlin
data class User(val name: String, val location: String)

val user = gson.fromJson("""{"name": "twocity", "location":"Beijing"}""", User::class.java)
println(user)
```

上面代码将会输出：`User(name=twocity, location=Beijing)`，使用上跟写 Java 代码没有什么区别。

如果给定的 json 字符串缺少某个字段会是什么情况？kotlin 是 null safety的，理论上 User 的两个 property：`name`，`location` 都不可能为 null：

```kotlin
val user = gson.fromJson("""{"name": "twocity"}""", User::class.java)
println(user)
```

上面代码将会输出：`User(name=twocity, location=null)`，location 的值是 `null`！

如果 location 有默认值呢？

```kotlin
data class User(val name: String, var location: String = "Shanghai")

val user = gson.fromJson("""{"name": "twocity", "location":"Beijing"}""", User::class.java)
println(user)
```
location 的值还是 null！

要想搞清楚为什么 location 的值为 null，我们需要从两方面入手：

1. Gson 是如何初始化一个 class 的？
2. kotlin 的 null safety 是如何工作的？


## Initialization

实际上，Gson 在反序列化时，会先去找该类的 no-args constructor（无参构造函数），如果没有的话会使用 [unsafe API](http://mishadoff.com/blog/java-magic-part-4-sun-dot-misc-dot-unsafe/) 来初始化一个实例。使用 Unsafe API 来初始化会绕过类的构造函数，如果该类包含有默认值的 field 的话，该 field 的初始化也会被绕过：

```java
public class User {
  private String name = "default name";
  
  public User() {
    System.out.print("Init: ");
  }

  public String getName() {
    return name;
  }
}

User user  = User.class.newInstance();
// print Init default name
System.out.println(user.getName()); 

user = (User) unsafe.allocateInstance(User.class);
// print null
System.out.println(user.getName());
```

## Null Safety of Kotlin

首先要说明的是这里讨论的是运行时的 null safety，在编译期 kotlinc 会保证 null safety。

我们知道 kotlinc 会将 kotlin 代码编译为 java bytecode ，而 java bytecode 的定义里是没有 non-null，nullable 的，kotlinc 通过 [metadata](https://blog.twocities.me/@kotlin.metadata.html) 把这些额外的信息保留到了 bytecode。在编译的同时在需要 non-null 参数的程序入口添加 non-null check。比如上面的User，当我们在调用其构造函数时，构造函数在对 property 初始化之前会先检查传入的值是否为 null，如果为 null 就会抛出异常。插入的 non-null check 是在编译期自动完成的。

使用 `Tools -> Kotlin -> Show Kotlin Bytecode -> Decompile` 查看插入的代码：

```java
public User(@NotNull String name, @NotNull String location) {
  // 插入的 non-null check
  Intrinsics.checkParameterIsNotNull(name, "name");
  Intrinsics.checkParameterIsNotNull(location, "location");
  super();
  this.name = name;
  this.location = location;
}
```

回到文章开始，在反序列化 User 时，因为 User 并没有 no-args constructor，所以 Gson 会使用 Unsafe API 初始化一个 User 实例，并对 name 赋值，因为没有调用 User 的构造函数，所以 kotlin 的 non-null check 代码没有执行，有默认值的 property 也没有被初始化。

## Practices

那么如何处理上面这种假 null safety 呢？

+ 给所有的类添加 no-args constructor
+ 所有的字段声明为 nullable
+ 在反序列话之后执行额外的 non-null check
+ 告诉 Gson 初始化类时调用其构造函数而不是通过反射或 Unsafe API 来实现

解释：

+ 第一种方案比较简单直接，但是要额外写多余的代码，如果 model 类够多的话代码也会变得臃肿；
+ 第二种方案比较傻，会导致在使用每个字段时都要做额外的 nullable 判断（虽然 kotlin 语法足够简洁），另外如果某个字段确实不能为 null 怎么办呢？实际上如果不该为 null 的字段为 null 了，我们应当做 [fail-fast](https://www.wikiwand.com/en/Fail-fast)，而不应该将该问题延后的该字段被调用时；
+ 第三种方案则遵循了 fail-fast 原则，在实现上需要知道具体字段的 nullable 属性：在运行时可以通过 kotlin 反射 API 实现，在编译时可以通过解析 kotlin metadata 来实现；
+ 第四种方案是最理想的方式，直接调用构造函数可以避免反射带来的时间开销，调用构造函数也可以保证含有默认值property 会顺利初始化。Moshi 的 [KotlinJsonAdapterFactory](https://github.com/square/moshi#kotlin-support) 就是利用 kotlin 反射 API 在运行时调用类的构造函数来初始化的。

注：

+ kotlin 反射 API 需要额外注意，参见下面章节；
+ 第三种方案提到的运行时做法可以参考 [gson-kotlin](https://github.com/sargunv/gson-kotlin)，编译时做法可以参考本人写的 [NonNullValidator](https://github.com/twocity/NonNullValidator)

## Kotlin Reflection API

kotlin 反射 API 分为 [Lite](https://github.com/Kotlin/kotlinx.reflect.lite) 跟 [Full](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect.full/index.html)：

Lite 版本比较小，`1.0.0` 版本里包含了 `1464` 个方法，jar 包大小为 `125kb`
Full 版本相当大，`kotlin 1.1.50` 版本里包含了 `11470` 个方法，jar 包大小为 `2480kb`
详见 [Lite](http://www.methodscount.com/?lib=org.jetbrains.kotlinx%3Akotlinx.reflect.lite%3A1.0.0
) vs. [Full](http://www.methodscount.com/?lib=org.jetbrains.kotlin%3Akotlin-reflect%3A1.1.50
)
因为 Lite 版本功能比较单一，如果要在运行时获取 property 的 nullable 属性的话需要引入 full 版本，而 Full 版本除了包比较大外，还比较 [buggy](https://mp.weixin.qq.com/s/3h2d5zYfQL5KRadqVKfZ4A)，慎用！

## Java

实际上，上面提到的情况在 Java 端也会存在。如果想做到 null safety 建议使用 [AutoValue](https://github.com/google/auto/tree/master/value) + [auto-value-gson](https://github.com/rharter/auto-value-gson)，类似于第四种解决方案。

## More Readings

+ [Gson Issue-1148](https://github.com/google/gson/issues/1148)
+ [Moshi Issue-143](https://github.com/square/moshi/issues/143)
+ [Default Values & Constructors](https://github.com/square/moshi#default-values--constructors)

