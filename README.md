# ssf_scala

A Scala client for emitting spans in the [Sensor Sensibility Format](https://github.com/stripe/veneur/tree/master/ssf).

# Features

* No dependencies, just boring Scala and Java stuff
* Asynchronous or Synchronous, your call!
* UDP only
* Option for max queue size when using asynchronous
* "Swallows" relevant exceptions (IO, Unresolveable) by **default** to prevent runtime errors breaking your service

# Examples

```scala
import github.gphat.ssf_scala.Client

val c = new Client(hostname = "localhost", port = 8128, service = "my-service")
c.startSpan(name = "slowTask")
// ... Do a thing!
c.finishSpan(span)
```

# Asynchronous (default behavior)

Metrics are locally queued via a BlockingQueue and emptied out in single
ThreadExecutor thread. Messages are sent as quickly as the blocking `take` in
that thread can fetch an item from the head of the queue.

You may provide a `maxQueueSize` when creating a client. Doing so will prevent
the accidental unbounded growth of the metric send queue. If the limit is reached
then new metrics **will be dropped** until the queue has room again. Logs will
be emitted in this case for every `consecutiveDropWarnThreshold` drops. You can
adjust this when instantiating a client.

**Note:** You can call `c.shutdown` to forcibly end things. The threads in this
executor are flagged as deaemon threads so ending your program will cause any
unsent metrics to be lost.

# Synchronous

If you instantiate the client with `asynchronous=false` then the various metric
methods will immediately emit your metric synchronously using the underlying
sending mechanism. This might be great for UDP but other backends may have
a high penalty!

```scala
val c = new Client(asynchronous = false)
```

## Updating protobuf definition

```sh
$ scalapbc -I ~/go/src/github.com/stripe/veneur ~/go/src/github.com/stripe/veneur/ssf/sample.proto --scala_out=.
```
