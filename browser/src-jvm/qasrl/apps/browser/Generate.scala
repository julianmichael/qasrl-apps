package qasrl.apps.browser

import cats.effect.IO
import cats.implicits._

import com.monovore.decline._

import java.nio.file.Path

import scalatags.Text.all.Frag

object Generate {

  def main(args: Array[String]): Unit = {
    val command = Command(
      name = "mill browser.jvm.runMain qasrl.apps.browser.Generate",
      header = "Generate the static site that hosts the QA-SRL browser."
    ) {
      val qasrlBankPath = Opts.option[Path](
        "qasrl-bank", metavar = "path", help = "Path to the QA-SRL Bank 2.0 data."
      )
      val apiUrl = Opts.option[String](
        "api-url", metavar = "url:port", help = "URL to access the data server at."
      )
      val compiledBrowserJS = Opts.option[Path](
        "browser-js", metavar = "path", help = "Path to compiled JS file for the browser webapp."
      )
      val compiledBrowserJSDeps = Opts.option[Path](
        "browser-jsdeps", metavar = "path", help = "Path to aggregated JS deps file for the browser webapp."
      )
      val siteRoot = Opts.option[Path](
        "site-root", metavar = "path", help = "Root directory in which to place the generated website."
      )
      val useLocalLinks = Opts.flag(
        "local-links", help = "Use links to site-local versions of Bootstrap dependencies"
      ).orFalse

      (qasrlBankPath, apiUrl, compiledBrowserJS, compiledBrowserJSDeps, siteRoot, useLocalLinks)
        .mapN(program)
    }
    val result = command.parse(args) match {
      case Left(help) => IO { System.err.println(help) }
      case Right(main) => main
    }
    result.unsafeRunSync
  }

  def program(
    qasrlBankPath: Path,
    apiUrl: String,
    compiledBrowserJS: Path,
    compiledBrowserJSDeps: Path,
    siteRoot: Path,
    useLocalLinks: Boolean
  ): IO[Unit] = {

    val browserScriptSiteLocation = "scripts/browser.js"
    val browserDepsSiteLocation = "scripts/browser-deps.js"
    val indexJSLocation = "scripts/data-meta-index.js"

    sealed trait LinkType; case object CSSLink extends LinkType; case object JSLink extends LinkType
    case class LinkForDownload(
      remoteUrl: String,
      localLocation: String,
      integrity: String,
      linkType: LinkType
    ) {
      import scalatags.Text.all._
      def makeTag(isLocal: Boolean) = linkType match {
        case CSSLink => link(
          rel := "stylesheet",
          href := (if(isLocal) localLocation else remoteUrl),
          Option(attr("integrity") := integrity).filter(_ => !isLocal),
          Option(attr("crossorigin") := "anonymous").filter(_ => !isLocal)
        )
        case JSLink => script(
          src := (if(isLocal) localLocation else remoteUrl),
          Option(attr("integrity") := integrity).filter(_ => !isLocal),
          Option(attr("crossorigin") := "anonymous").filter(_ => !isLocal)
        )
      }
    }

    val (bootstrapLink, bootstrapScripts) = {
      val bootstrapLink = LinkForDownload(
        "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/css/bootstrap.min.css",
        "css/bootstrap.min.css",
        "sha384-Gn5384xqQ1aoWXA+058RXPxPg6fy4IWvTNh0E263XmFcJlSAwiGgFAW/dAiS6JXm",
        CSSLink
      )
      val scriptLinks = List(
        LinkForDownload(
          "https://code.jquery.com/jquery-3.2.1.slim.min.js",
          "scripts/jquery-3.2.1.slim.min.js",
          "sha384-KJ3o2DKtIkvYIK3UENzmM7KCkRr/rE9/Qpg6aAZGJwFDMVNA/GpGFF93hXpG5KkN",
          JSLink
        ),
        LinkForDownload(
          "https://cdnjs.cloudflare.com/ajax/libs/popper.js/1.12.9/umd/popper.min.js",
          "scripts/popper.min.js",
          "sha384-ApNbgh9B+Y1QKtv3Rn7W3mgPxhU9K/ScQsAP7hUibX39j7fakFPskvXusvfa0b4Q",
          JSLink
        ),
        LinkForDownload(
          "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0/js/bootstrap.min.js",
          "scripts/bootstrap.min.js",
          "sha384-JZR6Spejh4U02d8jOt6vLEHfe/JQGiRRSQQxSfFWpi1MquVdAyjUar5+76PVCmYl",
          JSLink
        ),
      )
      (
        bootstrapLink,
        scriptLinks
      )
    }

    val config = {
      import scalatags.Text.all._
      GenerationConfig(
        apiUrl = apiUrl,
        bootstrapLink = bootstrapLink.makeTag(useLocalLinks),
        bootstrapScripts = div(bootstrapScripts.map(_.makeTag(useLocalLinks))),
        dataMetaIndexLocation = indexJSLocation,
        browserScriptDepsLocation = browserDepsSiteLocation,
        browserScriptLocation = browserScriptSiteLocation,
        siteRoot = siteRoot
      )
    }

    val htmlFiles: Map[Frag, String] = Map(
      pages.Index(config) -> "index.html",
      pages.Error(config) -> "error.html"
    )

    val jsFiles: Map[Path, String] = Map(
      compiledBrowserJS -> browserScriptSiteLocation,
      compiledBrowserJSDeps -> browserDepsSiteLocation
    )

    val jsonToJSFiles: Map[(Path, String), String] = Map(
      (qasrlBankPath.resolve("index.json.gz"), "dataMetaIndex") -> indexJSLocation
    )

    IO {

      import java.nio.file.Files
      import java.nio.file.{StandardCopyOption => Copy}
      import java.nio.charset.{StandardCharsets => Charsets}

      def writeFile(path: Path, content: String): Unit = {
        Files.createDirectories(path.getParent)
        Files.write(path, content.getBytes("utf-8"))
      }
      def copyFile(origin: Path, target: Path): Unit = {
        Files.createDirectories(target.getParent)
        Files.copy(origin, target, Copy.REPLACE_EXISTING)
      }

      def downloadFileToSite(url: String, location: String) = {
        val targetPath = siteRoot.resolve(location)
        if(!Files.exists(targetPath)) {
          Files.createDirectories(targetPath.getParent)
          import sys.process._
          s"curl -o $targetPath $url".!
        }
      }

      if(useLocalLinks) {
        (bootstrapLink :: bootstrapScripts).foreach { link =>
          downloadFileToSite(link.remoteUrl, link.localLocation)
        }
      }

      htmlFiles.mapValues(siteRoot.resolve).foreach { case (html, path) =>
        writeFile(path, "<!doctype html>\n" + html.render)
        System.out.println(s"Wrote $path")
      }

      jsFiles.mapValues(siteRoot.resolve).foreach { case (origin, target) =>
        copyFile(origin, target)
        System.out.println(s"Copied $origin to $target")
      }

      jsonToJSFiles.mapValues(siteRoot.resolve).foreach { case ((origin, varName), target) =>
        import scala.collection.JavaConverters._
        val jsonString = if(origin.toString.endsWith(".gz")) {
          import java.io.FileInputStream
          import java.util.zip.GZIPInputStream
          scala.io.Source.fromInputStream(
            new GZIPInputStream(new FileInputStream(origin.toString))
          ).getLines.mkString("\n")
        } else {
          Files.readAllLines(origin, Charsets.UTF_8).iterator.asScala.mkString("\n")
        }
        writeFile(target, s"var $varName = " + jsonString)
        System.out.println(s"Wrote $origin to $target as $varName")
      }
    }
  }
}
