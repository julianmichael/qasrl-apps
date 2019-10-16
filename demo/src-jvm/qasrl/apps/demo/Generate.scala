package qasrl.apps.demo

import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits._

import com.monovore.decline._
import com.monovore.decline.effect._

import java.nio.file.Path

import scalatags.Text.all.Frag

object Generate extends CommandIOApp(
  name = "mill demo.jvm.runMain qasrl.apps.demo.Generate",
  header = "Generate the static site that hosts the QA-SRL model demo.") {

  def main: Opts[IO[ExitCode]] = {
    val apiUrl = Opts.option[String](
      "api-url", metavar = "url:port", help = "URL to access the data server at."
    )
    val compiledDemoJS = Opts.option[Path](
      "demo-js", metavar = "path", help = "Path to compiled JS file for the demo webapp."
    )
    val compiledDemoJSDeps = Opts.option[Path](
      "demo-jsdeps", metavar = "path", help = "Path to aggregated JS deps file for the demo webapp."
    )
    val siteRoot = Opts.option[Path](
      "site-root", metavar = "path", help = "Root directory in which to place the generated website."
    )
    val useLocalLinks = Opts.flag(
      "local-links", help = "Use links to site-local versions of Bootstrap dependencies"
    ).orFalse

    (apiUrl, compiledDemoJS, compiledDemoJSDeps, siteRoot, useLocalLinks)
      .mapN(program)
  }

  def program(
    apiUrl: String,
    compiledDemoJS: Path,
    compiledDemoJSDeps: Path,
    siteRoot: Path,
    useLocalLinks: Boolean
  ): IO[ExitCode] = {

    val demoScriptSiteLocation = "scripts/demo.js"
    val demoDepsSiteLocation = "scripts/demo-deps.js"

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
        demoScriptDepsLocation = demoDepsSiteLocation,
        demoScriptLocation = demoScriptSiteLocation,
        siteRoot = siteRoot
      )
    }

    val htmlFiles: Map[Frag, String] = Map(
      pages.Index(config) -> "index.html",
      pages.Error(config) -> "error.html"
    )

    val jsFiles: Map[Path, String] = Map(
      compiledDemoJS -> demoScriptSiteLocation,
      compiledDemoJSDeps -> demoDepsSiteLocation
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
      ExitCode.Success
    }
  }
}
