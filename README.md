[![License](http://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/nh13/bfx-examples/blob/master/LICENSE)
[![Language](http://img.shields.io/badge/language-scala-brightgreen.svg)](http://www.scala-lang.org/)

# Bioinformatics Example Repository

This repository contains example tools and pipelines discussed on [my blog](http://www.nilshomer.com/blog).

Detailed user documentation is available on the [project website](http://nh13.github.io/bfx-examples/) including [tool usage](http://nh13.github.io/bfx-examples/tools/latest), [pipeline usage](http://nh13.github.io/bfx-examples/pipelines/latest), and [documentation of metrics produced](http://nh13.github.io/bfx-examples/metrics/latest).  

## Posts

Below we list posts that reference example tools, pipelines, and metrics contained in this repository:

 * [Sample Demultiplexing an Illumina Sequencing Run](http://nilshomer.com/2017/06/25/sample-demultiplexing-an-illumina-sequencing-run)


## Building 
### Cloning the Repository

 To clone the repository: `git clone https://github.com/nh13/bfx-examples.git`

### Running the build
 bfx-examples is built using [sbt](http://www.scala-sbt.org/).

 Use ```sbt assembly``` to build an executable jar for tools in ```tools/target/scala-2.12/``` and for pipelines in ```pipelines/target/scala-2.12/```.
 Tests may be run with ```sbt test```.
 Java SE 8 is required.

## Command line

 * Use `java -jar tools/target/scala-2.12/bfx-examples-tools.jar` to see the tools supported.  
 * Use `java -jar tools/target/scala-2.12/bfx-examples-tools.jar <command>` to see the help message for a particular tool.
 * Use `java -jar pipelines/target/scala-2.12/bfx-examples-pipelines.jar` to see the pipelines supported.  
 * Use `java -jar pipelines/target/scala-2.12/bfx-examples-pipelines.jar <command>` to see the help message for a particular pipeline.

### Configuring pipelines

Please see the [example `bfx-examples-pipelines` configuration](https://github.com/nh13/bfx-examples/blob/master/pipelines/src/main/resources/application.conf) for customizing dagr for your environment.


