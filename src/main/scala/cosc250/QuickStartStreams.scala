
//https://doc.akka.io/docs/akka/2.5.22/stream/stream-quickstart.html

//#stream-imports


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



//TODO: more operators: (like throttle)
//TODO https://doc.akka.io/docs/akka/2.5.22/stream/operators/index.html


object Basics extends App {

	//Source type = parametrized with two types
	//First: int = type of the element that the source emits
	//Second: Notused = signals that running the source may produce some
	//auxiliary value
	//NotUsed = when the no auxiliary info is produced.
	val source: Source[Int, NotUsed] = Source(1 to 100)

	//materializer = factory for stream execution engines = it makes
	// the streams run.
	//
	implicit val system = ActorSystem("QuickStart")
	implicit val materializer = ActorMaterializer()


	//running the source to get the numbers
	source.runForeach(i => println(i))(materializer)


	//Source is just a description of what you wan to run and can be incorporated
	//into a larger design
	// Can write it to a file
	//
	val factorials: Source[BigInt, NotUsed] = source.scan(BigInt(1))((acc, next) => acc * next)
	val result: Future[IOResult] = factorials.map(num => ByteString(s"$num\n"))
     	.runWith(FileIO.toFile(new File("factorials.txt")))




	//Step 2: make it reusable

	//flow from left to right = like a source but with open input
	def lineSink(filename: String): Sink[String, Future[IOResult]] =
		Flow[String].map(s => ByteString(s + "\n"))
			.toMat(FileIO.toPath(Paths.get(filename)))(Keep.right)


	//starting from Flow of Strings
	//convert each to bytestring
	//feed bytestring into file-writing Sink
	//Result is Sink[String, Future[IOResult]] (means it accepts strings as input and when materialized
	//.. it will create auxiliary information of type Future[IOResult].

	//When chaining operations on a source or flow, the auxiliary info (materialized value)
	//is given by the leftmost starting point, and since we want to retain what FileIO.toPath sink
	//has to offer, we need to say Keep.right

	//use the Sink by attaching it to factorials source
	factorials.map(_.toString).runWith(lineSink("factorial2.txt"))
}


object FactorialsTimeProcessing extends App {

	val source: Source[Int, NotUsed] = Source(1 to 100)
	implicit val system = ActorSystem("QuickStart")
	implicit val materializer = ActorMaterializer()

	//running the source to get the numbers
	//source.runForeach(i => println(i))(materializer)


	//This here is just a template for execution
	val factorials: Source[BigInt, NotUsed] = source.scan(BigInt(1))((acc, next) => acc * next)

	//Transform the stream by zipping it with another stream of nums 0 1o 100
	// zipwith (source) (combine)
	val result: Future[Done] = factorials.zipWith(Source(0 to 100))((num, idx) => s"$idx! = $num")
     	.throttle(1, 1.second) //cost = 1 element  per = 1 second
     	.runForeach(println) //implicit materializer
	//time dependent - throttle slows down streams to 1 elem per second

	//reason this does not crash out of memeory: akka streams implement flow control,
	//al operators respect back-pressure.
	//Allows throttle operator to signal to upstream sources of data that it can
	//only accept elements at a certain rate.
	//When incoming rate is higher, throttle asserts back-pressure upstream.

}







object TweetAPI {
	final case class Author(handle: String )
	final case class Hashtag(name: String)

	final case class Tweet(author: Author, timestamp: Long, body: String) {
		def hashtags: Set[Hashtag] =
			body.split(" ").collect { case t if t.startsWith("#") => Hashtag(t) }
				.toSet
	}

	val akkaTag = Hashtag("#akka")

	//--------------------
	//In order to prepare our environment by creating an ActorSystem and ActorMaterializer, which will be responsible
	//for materializing and running the streams we are about to create:

	implicit val system = ActorSystem("reactive-tweets")
	implicit val materializer = ActorMaterializer()


	//Streams start from Source[Out, M1] and continue through Flow[In, Out, M2] and
	//consumed by Sink[In, M3], where M1, M2, M3 are materialized types.

