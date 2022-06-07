NUMA-aware Virtual Threads
===

Modern processors are much faster than RAM. This leads to a forced "starvation" of the processor, that is, to its
idle time waiting for new data. To reduce the number of requests to RAM, high-speed cache memory was added to
processors. However, due to the rapid growth in the size of programs and operating systems, these optimizations 
aren't sufficient. In addition, RAM has a limited number of channels, and processor cores simply cannot access
memory all at the same time.

To solve this problem, the NUMA (Non-Uniform Memory Access) architecture was proposed. The essence of this
architecture is that each processor has its own block of memory (processors and memory blocks connected to each
other are called NUMA nodes). If the application is organized in such a way that each processor works with its own
part of the data, then the performance grows by a multiple of the number of processors (or memory blocks).
Processors can also access remote memory blocks, but this requires additional time spent on data transfer. Thus,
the final performance is highly dependent on the structure of the program.

NUMA support in Java
---

To work with NUMA in Java, it is needed to use JNA (Java Native Access), which allows to interact with native code
from Java. This project only works with the library for Linux, but similar methods can be implemented for other
operating systems.

On Linux, the `libnuma` library is used to work with NUMA. It provides an interface with which one can control the
scheduler (for example, choose which processors a particular thread can run on), as well as dynamic memory
allocation.

Virtual Threads
---

Java 19 added support for virtual threads as a preview feature. Unlike regular threads, which in most Java
implementations correspond to regular operating system threads, virtual threads are lightweight user-space threads.
Such threads are managed by the Java machine itself, without the involvement of OS mechanisms. This allows to speed
up the creation of new virtual threads many times over, and also makes it possible to work simultaneously with
millions of virtual threads without significant memory overhead.

Currently (May 2022), support for virtual streams is as follows. The `java.lang.VirtualThread` class declares a
static thread pool on which the code of virtual threads is executed. This pool is an instance of the `ForkJoinPool`
class, and instances of the `jdk.internal.misc.CarrierThread` class are used as worker threads in it. This class,
along with the `VirtualThread` class and JVM code, allows execution to be switched between virtual threads 
transparently for user code.

At the same time, all control of virtual thread is hidden from the user. Using the public API, the user cannot
choose to execute virtual threads on their own thread pool or customize the behavior of an existing pool. This
greatly complicates NUMA support, since virtual threads do not exist for the OS, and we cannot guarantee the
execution of a virtual thread on selected processors.

Project structure
---

This project consists of the following packages:

* `com.activeviam.experiments.loom.numa.data` contains classes responsible for allocating and accessing memory.
* `com.activeviam.experiments.loom.numa.platform` contains classes responsible for NUMA support on various 
platforms. Contains subpackages `*.linux` with classes for working on Linux OS and `*.share` with a stub class for
unsupported platforms.
* `com.activeviam.experiments.loom.numa.thread.virtual` contains classes that add multiple thread pools to run
virtual threads on specific NUMA nodes. This code is forced to contain parts copied from the Java library code,
uses reflection and refers to internal interfaces, since the methods and classes necessary for its operation are
hidden from the public API.
* `com.activeviam.experiments.loom.numa.util` contains classes with helper functions.

The main class is `com.activeviam.experiments.loom.numa.NumaDemo`. It creates arrays of numbers on the given NUMA
nodes, and then measures the access time to these arrays from other nodes.

Compilation and execution
---

To compile the project, one need Java 19 with [JEP 425](https://openjdk.java.net/jeps/425) support and Maven.

To run the project, one need to pass the following arguments to the JVM: `--enable-preview --add-exports
java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED`. This project requires
`numactl` package to be installed.
