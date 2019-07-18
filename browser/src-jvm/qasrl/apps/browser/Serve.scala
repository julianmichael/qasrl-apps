package qasrl.apps.browser

import qasrl.bank.Data

import cats.data.NonEmptySet
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.implicits._

import fs2.Stream

import qasrl.bank.service.DocumentServiceWebServer

import java.nio.file.Path

import com.monovore.decline._
import com.monovore.decline.effect._

object Serve extends CommandIOApp(
  name = "mill -i browser.jvm.runMain qasrl.apps.browser.Serve",
  header = "Spin up the data server for the QA-SRL Bank browser webapp.",
  version = "0.2.0") {

  def main: Opts[IO[ExitCode]] = {
    val qasrlBankO = Opts.option[Path](
      "qasrl-bank", metavar = "path", help = "Path to the QA-SRL Bank 2.0 data, e.g., ../qasrl-bank/data/qasrl-v2."
    )

    val portO = Opts.option[Int](
      "port", metavar = "port number", help = "Port to host the HTTP service on."
    )

    val domainRestrictionO = Opts.option[String](
      "domain", metavar = "http://...",
      help = "Domain to impose CORS restrictions to (otherwise, all domains allowed)."
    ).map(NonEmptySet.of(_)).orNone

    (qasrlBankO, portO, domainRestrictionO).mapN { case (qasrlBankPath, port, domainRestrictionOpt) =>
      IO.fromTry(Data.readFromQasrlBank(qasrlBankPath)).map { data =>
        DocumentServiceWebServer.serve(data.small, port, domainRestrictionOpt)
          .compile.drain.as(ExitCode.Success)
      }
    }
  }
}
