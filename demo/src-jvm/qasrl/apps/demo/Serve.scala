package qasrl.apps.demo

import qasrl.bank.Data

import cats.data.NonEmptySet
import cats.effect.IO
import cats.implicits._

import fs2.{Stream, StreamApp}
import fs2.StreamApp.ExitCode

import java.nio.file.Path

import com.monovore.decline._

import org.http4s.server.blaze._

object Serve extends StreamApp[IO] {
  override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {

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

    val command = Command(
      name = "mill demo.jvm.runMain qasrl.apps.demo.Serve",
      header = "Spin up the proxy server for the QA-SRL Bank Demo webapp.") {
      (serviceUrlO, portO, domainRestrictionO).mapN((_, _, _))
    }

    val resStreamEither = {
      command.parse(args).map { case (serviceUrl, port, domainRestrictionOpt) =>

        val bareService = ModelDemoRedirectService.makeService(serviceUrl)

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

        val service = CORS(bareService, corsConfig)

        import scala.concurrent.ExecutionContext.Implicits.global

        BlazeBuilder[IO]
          .bindHttp(port, "0.0.0.0")
          .mountService(service, "/")
          .serve
      }
    }

    resStreamEither.left.map(message =>
      Stream.eval(IO { System.err.println(message); ExitCode.Error })
    ).merge
  }
}