	val tweets: Source[Tweet, NotUsed] = Source(
		Tweet(Author("rolandkuhn"), System.currentTimeMillis, "#akka rocks!") ::
			Tweet(Author("patriknw"), System.currentTimeMillis, "#akka !") ::
			Tweet(Author("bantonsson"), System.currentTimeMillis, "#akka !") ::
			Tweet(Author("drewhk"), System.currentTimeMillis, "#akka !") ::
			Tweet(Author("ktosopl"), System.currentTimeMillis, "#akka on the rocks!") ::
			Tweet(Author("mmartynas"), System.currentTimeMillis, "wow #akka !") ::
			Tweet(Author("akkateam"), System.currentTimeMillis, "#akka rocks!") ::
			Tweet(Author("bananaman"), System.currentTimeMillis, "#bananas rock!") ::
			Tweet(Author("appleman"), System.currentTimeMillis, "#apples rock!") ::
			Tweet(Author("drama"), System.currentTimeMillis, "we compared #apples to #oranges!") ::
			Nil)
}

object TweetSourceSink extends App {
	import TweetAPI._
	//note: Example 1: authors
	//to materialize and run the computation we need to attach the flow to a sink
	//that will get the flow running.
	//To do this: call runWith(sink) on a source

	println("Tweets printed: " + tweets)


	println("\nAuthors source: ")
	val authors: Source[Author, NotUsed] =
		tweets.filter(_.hashtags.contains(akkaTag)).map(_.author)

	println("Authors: ", authors)


	//attaching the sink
	println("\nAttach the sink: ")
	authors.runWith(Sink.foreach(println))
	//shorthand:
	//authors.runForeach(println)

	Thread.sleep(500)


	//note: Example 2
	//to materialize and run the computation we need to attach the flow to a sink
	//that will get the flow running.
	//To do this: call runWith(sink) on a source
	println("\nTweets 2: ")
	tweets
		.map(_.hashtags) // Get all sets of hashtags ...
		.reduce(_ ++ _) // ... and reduce them to a single set, removing duplicates across all tweets
		.mapConcat(identity) // Flatten the set of hashtags to a stream of hashtags
		.map(_.name.toUpperCase) // Convert all hashtags to upper case
		.runWith(Sink.foreach(println)) // Attach the Flow to a Sink that will finally print the hashtags

}





object FlattenSeqsInStreams extends App {


	import TweetAPI._

	//Flattened stream using mapConcat() like flatMap on collections
	val flattenedHashTags: Source[Hashtag, NotUsed] = tweets
     	.mapConcat(_.hashtags)

	//print them!
	flattenedHashTags.runWith(Sink.foreach(println))
}


/**
  * GOAL: we want to get two things out of one live stream: get all hashtags AND
  * all author names from ONE live stream.
  * Say we want to write authors into one file and hashtags into another file.
  * Means we have to split the soruce stream into two streams to handle the writing to these
  * different files.
  *
  * Elements that can be used to form such fan-out or fan-in structures are junctions
  * in Akka streams.
  *
  *
  * An example is Broadcast, it emits elements from its input port to all of its output ports.
  *
  * Streams separate the linear stream structures (Flows) from the nonlinear, branching ones (Graphs)
  * to offer convenient API
  */

object BroadcastWithGraph extends App {

	import TweetAPI._

	val writeAuthors: Sink[Author, Future[Done]] = Sink.ignore
	val writeHashtags: Sink[Hashtag, Future[Done]] = Sink.ignore
	Thread.sleep(100)
	println("A Sink of Author and Future[Done]: ", writeAuthors)

	// format: OFF
	//#graph-dsl-broadcast
	val runnableGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
		import GraphDSL.Implicits._

		val bcast: UniformFanOutShape[Tweet, Tweet] = b.add(Broadcast[Tweet](2))
		//we use an implicit graph builder b to mutably construct
		// //the graph using the ~> “edge operator” (also read as “connect” or “via” or “to”)
		tweets ~> bcast.in
		bcast.out(0) ~> Flow[Tweet].map(_.author) ~> writeAuthors
		bcast.out(1) ~> Flow[Tweet].mapConcat(_.hashtags.toList) ~> writeHashtags
		ClosedShape

		//Use implicit GraphBuilder b to mutably construct the graph using the edge operator ~>
		//GraphDSL.create returns a Graph[ClosedShape, NotUsed]

