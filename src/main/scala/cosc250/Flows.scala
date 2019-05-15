//https://doc.akka.io/docs/akka/2.5.22/stream/stream-flows-and-basics.html

//TODO the pink-lettered code article: Sink = subscriber, Source = Publisher: https://scalac.io/streams-in-akka-scala-introduction/

//todo (?) working with graphs: https://doc.akka.io/docs/akka/2.5.22/stream/stream-graphs.html


import akka.actor.Cancellable
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, IOResult}

//#other-imports
import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent._
import scala.concurrent.duration._

//#other-imports
import java.nio.file.Paths
import java.io.File


//futures
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try //places default thread pool in scope so Future can be
// executed asynchronously.
import scala.concurrent.{Await, Future, Promise, future}
import scala.concurrent.duration._
import scala.util.{Success, Failure}




// Stream
// An active process that involves moving and transforming data.


// Element
// An element is the processing unit of streams. All operations transform and transfer elements from upstream to //
// downstream. Buffer sizes are always expressed as number of elements independently from the actual size of the
// elements.

//	Back-pressure
//A means of flow-control, a way for consumers of data to notify a producer about their current availability,
// effectively slowing down the upstream producer to match their consumption speeds. In the context of Akka Streams //
//back-pressure is always understood as non-blocking and asynchronous.


//	Non-Blocking
// Means that a certain operation does not hinder the progress of the calling thread, even if it takes a long time to
// finish the requested operation.


// Graph
// A description of a stream processing topology, defining the pathways through which elements shall flow when the /
// stream is running.


//	Operator
// The common name for all building blocks that build up a Graph. Examples of operators are map(), filter(), custom //
// ones extending GraphStages and graph junctions like Merge or Broadcast.


// When we talk about asynchronous, non-blocking backpressure, we mean that the operators available in Akka Streams //
// will not use blocking calls but asynchronous message passing to exchange messages between each other. This way they
// can slow down a fast producer without blocking its thread. This is a thread-pool friendly design, since entities
// that need to wait (a fast producer waiting on a slow consumer) will not block the thread but can hand it back
// for further use to an underlying thread-pool.


//SEE ALL HIGHLIGHTS with hypothesis here:
//TODO https://doc.akka.io/docs/akka/2.5.22/stream/stream-flows-and-basics.html



object MaterializeGraph extends App {

	implicit val system = ActorSystem("QuickStart")
	implicit val materializer = ActorMaterializer()


	val source = Source(1 to 10)
	val sink = Sink.fold[Int, Int](0)(_ + _)

	// connect the Source to the Sink, obtaining a RunnableGraph
	val runnable: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right)

	//note: toMat indicates we want to transform the materialized value of
	//the source and sink
	//Keep right to take only materialized value of sink.

	// materialize the flow and get the value of the FoldSink
	val sum: Future[Int] = runnable.run()
	sum.foreach(oneSumValue => println(oneSumValue))
}


/**
  * In general, a stream can expose multiple materialized values, but it is
  * quite common to be interested in only the value of the Source or the Sink
  * in the stream. For this reason there is a convenience method called runWith()
  * available for Sink, Source or Flow requiring, respectively,
  * 		- a supplied Source (in order to run a Sink),
  * 		- a Sink (in order to run a Source) or
  * 		- both a Source and a Sink (in order to run a Flow, since it has neither
  * attached yet).
  *
  */
object MaterializeRunWith extends App {


	implicit val system = ActorSystem("ActorSystem")
	implicit val materializer = ActorMaterializer()

	val source: Source[Int, NotUsed] = Source(1 to 100)
	val sink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)

	//materialize the flow, getting sink's materialized value
	val sum: Future[Int] = source.runWith(sink)
	sum.foreach(s => println(s"sum $s"))


}

//note: operators are immutable so connecting them returns new operator
object MaterializerImmutable extends App {


	implicit val system = ActorSystem("ActorSystem")
	implicit val materializer = ActorMaterializer()


	val source = Source(1 to 10)
	source.map(_ => 0) // has no effect on source, since it's immutable
	val s: Future[Int] = source.runWith(Sink.fold(0)(_ + _)) // 55
	s.foreach(sval => println(sval)) //note another cool way to wait for Futures to complete

	val zeroes = source.map(_ => 0) // returns new Source[Int], with `map()` appended
	val z: Future[Int] = zeroes.runWith(Sink.fold(0)(_ + _)) // 0
	z.foreach(zval => println(zval))
}




