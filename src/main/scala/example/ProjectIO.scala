package example

import java.io.File

import cilib.exec.Measurement
import scalaz.concurrent.Task
import scalaz.stream.{Process, Sink, sink}

object ProjectIO {

  def csvSink[A](
                  filename: String,
                  f: Measurement[A] => String): Sink[Task, Measurement[A]] = {
    val fileWriter = new java.io.PrintWriter(new File(filename))
    sink
      .lift((input: Measurement[A]) => Task.delay(fileWriter.println(f(input))))
      .onComplete(Process.eval_(Task.delay(fileWriter.close())))
  }

}
