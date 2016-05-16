package cosc250.weekSix

import java.util.concurrent.TimeoutException

import akka.NotUsed
import akka.actor._
import akka.stream.scaladsl.{Flow, Source}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.ning.NingWSClient

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object MyApp extends App {

  import Exercise._

  implicit val system = ActorSystem("exercise")
  implicit val materializer = ActorMaterializer()


  // Here's the start of your mission
  val s:Source[Int, NotUsed] = Source(1 to 1000000)



  /*
   * Next, create a flow that will take a Source[Int, NotUsed] and apply a "Fizz Buzz" conversion to it ...
   * that is, if it's divisible by 3 produce "Fizz"
   * if it's divisible by 5 produce "Buzz"
   * if it's divisible by 15 produce "Fizz Buzz"
   * otherwize produce an ordinary number.
   *
   * ... oh, and toString them...
   *
   */
  val flow = Flow[Int].map[String]({
    case x if x % 15 == 0 => "FizzBuzz"
    case x if x % 3 == 0 => "Fizz"
    case x if x % 5 == 0 => "Buzz"
    case x => x.toString
  })

  val slow = Flow[String].map[String]({ x =>
    try {
      Thread.sleep(200)
    } catch {
      case x:Exception => //
    }
    x
  })

  val t = 1.second

  /*
   * Once you have your flow, it's time to materialise it.
   * Let's just stream it to standard out
   */
  val mid = s.via(flow)

  val withHose = mid.via(slow)

  val withSink = withHose.runForeach(println)

  val after = withSink.andThen({ case _ => system.terminate() })




  /*
   * And now let's play with backpressure.
   * Somewhere in the flow, insert something that will make it only write out 5 numbers per second.
   */

}

