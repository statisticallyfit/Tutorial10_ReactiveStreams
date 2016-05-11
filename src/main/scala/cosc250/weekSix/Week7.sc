import scala.concurrent.{Promise, Future}

import scala.concurrent.ExecutionContext.Implicits.global

val p = Promise[Int]
val future = p.future


future.map(_ * 2)