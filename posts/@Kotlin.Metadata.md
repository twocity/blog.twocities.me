# @Kotlin.Metadata

*2017-10-25*

## 0x01

`@Metadata` 是 Kotlin 里比较特殊的一个注解。它记录了 Kotlin 代码元素的一些信息，比如 class 的可见性，function 的返回值，参数类型，property 的 lateinit，nullable 的属性等等。这些 Metadata 的信息由 kotlinc 生成，最终会以注解的形式存于 `.class` 文件。

```
      kotlin symbols             @Metadata
.kt  ----------------> kotlinc -------------> .class
```

之所以要把这些信息存放到 metadata 里，是因为 Kotlin 最终要编译为 java 的 bytecode，而 java bytecode 的定义里是没有 Kotlin 独有的语法信息（internal/lateinit/nullable等）的，所以要把这些信息已某种形式存放起来，也就是 `@Metadata`。


`@Metadata` 的信息会一直保留到运行时(`AnnotationRetention.RUNTIME`)，也就是说我们可以在运行时通过反射的方式获取 `@Metadata` 的信息，比如要判断某个 class 是否为 kotlin class，只需要判断它是否有 `@Metadata` 注解即可[注1]。Kotlin 的[反射 API](https://github.com/Kotlin/kotlinx.reflect.lite/blob/d08eb1586d64b2dc39fc607b23115c753c4806b5/src/main/java/kotlinx/reflect/lite/impl/impl.kt#L113) 就是基于 `@Metadata` 来实现的。

## 0x02

借助 IDE（Intellij or AndroidStudio）我们可以很方便的查看 `@Metadata` 的信息，比如一个简单的 kotlin class `class Foo {}` 然后通过 Tools -> Kotlin -> Show Kotlin Bytecode -> Decompile 即可看到 metadata 的定义：

```
   mv = {1, 1, 6},
   bv = {1, 0, 1},
   k = 1,
   d1 = {"\u0000\f\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\u0018"},
   d2 = {"Lexample/Foo;", "", "()V", "production sources for module xxx"}
)
```

完整的 `@Metadata` 包含：`k`，`mv`，`bv`，`d1`，`d2`，`xs`，`pn`，`xi`。这些参数名称都是缩略词，目的是减小最终的 class 文件体积。

__k__
即 kind，int 类型，表示该 metadata 的类型：

+ 1：Class
+ 2：File
+ 3：Synthetic class
+ 4：Multi-file class facade
+ 5：Multi-file class part

其中 4，5 的值是由 [JvmMultifileClass](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.jvm/-jvm-multifile-class/index.html) 决定的。

__mv__
即 metadata version, IntArray 类型

__bv__
即 bytecode version，IntArray 类型

__d1__ __d2__
即 data1，data2，这两个字段都是 Array<String>类型。包含了 kotlin 语法的主要信息。

__xs__
即 extra string，String 类型

__xi__
即 extra int， Int 类型

__pn__
即 fully qualified name of package，string 类型。

## 0x03

上面提到 `d1` `d2`包含了 `@Metadata` 所修饰的 class 的主要信息，其中 `d1` 是 protobuf 编码过的二进制流。其定义可参见 [descriptors.proto](https://github.com/JetBrains/kotlin/blob/e83f1b138b554209a310d6d04e810dfced4d4d29/core/deserialization/src/descriptors.proto)。

Kotlin 源码里有一个 [JvmProtoBufUtil](https://github.com/JetBrains/kotlin/blob/e83f1b138b554209a310d6d04e810dfced4d4d29/core/descriptor.loader.java/src/org/jetbrains/kotlin/serialization/jvm/JvmProtoBufUtil.kt) 的工具类可以解析 `d1` `d2` 数据，不过 kotlin 的标准库并没有这个 api，好在我们可以把 kotlin compiler 加到依赖环境里：


```groovy
  compile "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlin_version"
```

当然也可以像 [kotlin.reflect.lite]() 一样把相应的代码单独拿出来放到自己的代码库里。

```
(nameResolver, classProto) = JvmProtoBufUtil.readClassDataFrom(d1, d2)
val flags = classProto.flags
// verify class is data class
val isDataClass = Flags.IS_DATA.get(flags)
// print property names
classProto.propertyList.forEach { 
  println("Property: ${nameResolver.getString(it.name)}")
}
```

## 0x04

[kotlin-metadata-example](https://github.com/twocity/kotlin-metadata-example)


注1：*`@Metadata` 信息可以通过 `Proguard` 移除，如果要再运行时获取 metadata 信息，需要确保 `@Metadata` 没有被 proguard 删除。*

