package it

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.components.OneAppPerSuiteWithComponents
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.{DefaultAwaitTimeout, FakeRequest, Helpers}
import play.api.{BuiltInComponents, BuiltInComponentsFromContext, Configuration, NoHttpFiltersComponents}
import play.api.http._
import play.api.libs.json.Json

import scala.concurrent.Future

class TestingStuffSpec extends PlaySpec with OneAppPerSuiteWithComponents with DefaultAwaitTimeout {

  implicit val timeout2: akka.util.Timeout = defaultAwaitTimeout

  // #scalacomponentstest-inlinecomponents
  override def components: BuiltInComponents = new BuiltInComponentsFromContext(context) with NoHttpFiltersComponents {

    import play.api.mvc.Results
    import play.api.routing.Router
    import play.api.routing.sird._

    lazy val router: Router = Router.from({
      case GET(p"/") => defaultActionBuilder {
        Results.Ok("success!")
      }
    })
    override lazy val configuration: Configuration = context.initialConfiguration ++ Configuration("foo" -> "bar", "ehcacheplugin" -> "disabled")
  }
  // #scalacomponentstest-inlinecomponents

  "The OneAppPerSuiteWithComponents trait" must {
    "provide an Application" in {
      import play.api.test.Helpers._

      val Some(result): Option[Future[Result]] = route(app, FakeRequest(GET, "/").withHeaders("accepts" -> "text/plain"))
      Helpers.contentAsString(result) must be("success!")
    }
    "override the configuration" in {
      app.configuration.getOptional[String]("foo") mustBe Some("bar")
    }
  }
}