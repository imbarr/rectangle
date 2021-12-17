import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.caching.LfuCache
import akka.http.caching.scaladsl.{Cache, CachingSettings}
import akka.http.scaladsl.server.RouteResult
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import scala.concurrent.duration._
import akka.http.scaladsl.server.directives.CachingDirectives._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.RequestContext

import scala.io.StdIn

object Main extends App {
  implicit val system = ActorSystem(Behaviors.empty, "my-system")
  implicit val executionContext = system.executionContext

  val keyFunc: PartialFunction[RequestContext, Uri] = {
    case r => r.request.uri
  }
  val cacheSettings = CachingSettings(system)
  val lfuCacheSettings = cacheSettings.lfuCacheSettings
    .withInitialCapacity(10)
    .withMaxCapacity(10)
    .withTimeToLive(60.seconds)
    .withTimeToIdle(60.seconds)
  val lfuCache: Cache[Uri, RouteResult] =
    LfuCache(cacheSettings.withLfuCacheSettings(lfuCacheSettings))

  val route =
    alwaysCache(lfuCache, keyFunc) {
      path("area") {
        get {
          parameters("a".as[Int], "b".as[Int]) { (a, b) =>
            system.log.info("Calculating area")
            complete {
              HttpEntity(ContentTypes.`text/html(UTF-8)`, (a * b).toString)
            }
          }
        }
      }
    }

  val binding = Http().newServerAt("localhost", 8080).bind(route)
  system.log.info("Server started")

  StdIn.readLine()
  binding.flatMap(_.unbind()).onComplete(_ => system.terminate())
}
