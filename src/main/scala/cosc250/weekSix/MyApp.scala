package cosc250.weekSix

import java.util.concurrent.TimeoutException

import akka.NotUsed
import akka.actor._

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object MyApp extends App {

  Console.println("As this extends App, the object's body is run at startup.")

  // This creates an Actor System
  val system = ActorSystem.apply("Spit")

  // The rest is over to you...

}

