package cosc250.util

/**
  *
  */


import scala.concurrent.future
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random
import scala.util.{Success, Failure}



/**
  *
  */
object Wait {
	def hangOnS[T](future: Future[T], start: Int = 0,msg: String = ""): Future[T] = {
		var count = start
		while(!future.isCompleted){
			//println(s"Slept $count times")

			Thread.sleep(100)
			count += 1
		}

		//help don't know why: this isn't printing!!??
		/*future.onComplete {
			case Success(_) => println(future)
			case Failure(_) => println(future)
			//case s@Success(_) => println(s"Future($s)")
			//case f@Failure(_) => println(s"Future($f)")
		}*/
		println(s"$msg $future")

		future
	}

	def hangOn[T](future: Future[T], start: Int = 0): Future[T] = {
		var count = start
		while(!future.isCompleted){
			//println(s"Slept $count times")

			Thread.sleep(10)
			count += 1
		}
		future
	}
}