package com.softwaremill.reactive.complete

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl._
import akka.util.ByteString
import com.softwaremill.reactive._

import scala.concurrent.duration._

object SenderComplete extends App with Logging {
  implicit val system = ActorSystem()
  val serverConnection = Tcp().outgoingConnection("localhost", 9182)

  val getLines = () => scala.io.Source.fromFile("/Users/adamw/projects/reactive-akka-pres/data/2008.csv").getLines()

  val linesSource = Source(getLines).map { line => ByteString(line + "\n") }
  val logCompleteSink = Sink.onComplete(r => logger.info("Completed with: " + r))

  val graph = FlowGraph.closed() { implicit b =>
    import FlowGraph.Implicits._

    val broadcast = b.add(Broadcast[ByteString](2))

    val logWindowFlow = Flow[ByteString]
      .groupedWithin(10000, 1.seconds)
      .map(group => group.map(_.size).foldLeft(0)(_ + _))
      .map(groupSize => logger.info(s"Sent $groupSize bytes"))

    linesSource ~> broadcast ~> serverConnection  ~> logCompleteSink
                   broadcast ~> logWindowFlow     ~> Sink.ignore
  }

  implicit val mat = ActorFlowMaterializer()
  graph.run()
}
