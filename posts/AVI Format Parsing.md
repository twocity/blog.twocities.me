---
title: AVI 格式解析
date: 2018-02-06
---
# AVI 格式解析

这篇文章有些过时了，毕竟 AVI 已经不是主流格式，因为工作要解析一下 AVI，整个过程还是蛮有趣的，记录之。

### RIFF

在介绍 AVI 格式之前先了解一下 RIFF 文件格式，因为 AVI 就是使用 RIFF 作为基本格式的。
RIFF 全称为 Resource Interchange File Format，即资源交错文件格式。其中 chunk 为其最小存储单位，比如 AVI，音视频会以 chunk 的形式交错排列，来实现音视频同步。

#### Chunk

chunk 格式如下：

```
type size data
```

+ type 是四个字节的 [FourCC](https://en.wikipedia.org/wiki/FourCC)，为该 chunk 的类型
+ size 四字节整数（小端序），为 data 的大小，不包含 type 及 size 的大小
+ data 该 chunk 的数据
+ 如果 data 大小为奇数，还有一个字节的 padding

#### Chunk Container

除了 chunk 外，还有一种可以包含其他 chunk 的 chunk，格式如下：

```
header size type data
```

+ header 类型为 FourCC，只可能为 `RIFF` `LIST` 中一个
+ size 四字节整数（小端序），是 type 和 data 的长度，不包括 header 和 size 的大小
+ type 类型为 FourCC，为该 chunk 的类型
+ data 即该 chunk 的数据，包含了其他 chunk

一个 RIFF 文件只能包含一个 RIFF chunk，所以一个 RIFF 文件前四个字节必然是 `RIFF`

### AVI 

前面讲到 AVI 文件是以 RIFF 格式存储的，所以 AVI 文件格式如下：

```
RIFF size AVI data ...
```
即 RIFF chunk 中的 type 类型为 AVI，data 中包含了 AVI 格式的具体信息，关于这一部分不再赘述，可以参考[MSDN](https://msdn.microsoft.com/en-us/library/windows/desktop/dd318189(v=vs.85).aspx)文档，另外推荐阅读[AVI文件详细解析](http://blog.jianchihu.net/avi-file-parse.html#comment-1260)

### 解析

#### 定义

解析前先定义好数据结构：

```kotlin
open class Chunk(val type: String, val size: Int, val buffer: BufferedSource) {
    open val totalSize = 4 + 4 + size
}
```
其中 size 的大小为 buffer 的长度，所以整个 chunk 的大小为 type 长度（4） + size 长度（4） + buffer 长度

chunk container 定义为：

```kotlin
class ChunkGroup(val header: Header, type: String,
    size: Int, buffer: BufferedSource) : Chunk(type, size, buffer) {
  override val totalSize: Int = 4 + super.totalSize 
  val chunks: List<Chunk> by lazy { scanChunks() }
}
```
ChunkGroup 长度比 Chunk 多出一个 header 的长度，所以总长度为 header 长度（4）+ super.totalSize
chunks 为包含的子 Chunk，其解析过程我们稍候定义。

因为 Chunk 结构可能会嵌套其他 Chunk，而且是层层嵌套的：被嵌套的 Chunk 可能会嵌套其他 Chunk，所以为了解析方便，这里把子 chunk 的解析分散到了各个 ChunkGroup 中。

最后定义一个 class 代表 AVI 文件，它由一个 RIFF 格式的 ChunkGroup 组成

```kotlin
class AVIFormat(val riff: ChunkGroup)
```

#### 解析

解析过程：

```kotlin
 fun parse(file: File): AVIFormat {
	  val buffer = Okio.buffer(Okio.source(file))
	  
	  val header = buffer.readFourCC()
	  val size = buffer.readIntLe()
	  val type = buffer.readFourCC()
	  val buf = Buffer()
	  buffer.readFully(buf, size.toLong() - 4)
	  val riff = ChunkGroup(RIFF, size = size - 4, type = type, buffer = buf)
	  return AVIFormat(riff)
 }
```

注：

1. 解析过程使用了 [Okio](https://github.com/square/okio)
2. size 是小端序，所以这里使用 `readIntLe()` 来读取
3. `readFourCC` 为 extension function

到此，整个 RIFF 的解析就完成了~

剩下的部分就是如何解析子 chunks：
因为我们把子 chunk 的解析分散到各个 ChunkGroup 中，所以我们在解析 ChunkGroup 时，只需解析该 chunk 包含的子 chunk， 如果解析的子 chunk 是 LIST 类型（chunk container)，那么该 chunk 的子 chunk 解析由该 chunk 自己负责。

scanChunks 简单定义如下：

```kotlin
val chunks = mutableListOf<Chunk>()
while (!buffer.exhausted()) {
    val c = buffer.readChunk()
    chunks.add(c)
}
```

`buffer.readChunk` 为 extension function：

```kotlin
private fun BufferedSource.readChunk(): Chunk {
    val header = readFourCC()
    return when (header) {
      LIST.name -> {
        val size = readIntLe()
        val type = readFourCC()
        val sink = Buffer()
        readFully(sink, size.toLong() - 4)
        ChunkGroup(header = LIST, type = type, size = size - 4, buffer = sink)
      }
      else -> {
        val size = readIntLe()
        val sink = Buffer()
        readFully(sink, size.toLong())
        Chunk(type = header, size = size, buffer = sink)
      }
    }
  }
```

这里子 chunk 的解析是借鉴了[分治](https://zh.wikipedia.org/wiki/%E5%88%86%E6%B2%BB%E6%B3%95)的思想。

#### 使用

解析拿到 chunk 之后，我们可以直接读取具体类型的 chunk 数据，解析前先定义几个辅助方法：


```kotlin
class AVIFormat(val riff: ChunkGroup) {

 private val chunks by lazy { riff.spread() }

 fun find(type: String): Chunk = findAll(type).first()

 fun findAll(type: String): List<Chunk> = chunks.filter { it.type == type }
 
 private fun ChunkGroup.spread(): List<Chunk> {
    return chunks.fold(listOf()) { acc, chunk ->
      acc.toMutableList().apply {
        add(chunk)
        if (chunk is ChunkGroup) {
          addAll(chunk.spread())
        }
      }.toList()
    }
  }
}
```

读取 avih

```kotlin
val avih = avi.find("avih")
println("type: ${avih.type}, size: ${avih.size}")
```
然后可以根据 [avih](https://msdn.microsoft.com/en-us/library/windows/desktop/dd318180(v=vs.85).aspx) 的定义继续解析 avih 的 buffer 字段

注：完整代码参见 [gist](https://gist.github.com/twocity/dce4b79884a8dfd73dd3fa4fffbb26ee)