		//ClosedShape means it is a fully connected or closed graph, no unconnected
		//inputs or outputs
		//So  we can transform it into RunnableGraph using fromGraph

		//Then can run() the runnablegraph to materialize a stream out of it

		//Graph and RunnableGraph are immutable, thread-safe and freely shareable
	})
	val res: NotUsed = runnableGraph.run() //notused is the materialized value
}




//Akka streams always propagate backpressure info from stream Sinks (subscribers) to their
//sources (publishers)
object BackPressureInAction extends App {
	import TweetAPI._

	//Buffer can be used to get only the most recent tweets
	//tweets.buffer(10, OverflowStrategy.dropHead).map(slowComputation).runWith(Sink.ignore)
}


/**
  * We may want to get the materialized value, not just the end  value from Flow that
  * is consumed by an external Sink.
  * Example: may want to know how many tweets were processed.
  */
object MaterializeExplain extends App {
	import TweetAPI._

	//Preparing a reusable flow that will change each incoming tweet into integer
	//value of 1 (counting )
	//in = tweet, out = int, mat = notused
	val count: Flow[Tweet, Int, NotUsed] = Flow[Tweet].map(_ => 1) //counting template

	//Prepare sink fold that sums all the int elements of the stream graph, and make its result
	//available as a Future[Int]
	//In = Int, Mat = Materialized
	val sumSink: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)
	//((acc, next) => acc + next)

	//Connect tweets stream to count flow with VIA (because SUMSINK is a GRAPH)
	//Then connect the flow to the SINK useing toMat (tomaterialized)
	//KeepRight is to keep the materialized value
	//TODO see more explanation: https://manuel.bernhardt.io/2017/05/22/akka-streams-notused/
	val counterGraph: RunnableGraph[Future[Int]] =
		tweets.via(count).toMat(sumSink)(Keep.right)
	//when chaining these together,
		// Source[+Out, _Mat]
		// Flow[-In, +Out, +Mat]
		// Sink[-In, +Mat]
	//then we combine their materialized values
	//We want to use Keep.Right, to get the materialized type (appended to the right)
	//..Final materialized type is Future[Int]

	//note: this does not yet materialize the pipeline, just prepares
	//note the description of the flow


	//once the flow is connectd to sink it can be run, indicated
	//by RUnnableGraph type/

	//Calling run() uses implicit ActorMaterializer to materialize and run the fFlow
	val sum: Future[Int] = counterGraph.run() //contains a future of total length of our tweets.. If failure, future
	// completes with a Failure.
	sum.foreach(c => println(s"Total tweets processed: $c"))
}






object MaterializeExample extends App {
	import TweetAPI._

	val tweetsInMinuteFromNow = tweets // not really in second, just acting as if

	//#tweets-runnable-flow-materialized-twice
	val sumSink = Sink.fold[Int, Int](0)(_ + _)
	val counterRunnableGraph: RunnableGraph[Future[Int]] =
		tweetsInMinuteFromNow.filter(_.hashtags contains akkaTag).map(t => 1).toMat(sumSink)(Keep.right)
	//tweets.via(count).toMat(sumSink)(Keep.right)

	// materialize the stream once in the morning
	val morningTweetsCount: Future[Int] = counterRunnableGraph.run()
	// and once in the evening, reusing the flow
	val eveningTweetsCount: Future[Int] = counterRunnableGraph.run()

	//note: can reuse the runnable graph because it is a blueprint of a stream

	//print the results
	//A RunnableGraph may be reused and materialized multiple times, because it is only the “blueprint” of the stream
	//This means that if we materialize a stream, for example one that consumes a live stream of tweets within a
	// minute, the materialized values for those two materializations will be different, as illustrated by this
	// example:
	morningTweetsCount.foreach(c => println(s"Total tweets processed: $c"))
	eveningTweetsCount.foreach(c => println(s"Total tweets processed: $c"))

	val sum: Future[Int] = counterRunnableGraph.run()

	sum.map { c =>
		println(s"Total tweets processed: $c")
	}


	//Another way:
	val sum1: Future[Int] = tweets.map(t => 1).runWith(sumSink)
	sum1.foreach(c => println(s"Total tweets processed: $c"))
}