/**

// Since a stream can be materialized multiple times, the materialized value will also be calculated anew for each such
// 	materialization, usually leading to different values being returned each time. In the example below, we create
//two running materialized instances of the stream that we described in the runnable variable. Both
// materializations give us a different Future from the map even though we used the same sink to refer to the future:
  */

object MaterializeDifferentResults extends App {
	implicit val system = ActorSystem("ActorSystem")
	implicit val materializer = ActorMaterializer()


	// connect the Source to the Sink, obtaining a RunnableGraph
	val sink = Sink.fold[Int, Int](0)(_ + _)
	val runnable: RunnableGraph[Future[Int]] =
		Source(1 to 10).toMat(sink)(Keep.right)

	// get the materialized value of the FoldSink
	val sum1: Future[Int] = runnable.run()
	val sum2: Future[Int] = runnable.run()

	// sum1 and sum2 are different Futures! (even though value is the same)
	sum1.foreach(s1 => println(s1))
	sum2.foreach(s2 => println(s2))

	println(sum1 == sum2)
}




object DefiningSinksSourceFlow extends App {
	// Create a source from an Iterable
	Source(List(1, 2, 3))

	// Create a source from a Future
	Source.fromFuture(Future.successful("Hello Streams!"))

	// Create a source from a single element
	println(Source.single("only one element"))

	// an empty source
	Source.empty

	// Sink that folds over the stream and returns a Future
	// of the final result as its materialized value
	Sink.fold[Int, Int](0)(_ + _)

	// Sink that returns a Future as its materialized value,
	// containing the first element of the stream
	Sink.head

	// A Sink that consumes a stream without doing anything with the elements
	Sink.ignore

	// A Sink that executes a side-effecting call for every element of the stream
	Sink.foreach[String](println(_))
}




object WaysToWireUp extends App {

	implicit val system = ActorSystem("ActorSystem")
	implicit val materializer = ActorMaterializer()



	// Explicitly creating and wiring up a Source, Sink and Flow
	println("(1)")
	val runnable: RunnableGraph[NotUsed] =
		Source(1 to 6).via(Flow[Int].map(_ * 2)).to(Sink.foreach(println(_)))
	val res: NotUsed = runnable.run()

	// Starting from a Source
	println("(2)")
	val source = Source(1 to 6).map(_ * 2)
	source.to(Sink.foreach(println(_)))

	// Starting from a Sink
	println("(3)")
	val sink: Sink[Int, NotUsed] = Flow[Int].map(_ * 2).to(Sink.foreach(println(_)))
	Source(1 to 6).to(sink)

	println("(4)")
	// Broadcast to a sink inline
	val otherSink: Sink[Int, NotUsed] =
		Flow[Int].alsoTo(Sink.foreach(println(_))).to(Sink.ignore)
	Source(1 to 6).to(otherSink)
}


// Combining materialized values
// Since every operator in Akka Streams can provide a materialized value after being materialized, it is necessary to
// 	somehow express how these values should be composed to a final value when we plug these operators together. For
// this, many operator methods have variants that take an additional argument, a function, that will be used to combine
//	the resulting values. Some examples of using these combiners are illustrated in the example below.

object CombineMaterializedValues extends App {

	implicit val system = ActorSystem("ActorSystem")
	implicit val materializer = ActorMaterializer()



	val throttler = Flow.fromGraph(GraphDSL.create(Source.tick(1.second, 1.second, "test")) {
		implicit builder => tickSource =>
			import GraphDSL.Implicits._
			val zip = builder.add(ZipWith[String, Int, Int](Keep.right))
			tickSource ~> zip.in0
			FlowShape(zip.in1, zip.out)
	})


	// An source that can be signalled explicitly from the outside
	val source: Source[Int, Promise[Option[Int]]] = Source.maybe[Int]

	// A flow that internally throttles elements to 1/second, and returns a Cancellable
	// which can be used to shut down the stream
	val flow: Flow[Int, Int, Cancellable] = throttler

	// A sink that returns the first element of a stream in the returned Future
	val sink: Sink[Int, Future[Int]] = Sink.head[Int]

	// By default, the materialized value of the leftmost stage is preserved
	val r1: RunnableGraph[Promise[Option[Int]]] = source.via(flow).to(sink)

