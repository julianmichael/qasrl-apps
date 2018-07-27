// package qasrl.apps.demo

// import cats.implicits._
// import cats.effect._

// import org.http4s._
// import org.http4s.implicits._

// import org.http4s.server.blaze._

// import fs2.{Stream, StreamApp}
// import fs2.StreamApp.ExitCode

// import scala.concurrent.ExecutionContext.Implicits.global

// import nlpdata.util.Text
// import nlpdata.util.LowerCaseStrings._

// object DemoServerMain extends StreamApp[IO] {
//   override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] = {

//     val bareService = ModelDemoRedirectService.makeService(args(0))

//     import org.http4s.server.middleware._
//     import scala.concurrent.duration._

//     val corsConfig = CORSConfig(
//       // anyOrigin = false,
//       // allowedOrigins = Set("http://demo.qasrl.org"),
//       anyOrigin = true,
//       anyMethod = false,
//       allowedMethods = Some(Set("POST")),
//       allowCredentials = false,
//       maxAge = 1.day.toSeconds)

//     val service = CORS(bareService, corsConfig)

//     BlazeBuilder[IO]
//       .bindHttp(5050, "0.0.0.0")
//       .mountService(service, "/")
//       .serve
//   }
// }
