# Fold and Reduce

### 0x01

`reduce`/`fold` 是函数式里比较常用的两个高阶函数，虽然这两个名字看上去有点让人不知所措，但其实并没有想象中的高深莫测。

`reduce` 即通过某种操作将 list（iterator）的所有元素组合并返回一个新的值。比如对给定 list 为 `(1,2,3,4)`，假定使用加法`+` 来组合两个元素，那么这次 reduce 操作即为：

```
(((1 + 2) + 3) + 4)
```

如果用 `op` 来表示这种操作的话，那么这个 reduce 操作即为：`op(op(op(op(1,2),3),4),5)`

更进一步，对于 n 个元素的列表，reduce 可表示为：

```
          op
         / \
        op   n
       / \  
      op  n-1
     ...
    op
   / \
  op   3
 / \
1   2

```

理解了 `reduce` 那么 `fold`就变得很简单了，fold 即为带初始值的 reduce：

```
          op
         / \
        op   n
       / \  
      op  n-1
     ...
    op
   / \
  op   2
 / \
z   1  // z 为初始值

```

### 0x02

理解了 fold 函数概念后，我们就可以写一个简单的实现。以 kotlin 为例，写一个 Map 的扩展方法来支持 `fold`:


```kotlin
inline fun <R, K, V> Map<K, V>.fold(initialValue: R,
    operation: (R, Map.Entry<K, V>) -> R): R {
  var acc = initialValue
  val iterator = entries.iterator()
  while (iterator.hasNext()) {
    acc = operation(acc, iterator.next())
  }
  return acc
}
```
fold 返回值的类型由初始值的类型决定

### 0x03

上面说到的 reduce/fold 函数时是从 list 的第一个元素遍历到最后一个元素，即从左到右的方式，这种由左至右的操作称之为 fold left，同样从右至左的方式称之为 fold right：

```
  f
 / \
1   f
   / \  
  2   f
      ...
       f
      / \
     f   n-1
    / \
   n   z      // z 为初始值
```

<!--上面提到的对列表元素求和的操作，foldLeft 的值是等于 foldRight 的，即：

```kotlin
val list = listOf(1, 2, 3, 4)
// fold 即为 foldLeft
list.fold(0) {acc, i -> acc + i} == list.foldRight(0) {i, acc -> acc + i}
```




但并不是所有的 foldLeft 操作都等于 foldRight，比如

```
val left = list.fold(0) {acc, i -> acc / i}
val right = list.foldRight(0) {acc, i -> acc / i}
```-->

