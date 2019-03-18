package github.gphat.ssf_scala

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal
import _root_.ssf.sample.SSFSpan
import java.io.ByteArrayOutputStream
import java.net.{InetSocketAddress,SocketException}
import java.nio.ByteBuffer
import java.nio.channels.{DatagramChannel,UnresolvedAddressException}
import java.nio.charset.StandardCharsets
import java.util.{LinkedList,Random}
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Logger

class Client(
  hostname: String = "127.0.0.1",
  port: Int = 8128,
  service: String,
  rng: PositiveRandom = new PositiveRandom(),
  allowExceptions: Boolean = false,
  asynchronous: Boolean = true,
  maxQueueSize: Option[Int] = None,
  consecutiveDropWarnThreshold: Long = 1000,
  val consecutiveDroppedMetrics: AtomicLong = new AtomicLong(0),
  version: Byte = 0
) {
  private[this] val log: Logger = Logger.getLogger(classOf[Client].getName)

  val clientSocket = DatagramChannel.open.connect(new InetSocketAddress(hostname, port))

  private[ssf_scala] val queue: LinkedBlockingQueue[SSFSpan] =
    maxQueueSize.map({
      capacity => new LinkedBlockingQueue[SSFSpan](capacity)
    }).getOrElse(
      // Unbounded is kinda dangerous, but sure!
      new LinkedBlockingQueue[SSFSpan]()
    )

  // This is an Option[Executor] to allow for NOT sending things.
  // We'll make an executor if we are running in asynchronous mode then spin up
  // the thread-works.
  private[this] val executor: Option[ExecutorService] = if(asynchronous) {
    Some(Executors.newSingleThreadExecutor(new ThreadFactory {
      override def newThread(r: Runnable): Thread = {
        val t = Executors.defaultThreadFactory.newThread(r)
        t.setDaemon(true)
        t
      }
    }))
  } else {
    None
  }

  // If we are running asynchronously, then kick off our long-running task that
  // repeatedly polls the queue and sends the available metrics down the road.
  executor.foreach { ex =>
    val task = new Runnable {
      def tick(): Unit = try {
        Option(queue.take).foreach(send)
      } catch {
        case _: InterruptedException => Thread.currentThread.interrupt
        case NonFatal(exception) => {
          log.warning(s"Swallowing exception thrown while sending metric: $exception")
        }
      }

      def run(): Unit = {
        while (!Thread.interrupted) {
          tick
        }
      }
    }

    ex.submit(task)
  }

  // Encode and send a span
  private def send(span: SSFSpan): Unit = {
    try {
      clientSocket.write(ByteBuffer.wrap(span.toByteArray))
    } catch {
      case se @ (_ : SocketException | _ : UnresolvedAddressException) => {
        // Check if we're allowing exceptions and rethrow if so. We didn't use
        // a guard on the case because then we'd need a second case to catch
        // the !allowExceptions case!
        if(allowExceptions) {
          throw se
        }
      }
    }
  }

  def shutdown(): Unit = {
    // It's pretty safe to just forcibly shutdown the executor and interrupt
    // the running async task.
    executor.foreach(_.shutdownNow)
  }

  def startSpan(
    name: String = "unknown", tags: Map[String,String] = Map.empty, parent: Option[SSFSpan] = None,
    indicator: Boolean = false, service: String = service
  ): SSFSpan = {
    val id = rng.nextNonNegative
    var sample = SSFSpan(
      id=id,
      traceId=id, // We'll pre-set this, it will be overriden if we have a parent
      startTimestamp=System.nanoTime,
      name=name,
      tags=tags,
      indicator=indicator,
      service=service,
    )
    sample = parent.map({ p =>
      sample.withTraceId(p.traceId).withParentId(p.id)
    }).getOrElse(sample)
    sample
  }

  def finishSpan(span: SSFSpan): Unit = {
    val finalSpan = span.withEndTimestamp(System.nanoTime)
    if(asynchronous) {
      // Queue it up! Leave encoding for later so get we back as soon as we can.
      if (!queue.offer(finalSpan)) {
        val dropped = consecutiveDroppedMetrics.incrementAndGet
        if (dropped == 1 || (dropped % consecutiveDropWarnThreshold) == 0) {
          log.warning(
            "Queue is full. Metric was dropped. " +
              "Consider decreasing the defaultSampleRate or increasing the maxQueueSize."
          )
        }
      }
    } else {
      consecutiveDroppedMetrics.set(0)
      // Just send it.
      send(finalSpan)
    }
  }
}

class PositiveRandom extends Random {
 def nextNonNegative(): Int = {
   return next(31)
 }
}
