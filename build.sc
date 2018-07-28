import mill._, mill.scalalib._, mill.scalalib.publish._, mill.scalajslib._
import mill.scalalib.scalafmt._
import coursier.maven.MavenRepository
import ammonite.ops._

val thisScalaVersion = "2.12.6"
val thisScalaJSVersion = "0.6.23"

val macroParadiseVersion = "2.1.0"
val kindProjectorVersion = "0.9.4"

// cats libs -- maintain version agreement or whatever
val catsVersion = "1.1.0"
val catsEffectVersion = "0.10.1"
val nlpdataVersion = "0.2.0"
val qasrlVersion = "0.1.0"
val qasrlBankVersion = "0.1.0"
val radhocVersion = "0.1.0"
val circeVersion = "0.9.3"
val http4sVersion = "0.18.14"
val declineVersion = "0.4.2"
val monocleVersion = "1.5.1-cats"

val scalatagsVersion = "0.6.7"
val scalacssVersion = "0.5.3"

val ammoniteOpsVersion = "1.1.2"
val logbackVersion = "1.2.3"

val scalajsDomVersion = "0.9.6"
val scalajsJqueryVersion = "0.9.3"
val scalajsReactVersion = "1.1.0"
val scalajsScalaCSSVersion = "0.5.3"

trait CommonModule extends ScalaModule with ScalafmtModule {

  def scalaVersion = thisScalaVersion

  def scalacOptions = Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:higherKinds",
    "-Ypartial-unification"
  )
  def scalacPluginIvyDeps = super.scalacPluginIvyDeps() ++ Agg(
    ivy"org.scalamacros:::paradise:$macroParadiseVersion",
    ivy"org.spire-math::kind-projector:$kindProjectorVersion"
  )

  // add back in when necessary
  // def repositories = super.repositories ++ Seq(
  //   MavenRepository("https://oss.sonatype.org/content/repositories/snapshots")
  // )
}

trait CrossPlatformModule extends ScalaModule {
  def platformSegment: String

  def sources = T.sources(
    millSourcePath / "src",
    millSourcePath / s"src-$platformSegment"
  )

}

trait JvmPlatform extends CrossPlatformModule {
  def platformSegment = "jvm"

  // for using runMain in commands
  def runMainFn = T.task { (mainClass: String, args: Seq[String]) =>
    import mill.modules.Jvm
    import mill.eval.Result
    try Result.Success(
      Jvm.interactiveSubprocess(
        mainClass,
        runClasspath().map(_.path),
        forkArgs(),
        forkEnv(),
        args,
        workingDir = ammonite.ops.pwd
      )
    ) catch {
      case e: InteractiveShelloutException =>
        Result.Failure("subprocess failed")
    }
  }

}

trait JsPlatform extends CrossPlatformModule with ScalaJSModule {
  def scalaJSVersion = T(thisScalaJSVersion)
  def platformSegment = "js"
}

trait QASRLBrowserModule extends CommonModule {
  def millSourcePath = build.millSourcePath / "browser"

  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"org.typelevel::cats-core::$catsVersion",
    ivy"org.julianmichael::qasrl::$qasrlVersion",
    ivy"org.julianmichael::qasrl-bank::$qasrlBankVersion",
    ivy"org.julianmichael::qasrl-bank-service::$qasrlBankVersion"
  )
}

import $file.scripts.SimpleJSDepsBuild, SimpleJSDepsBuild.SimpleJSDeps
import $file.scripts.ScalatexBuild, ScalatexBuild.ScalatexModule

trait CoreModule extends CommonModule {
  def millSourcePath = build.millSourcePath / "core"
}

object core extends Module {
  object jvm extends CoreModule with JvmPlatform
  object js  extends CoreModule with JsPlatform
}

object browser extends Module {
  object jvm extends QASRLBrowserModule with JvmPlatform with ScalatexModule {

    def moduleDeps = Seq(core.jvm)

    override def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.github.japgolly.scalacss::core:$scalacssVersion",
      ivy"com.github.japgolly.scalacss::ext-scalatags:$scalacssVersion",
      ivy"com.monovore::decline::$declineVersion",
      ivy"org.http4s::http4s-dsl::$http4sVersion",
      ivy"org.http4s::http4s-blaze-server::$http4sVersion",
      ivy"ch.qos.logback:logback-classic:$logbackVersion"
    )

    def generateDev(
      qasrlBankLocation: Path,
      siteRoot: Path,
      port: Int,
      domain: String = "localhost",
    ) = T.command {
      val browserJSPath = browser.js.fastOpt().path
      val browserJSDepsPath = browser.js.aggregatedJSDeps().path
      val runMain = runMainFn()
      runMain(
        "qasrl.apps.browser.Generate", Seq(
          "--qasrl-bank",      qasrlBankLocation.toString,
          "--api-url",         s"http://$domain:$port",
          "--browser-js",      browserJSPath.toString,
          "--browser-jsdeps",  browserJSDepsPath.toString,
          "--site-root",       siteRoot.toString,
          "--local-links"
          )
      )
    }

    def generateProd(
      qasrlBankLocation: Path,
      siteRoot: Path,
      port: Int,
      domain: String,
    ) = T.command {
      val browserJSPath = browser.js.fullOpt().path
      val browserJSDepsPath = browser.js.aggregatedJSDeps().path
      val runMain = runMainFn()
      runMain(
        "qasrl.apps.browser.Generate", Seq(
          "--qasrl-bank",      qasrlBankLocation.toString,
          "--api-url",         s"http://$domain:$port",
          "--browser-js",      browserJSPath.toString,
          "--browser-jsdeps",  browserJSDepsPath.toString,
          "--site-root",       siteRoot.toString
        )
      )
    }