	// Simple selection of materialized values by using Keep.right
	val r2: RunnableGraph[Cancellable] = source.viaMat(flow)(Keep.right).to(sink)
	val r3: RunnableGraph[Future[Int]] = source.via(flow).toMat(sink)(Keep.right)
	r3.run().foreach(println("r3: ", _))

	// Using runWith will always give the materialized values of the stages added
	// by runWith() itself
	val r4: Future[Int] = source.via(flow).runWith(sink)
	r4.foreach(r => println("r4: ", r))

	val r5: Promise[Option[Int]] = flow.to(sink).runWith(source)
	val r6: (Promise[Option[Int]], Future[Int]) = flow.runWith(source, sink)

	// Using more complex combinations
	val r7: RunnableGraph[(Promise[Option[Int]], Cancellable)] =
		source.viaMat(flow)(Keep.both).to(sink)

	val r8: RunnableGraph[(Promise[Option[Int]], Future[Int])] =
		source.via(flow).toMat(sink)(Keep.both)

	val r9: RunnableGraph[((Promise[Option[Int]], Cancellable), Future[Int])] =
		source.viaMat(flow)(Keep.both).toMat(sink)(Keep.both)

	val r10: RunnableGraph[(Cancellable, Future[Int])] =
		source.viaMat(flow)(Keep.right).toMat(sink)(Keep.both)

	// It is also possible to map over the materialized values. In r9 we had a
	// doubly nested pair, but we want to flatten it out
	val r11: RunnableGraph[(Promise[Option[Int]], Cancellable, Future[Int])] =
	r9.mapMaterializedValue {
		case ((promise, cancellable), future) =>
			(promise, cancellable, future)
	}

	// Now we can use pattern matching to get the resulting materialized values
	val (promise, cancellable, future) = r11.run()

	// Type inference works as expected
	promise.success(None)
	cancellable.cancel()
	future.map(_ + 3)

	// The result of r11 can be also achieved by using the Graph API
	val r12: RunnableGraph[(Promise[Option[Int]], Cancellable, Future[Int])] =
		RunnableGraph.fromGraph(GraphDSL.create(source, flow, sink)((_, _, _)) { implicit builder => (src, f, dst) =>
			import GraphDSL.Implicits._
			src ~> f ~> dst
			ClosedShape
		})
}



//Backpressure EXPLAINED:

// The back pressure protocol is defined in terms of the number of elements a downstream Subscriber is able to receive /
// and buffer, referred to as demand. The source of data, referred to as Publisher in Reactive Streams terminology and
// implemented as Source in Akka Streams, guarantees that it will never emit more elements than the received total
//	demand for any given Subscriber.

// Slow Publisher, fast Subscriber
// 	This is the happy case – we do not need to slow down the Publisher in this case. However signalling rates are
// 	rarely constant and could change at any point in time, suddenly ending up in a situation where the Subscriber is
// 	now slower than the Publisher. In order to safeguard from these situations, the back-pressure protocol must
// still be enabled during such situations, however we do not want to pay a high penalty for this safety net being
// 	enabled. 	The Reactive Streams protocol solves this by asynchronously signalling from the Subscriber to the Publisher Request(n:Int) signals. The protocol guarantees that the Publisher will never signal more elements than the signalled demand. Since the Subscriber however is currently faster, it will be signalling these Request messages at a higher rate (and possibly also batching together the demand - requesting multiple elements in one Request signal). This means that the Publisher should not ever have to wait (be back-pressured) with publishing its incoming elements.

// As we can see, in this scenario we effectively operate in so called push-mode since the Publisher can continue
//	producing elements as fast as it can, since the pending demand will be recovered just-in-time while it is
//	emitting elements
//
//
//
//Fast Publisher, slow Subscriber
//This is the case when back-pressuring the Publisher is required, because the Subscriber is not able to cope with the rate at which its upstream would like to emit data elements.
//
//Since the Publisher is not allowed to signal more elements than the pending demand signalled by the Subscriber, it will have to abide to this back-pressure by applying one of the below strategies:
//
//not generate elements, if it is able to control their production rate,
//try buffering the elements in a bounded manner until more demand is signalled,
//drop elements until more demand is signalled,
//tear down the stream if unable to apply any of the above strategies.
//As we can see, this scenario effectively means that the Subscriber will pull the elements from the Publisher – this mode of operation is referred to as pull-based back-pressure.	.

