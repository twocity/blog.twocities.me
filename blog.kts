#!/usr/bin/env kscript
import okio.Okio
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Options
import org.commonmark.ext.front.matter.YamlFrontMatterExtension
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor
import org.commonmark.parser.Parser
import java.io.File
import java.nio.file.Paths
import org.commonmark.renderer.html.HtmlRenderer
import org.yaml.snakeyaml.Yaml
import java.io.FileReader
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

//DEPS com.squareup.okio:okio:1.13.0
//DEPS commons-cli:commons-cli:1.4
//DEPS com.atlassian.commonmark:commonmark:0.11.0
//DEPS com.atlassian.commonmark:commonmark-ext-yaml-front-matter:0.11.0
//DEPS org.yaml:snakeyaml:1.19

val pwd = Paths.get("").toAbsolutePath()!!
val assets = pwd.resolve("assets")!!
val postPath = pwd.resolve("posts")!!
val templatePath = pwd.resolve("template")!!
val blogPath = pwd.resolve("blog")!!
val config by lazy {
  Yaml().load<Map<String, String>>(FileReader(pwd.resolve("config.yaml").toFile()))!!
}
val gaId = config["ga"]!!
val blogUrl = config["url"]!!

val gaScript by lazy {
  val gaTemplate = File(templatePath.toFile(), "ga.html").readAsString()
  String.format(gaTemplate, gaId, gaId)
}

val helpHeader = """
A simple and tiny tool to generate static blog for https://blog.twocities.me

"""
val helpFooter = """
Author    : twocity
License   : MIT
Website   : https://github.com/twocity/blog.twocities.me

"""
val options = Options().apply {
  addOption("build", false, "generate posts at `blog` folder")
  addOption("clean", false, "clean all the generated files")
  addOption("help", false, "print help information")
}

val formatter = HelpFormatter()
val command = DefaultParser().parse(options, args, true)

when {
  command.hasOption("clean") -> clean()
  command.hasOption("build") -> build()
  command.hasOption("help") -> formatter.printHelp("kscript blog.kts", helpHeader, options,
      helpFooter,
      true)
  else -> println("wrong command, use -help for more information.")
}

fun clean() {
  blogPath.toFile().apply {
    if (exists()) {
      if (deleteRecursively()) println("clean done") else println("delete files failed")
    }
  }
}

fun build() {
  println("scanning posts...")
  blogPath.toFile().apply { if (!exists()) mkdir() }
  val posts = postPath.toFile().listFiles { file ->
    !file.isDirectory && !file.isHidden && file.extension == "md"
  }.map { MarkdownParser.parse(it) }
      .sortedByDescending { it.property.date }

  // generate index.html
  println("generating blog/index.html")
  val indexTemplate = File(templatePath.toFile(), "index.html").readAsString()
  val output = File(blogPath.toFile(), "index.html")
  Okio.buffer(Okio.sink(output)).use {
    val items = posts.joinToString(
        separator = "\n") {
      "<li><a href=${it.htmlFileName}>${it.property.title}</a>  @${it.property.formattedDate}</li>"
    }
    it.writeUtf8(String.format(indexTemplate, gaScript, items))
  }

  // generate posts html
  val postTemplate = File(templatePath.toFile(), "post.html").readAsString()
  posts.forEach {
    println("generating ${it.htmlFileName}")

    val disqusTemplate = File(templatePath.toFile(), "disqus.html").readAsString()
    val disqus = String.format(disqusTemplate, blogUrl + it.htmlFileName, it.htmlFileName)
    val html = it.html
    Okio.buffer(Okio.sink(File(blogPath.toFile(), it.htmlFileName))).use {
      it.writeUtf8(String.format(postTemplate, gaScript, html, disqus))
    }
  }
  // copy assets
  Files.walkFileTree(assets, CopyVisitor(assets, blogPath.resolve("assets")))
}

object MarkdownParser {
  private val extensions = setOf(YamlFrontMatterExtension.create())
  private val parser = Parser.builder()
      .extensions(extensions)
      .build()
  private var renderer = HtmlRenderer.builder().extensions(extensions).build()

  fun parse(file: File): Post {
    println("render: ${file.name}")
    val content = Okio.buffer(Okio.source(file)).readUtf8()
    val visitor = YamlFrontMatterVisitor()
    val document = parser.parse(content)
        .apply { accept(visitor) }
    val property = visitor.data
    val html = renderer.render(document)
    return Post(file, html, Property.from(file, property))
  }
}

class Post(private val markdownFile: File, val html: String, val property: Property) {
  val htmlFileName by lazy {
    val name = markdownFile.nameWithoutExtension.toLowerCase(Locale.US)
        .replace(' ', '-')
    "$name.html"
  }
}

data class Property(val title: String, val date: Date) {
  val formattedDate: String by lazy {
    val formatter = SimpleDateFormat("yyyy-MM-dd")
    formatter.format(date)
  }

  companion object {
    fun from(file: File, map: Map<String, List<String>>): Property {
      val pMap = map.filterValues { it.isNotEmpty() }.mapValues { it.value.first() }
      val title = pMap.getOrDefault("title", file.nameWithoutExtension)
      val date =
          if (pMap.containsKey("date")) {
            val formatter = SimpleDateFormat("yyyy-MM-dd")
            formatter.parse(pMap["date"])
          } else {
            val attr = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            Date(attr.creationTime().toMillis())
          }
      return Property(title = title, date = date)
    }
  }
}

fun File.readAsString(): String = Okio.buffer(Okio.source(this)).readUtf8()

class CopyVisitor(private val source: Path, private val target: Path) : SimpleFileVisitor<Path>() {

  override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
    val targetDir = target.resolve(source.relativize(file))
    Files.copy(file, targetDir, StandardCopyOption.REPLACE_EXISTING)
    return CONTINUE
  }

  override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
    val targetDir = target.resolve(source.relativize(dir))
    if (!Files.exists(targetDir)) {
      Files.createDirectory(targetDir)
    }
    return CONTINUE
  }
}
