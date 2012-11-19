package aws.cloudsearch

import scala.concurrent.Future
import play.api.libs.ws._
import play.api.libs.ws.WS._
import aws.core._

import org.specs2.mutable._

object TestUtils extends Specification { // Evil hack to access Failure

  import scala.concurrent._
  import scala.concurrent.duration.Duration
  import java.util.concurrent.TimeUnit._

  implicit val region = CloudSearchRegion.US_EAST_1

  def checkResult[M <: Metadata, T](r: Result[M, T]) = r match {
    case AWSError(meta, code, message) => failure(message)
    case Result(_, _) => success
  }

  def waitFor[T](f: Future[T]) = Await.result(f, Duration(30, SECONDS))
}

object CloudSearchSpec extends Specification {

  import TestUtils._

  val domain = ("imdb-movies", "5d3sfdtvri2lghw27klaho756y")

  import CloudSearchParsers._
  import aws.core.parsers._
  import play.api.libs.json._

  case class Movie(id: String, title: Seq[String])
  implicit val moviesParser = Parser[Seq[Movie]] { r =>
    import play.api.libs.json.util._
    val reader = ((__ \ "id").read[String] and
    (__ \ "data" \ "title").read[Seq[String]])(Movie)

    Success((r.json \ "hits" \ "hit").as[Seq[JsValue]].map { js =>
      js.validate(reader).get
    })
  }

  "CloudSearch API" should {
    import scala.concurrent.ExecutionContext.Implicits.global

    "Search using String query" in {
      val r = waitFor(CloudSearch.search[Seq[Movie]](
        domain = domain,
        query = Some("star wars"),
        returnFields = Seq("title")))
      checkResult(r)
    }

    "Search using MatchExpression and Filter" in {
      import CloudSearch.MatchExpressions._
      val ex = Field("title", "star wars") and Filter("year", 2008)
      val r = waitFor(CloudSearch.search[Seq[Movie]](
        domain = domain,
        matchExpression = Some(ex),
        returnFields = Seq("title")))
      checkResult(r)
    }

    "Search using MatchExpression and Filter range" in {
      import CloudSearch.MatchExpressions._
      val ex = Field("title", "star wars") and Filter("year", 2000 to 2012)
      val r = waitFor(CloudSearch.search[Seq[Movie]](
        domain = domain,
        matchExpression = Some(ex),
        returnFields = Seq("title")))
      checkResult(r)
    }
  }
}