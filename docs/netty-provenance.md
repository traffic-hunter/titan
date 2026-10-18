# Netty source attribution

Titan uses Netty as a dependency and has some source-level implementations that
were informed by Netty. The files below carry Netty's relevant copyright and a
change notice in their headers. Titan's own copyright applies to its changes.

The upstream links point to Netty `4.2.15.Final`, the version in Titan's current
version catalog. They are comparison references, **not a claim that this was the
exact version originally consulted**. Earlier Titan history used Netty
`4.2.2.Final` when `ScheduledPromiseImpl` was introduced. The original reference
version for each implementation has not been established.

| Titan source and affected methods | Corresponding Netty source | Titan changes |
| --- | --- | --- |
| [`AbstractEventLoop.start()`](../core/src/main/java/org/traffichunter/titan/core/channel/AbstractEventLoop.java) | [`SingleThreadEventExecutor.startThread()`](https://github.com/netty/netty/blob/netty-4.2.15.Final/common/src/main/java/io/netty/util/concurrent/SingleThreadEventExecutor.java) | Uses Titan's `EventLoopStatus`, atomic updater, and `ThreadPoolExecutor` lifecycle. |
| [`SingleThreadEventLoop.loadTaskAfterCompletedScheduledTask()` and `pollScheduledTask()`](../core/src/main/java/org/traffichunter/titan/core/channel/SingleThreadEventLoop.java) | [`AbstractScheduledEventExecutor.fetchFromScheduledTaskQueue()` and `pollScheduledTask()`](https://github.com/netty/netty/blob/netty-4.2.15.Final/common/src/main/java/io/netty/util/concurrent/AbstractScheduledEventExecutor.java) | Uses Titan's `ScheduledPromise`, task queues, shutdown behavior, and clock. |
| [`ScheduledPromiseImpl.compareTo()`, `run()`, `cancel()`, `priorityQueueIndex()`](../core/src/main/java/org/traffichunter/titan/core/util/concurrent/ScheduledPromiseImpl.java) | [`ScheduledFutureTask`](https://github.com/netty/netty/blob/netty-4.2.15.Final/common/src/main/java/io/netty/util/concurrent/ScheduledFutureTask.java) | Integrates Titan's `Promise`, event loop, periodic-task handling, and clock. |
| [`PromiseImpl.await()` and `notifyListeners()`](../core/src/main/java/org/traffichunter/titan/core/util/concurrent/PromiseImpl.java) | [`DefaultPromise`](https://github.com/netty/netty/blob/netty-4.2.15.Final/common/src/main/java/io/netty/util/concurrent/DefaultPromise.java) | Uses Titan's completion state, listener storage, and `EventExecutorService`. |
| [`NewIONetChannel.connect0()`](../core/src/main/java/org/traffichunter/titan/core/channel/NewIONetChannel.java) | [`NioSocketChannel.doConnect()`](https://github.com/netty/netty/blob/netty-4.2.15.Final/transport/src/main/java/io/netty/channel/socket/nio/NioSocketChannel.java) | Uses Titan's channel, event-loop registration, and error handling. |

The Netty copyright year in each Titan header comes from the corresponding
upstream source file. The separate `NOTICE` file records Netty attribution for
source and binary distributions. The Apache License, Version 2.0, is in
[`LICENSE`](../LICENSE).

General Java NIO patterns, such as iterating selected keys, setting interest
operations, and waking a selector, are not listed as Netty-derived source here.
