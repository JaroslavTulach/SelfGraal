## [Self](http://selflanguage.org) on top of [GraalVM](http://graalvm.org)

[![Build Status](https://travis-ci.org/JaroslavTulach/SelfGraal.svg?branch=master)](https://travis-ci.org/JaroslavTulach/SelfGraal)

This is my attempt to implement as trivial language as possible via **Truffle**.
After a bit of research I decided to implement [Self](http://selflanguage.org).
There are at least three reasons for doing that:

When I was visiting the Smalltalk course at Charles University in 
the middle of nineteen nineties we dedicated one hour to something _simpler_
than Smalltalk called [Self](http://selflanguage.org). Smalltalk itself was quite
simple. Yet, surprisingly to us, the [Self](http://selflanguage.org) language
really was simpler. The _simplicity_ is one reason for trying to
re-implement it.

The other thing I learned in the course was that we don't have hardware
to run [Self](http://selflanguage.org). A computer with 32MB of memory and SunOS
was needed and we had just poor Pentiums with at most four megabytes hardly running
Linux and X11. As a result I have not yet written and executed a single 
[Self](http://selflanguage.org) program yet. Re-implementing the language is a nice
opportunity to do so!

Last, but definitely not least. There would be no Java, no [GraalVM](http://graalvm.org)
without the [Self](http://selflanguage.org) language. Re-implementing the language
is a way to pay back to this great pioneer of dynamic compilation.

Join me celebrating [Self](http://selflanguage.org) by forking this repository.

### Parsing Using Partially Evaluated Parser Combinator

The implementation also includes an
[experimental parser combinator](https://github.com/JaroslavTulach/SelfGraal/commit/3a9f940)
which is able to speed itself significantly by using Truffle API.
To see the difference execute:
```
SelfGraal$ JAVA_HOME=/jdk1.8.0 mvn install -DSelfGraal.Benchmark=BenchJDK8 2>&1 | grep BenchJDK8
BenchJDK8 took 306 ms on average

SelfGraal$ JAVA_HOME=/graalvm/ mvn install -DSelfGraal.Benchmark=BenchGraalVM 2>&1 | grep BenchGraalVM
BenchGraalVM took 127 ms on average
```
The parser combinators (`seq`, `alt`, `rep`) compose an *AST* and use Truffle
constructs (`CallTarget`, `@CompilationFinal`, etc.) to optimize it. The parser
is a Truffle language on its own!