    def serve(
      qasrlBankLocation: String,
      port: Int,
      domainRestriction: String = ""
    ) = T.command {
      val runMain = runMainFn()
      runMain(
        "qasrl.apps.browser.Serve", Seq(
          "--qasrl-bank", qasrlBankLocation,
          "--port",       s"$port"
        ) ++ Option(domainRestriction).filter(_.nonEmpty).toSeq.flatMap(d => Seq("--domain", d))
      )
    }
  }
  object js extends QASRLBrowserModule with JsPlatform with SimpleJSDeps {

    def moduleDeps = Seq(core.js)

    def mainClass = T(Some("qasrl.apps.browser.Main"))

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.julianmichael::nlpdata::$nlpdataVersion",
      ivy"org.julianmichael::radhoc::$radhocVersion",
      ivy"com.github.julien-truffaut::monocle-core::$monocleVersion",
      ivy"com.github.julien-truffaut::monocle-macro::$monocleVersion",
      ivy"org.scala-js::scalajs-dom::$scalajsDomVersion",
      ivy"com.github.japgolly.scalajs-react::core::$scalajsReactVersion",
      ivy"com.github.japgolly.scalajs-react::ext-monocle::$scalajsReactVersion",
      ivy"com.github.japgolly.scalajs-react::ext-cats::$scalajsReactVersion",
      ivy"com.github.japgolly.scalacss::ext-react::$scalajsScalaCSSVersion"
    )

    def jsDeps = Agg(
      "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react.js",
      "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react-dom.js"
    )
  }
}

trait QASRLDemoModule extends CommonModule {
  def millSourcePath = build.millSourcePath / "demo"

  def ivyDeps = super.ivyDeps() ++ Agg(
    ivy"org.typelevel::cats-core::$catsVersion",
    ivy"org.julianmichael::qasrl::$qasrlVersion",
    ivy"org.julianmichael::qasrl-bank::$qasrlBankVersion",
    ivy"org.julianmichael::qasrl-bank-service::$qasrlBankVersion"
  )
}

object demo extends Module {
  object jvm extends QASRLDemoModule with JvmPlatform with ScalatexModule {

    def moduleDeps = Seq(core.jvm)

    override def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.github.japgolly.scalacss::core:$scalacssVersion",
      ivy"com.github.japgolly.scalacss::ext-scalatags:$scalacssVersion",
      ivy"com.monovore::decline::$declineVersion",
      ivy"org.http4s::http4s-dsl::$http4sVersion",
      ivy"org.http4s::http4s-blaze-server::$http4sVersion",
      ivy"org.http4s::http4s-blaze-client::$http4sVersion",
      ivy"ch.qos.logback:logback-classic:$logbackVersion"
    )

    def generateDev(port: Int, domain: String = "localhost") = T.command {
      val demoJSPath = demo.js.fastOpt().path.toString
      val demoJSDepsPath = demo.js.aggregatedJSDeps().path.toString
      val runMain = runMainFn()
      runMain(
        "qasrl.apps.demo.Generate", Seq(
          "--api-url",      s"http://$domain:$port",
          "--demo-js",      demoJSPath,
          "--demo-jsdeps",  demoJSDepsPath,
          "--site-root",    "site/demo/dev",
          "--local-links"
        )
      )
    }

    def generateProd(port: Int, domain: String) = T.command {
      val demoJSPath = demo.js.fullOpt().path.toString
      val demoJSDepsPath = demo.js.aggregatedJSDeps().path.toString
      val runMain = runMainFn()
      runMain(
        "qasrl.apps.demo.Generate", Seq(
          "--api-url",      s"http://$domain:$port",
          "--demo-js",      demoJSPath,
          "--demo-jsdeps",  demoJSDepsPath,
          "--site-root",    "site/demo/prod"
        )
      )
    }

    def serve(port: Int, serviceUrl: String, domainRestriction: String = "") = T.command {
      val runMain = runMainFn()
      runMain(
        "qasrl.apps.demo.Serve", Seq(
          "--service-url", serviceUrl,
          "--port",        s"$port"
        ) ++ Option(domainRestriction).filter(_.nonEmpty).toSeq.flatMap(d => Seq("--domain", d))
      )
    }
  }
  object js extends QASRLDemoModule with JsPlatform with SimpleJSDeps {
    def moduleDeps = Seq(core.js)

    def mainClass = T(Some("qasrl.apps.demo.Main"))

    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.julianmichael::qasrl::$qasrlVersion",
      ivy"org.julianmichael::nlpdata::$nlpdataVersion",
      ivy"org.julianmichael::radhoc::$radhocVersion",
      ivy"com.github.julien-truffaut::monocle-core::$monocleVersion",
      ivy"com.github.julien-truffaut::monocle-macro::$monocleVersion",
      ivy"org.scala-js::scalajs-dom::$scalajsDomVersion",
      ivy"com.github.japgolly.scalajs-react::core::$scalajsReactVersion",
      ivy"com.github.japgolly.scalajs-react::ext-monocle::$scalajsReactVersion",
      ivy"com.github.japgolly.scalajs-react::ext-cats::$scalajsReactVersion",
      ivy"com.github.japgolly.scalacss::ext-react::$scalajsScalaCSSVersion"
    )

    def jsDeps = Agg(
      "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react.js",
      "https://cdnjs.cloudflare.com/ajax/libs/react/15.6.1/react-dom.js"
    )
  }
}
