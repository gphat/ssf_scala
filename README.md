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

## Updating protobuf definition

```sh
$ scalapbc -I ~/go/src/github.com/stripe/veneur ~/go/src/github.com/stripe/veneur/ssf/sample.proto --scala_out=.
```
