package qasrl.apps.demo

import qasrl.bank.Data

import cats.data.NonEmptySet
import cats.effect.ExitCode
import cats.effect.IO
import cats.implicits._

import java.nio.file.Path

import com.monovore.decline._
import com.monovore.decline.effect._

import org.http4s.server.Router
import org.http4s.server.blaze._

object Serve extends CommandIOApp(
  name = "mill demo.jvm.runMain qasrl.apps.demo.Serve",
  header = "Run the proxy server for the QA-SRL model demo.") {

  def main: Opts[IO[ExitCode]] = {

    val serviceUrlO = Opts.option[String](
      "service-url", metavar = "url", help = "URL of the model prediction service."
    )

    val portO = Opts.option[Int](
      "port", metavar = "port number", help = "Port to host the proxy HTTP service on."
    )

    val domainRestrictionO = Opts.option[String](
      "domain", metavar = "http://...",
      help = "Domain to impose CORS restrictions to (otherwise, all domains allowed)."
    ).map(NonEmptySet.of(_)).orNone

    (serviceUrlO, portO, domainRestrictionO).mapN(program)
  }

  def program(
    serviceUrl: String,
    port: Int,
    domainRestrictionOpt: Option[NonEmptySet[String]]
  ): IO[ExitCode] = {

    import org.http4s.server.middleware._
    import scala.concurrent.duration._

    val corsConfig = domainRestrictionOpt match {
      case None => CORSConfig(
        anyOrigin = true,
        anyMethod = false,
        allowedMethods = Some(Set("POST")),
        allowCredentials = false,
        maxAge = 1.day.toSeconds
      )
      case Some(domains) => CORSConfig(
        anyOrigin = false,
        allowedOrigins = domains.toSortedSet,
        anyMethod = false,
        allowedMethods = Some(Set("POST")),
        allowCredentials = false,
        maxAge = 1.day.toSeconds
      )
    }

    import scala.concurrent.ExecutionContext.Implicits.global
    import org.http4s.syntax.kleisli._

    ModelDemoRedirectService.makeService(serviceUrl) >>= (bareService =>
      BlazeServerBuilder[IO]
        .bindHttp(port, "0.0.0.0")
        .withHttpApp(Router("/" -> CORS(bareService, corsConfig)).orNotFound)
        .serve.compile.drain
        .as(ExitCode.Success)
    )
  }
}
