[![License](http://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/nh13/bfx-examples/blob/master/LICENSE)
[![Language](http://img.shields.io/badge/language-scala-brightgreen.svg)](http://www.scala-lang.org/)

# Bioinformatics Example Repository

This repository contains example tools and pipelines discussed on [my blog](http://www.nilshomer.com/blog).

## Posts

Here are some posts you may be interested in:

 * [Sample Demultiplexing an Illumina Sequencing Run](http://nilshomer.com/2017/06/25/sample-demultiplexing-an-illumina-sequencing-run)

## Getting Started

To run bfx-examples you will need [Java 8](https://java.com/en/download/) (aka Java 1.8) or later installed.  To see the version of Java currently installed run the following in a terminal:

```
java -version
```

If the reported version on the first line starts with `1.8` or higher, you are all set.

Once you have Java installed and a release downloaded you can run:

* Run `java -jar bfx-examples-tool.jar` to get a list of available tools
* Run `java -jar bfx-examples-pipelines.jar` to get a list of available pipelines
* Run `java -jar bfx-examples-tool.jar <Tool Name>` to see detailed usage instructions on any tool (similarly for pipelines)

When running tools we recommend the following set of Java options as a starting point though individual tools may need more or less memory depending on the input data:

```
java -Xmx4g -XX:+AggressiveOpts -XX:+AggressiveHeap -jar bfx-examples-tools.jar ...
```

## Documentation

Each tool and pipeline has detailed usage and argument documentation that can be viewed at the command line by running the tool in question with no arguments.

Documentation is also available online:
* Tool usage documentation is available [here](tools/latest)
* Pipeline usage documentation is available [here](pipelines/latest)
* Documentation of the various metrics files is available [here](metrics/latest)
