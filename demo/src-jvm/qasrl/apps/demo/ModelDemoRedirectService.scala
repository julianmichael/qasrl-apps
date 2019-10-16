package qasrl.apps.demo

import cats.implicits._
import cats.effect._

import org.http4s._
// import org.http4s.client._
import org.http4s.client.blaze._
import org.http4s.implicits._
import org.http4s.client.dsl.Http4sClientDsl

import io.circe.Json

import scala.concurrent.ExecutionContext.Implicits.global

object ModelDemoRedirectService {

  object ClientHelper extends Http4sClientDsl[IO] {
    def makePostRequest(
      modelServiceUrl: String,
      sentence: String
    ) = {
      import org.http4s.dsl.io._
      import org.http4s.circe._
      Uri.fromString(modelServiceUrl).map(uri =>
        POST(Json.obj("sentence" -> Json.fromString(sentence)), uri)
      )
    }
  }

  def makeService(
    modelServiceUrl: String)(
    implicit cs: ContextShift[IO]
  ) = {
    BlazeClientBuilder[IO](global).resource.use { client =>
      import io.circe.syntax._
      import org.http4s.dsl.io._
      import org.http4s.circe._
      IO.pure(
        HttpRoutes.of[IO] {
          case req @ POST -> Root / "parse" => for {
            sentence <- req.as[String]
            request <- IO.fromEither(ClientHelper.makePostRequest(modelServiceUrl, sentence))
            result <- client.expect[Json](request)
            response <- Ok(result)
          } yield response
        }
      )
    }
  }
}
