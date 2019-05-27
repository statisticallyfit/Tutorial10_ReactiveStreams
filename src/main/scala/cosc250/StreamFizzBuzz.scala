package cosc250

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}
import cosc250.util.Wait

import scala.concurrent.Future

/**
  *
  */
object StreamFizzBuzz extends App {

	implicit val system = ActorSystem("ActorSystem")
	implicit val materializer = ActorMaterializer()


	// Here's the start of your mission
	// Explicitly creating and wiring up a Source, Sink and Flow
	val source: Source[Int, NotUsed] = Source(1 to 20) //1000000

	println("Way (1)")
	val runnable: RunnableGraph[NotUsed] =
		source.via(Flow[Int].map(_ * 2)).to(Sink.foreach(println(_)))
	val res: NotUsed = runnable.run()

	Thread.sleep(2000)

	// Starting from a Sink
	println("Way (2)")
	val sink: Sink[Int, NotUsed] = Flow[Int].map(_ * 2).to(Sink.foreach(println(_)))
	source.to(sink).run()

	Thread.sleep(2000)


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
	println("\n\nFizz Buzz case study")

	// Make a flow to convert a number to fizz buzz values
	// In = Int, Out = String, Materialized = NotUsed
	val flow: Flow[Int, String, NotUsed] = Flow[Int].map[String] {
		case n if n % 15 == 0 => "FizzBuzz"
		case n if n % 3 == 0 => "Fizz"
		case n if n % 5 == 0 => "Buzz"
		case n => n.toString
	}


	/*
	 * Once you have your flow, it's time to materialise it.
	 * Let's just stream it to standard out
	 */
	println("\nRunning the fizz buzz graph")
	source.via(flow).to(Sink.foreach(println(_))).run()



	//TUtorial solution way
	val slowFlow: Flow[String, String, NotUsed] = Flow[String].map[String] { x =>
		try {
			Thread.sleep(200)
		} catch {
			case x: Exception => //
		}
		x
	}


	val mid: Source[String, NotUsed] = source.via(flow)
	val withHose: Source[String, NotUsed] = source.via(flow).via(slowFlow) //mid.via(slowFlow)

	println("\nwithHose.runForeach(println(): ")
	val withSink: Future[Done] = withHose.runForeach(println) //equivalent to withsink3

	Wait.hangOn(withSink)


	println("\nwithHose.to(Sink.foreach(println(_))).run()")
	val withSink2: NotUsed = withHose.to(Sink.foreach(println(_))).run()

	Thread.sleep(20000) //Wait.hangOn(withSink2)

	println("\nwithHose.runWith(Sink.foreach(println(_)))")
	val withSink3: Future[Done] = withHose.runWith(Sink.foreach(println(_)))

	Wait.hangOn(withSink3)

	val after: Future[Done] = withSink.andThen({case _ => system.terminate() })
	val after3: Future[Done] = withSink3.andThen({case _ => system.terminate() }) //note - this works too



	/*
	 * And now let's play with backpressure.
	 * Somewhere in the flow, insert something that will make it only write out 5 numbers per second.
	 */

}
