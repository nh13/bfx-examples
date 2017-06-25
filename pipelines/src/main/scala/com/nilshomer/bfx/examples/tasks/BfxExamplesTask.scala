/*
 * The MIT License
 *
 * Copyright (c) 2017 Nils Homer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package com.nilshomer.bfx.examples.tasks

import java.nio.file.Path

import dagr.core.config.Configuration
import dagr.core.execsystem.{Cores, Memory}
import dagr.core.tasksystem.{FixedResources, ProcessTask}
import dagr.tasks.JarTask

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object BfxExamplesTask {
  /** Override to return the path to the bfx-examples-tools jar. */
  val BfxExamplesJarConfigPath = "bfx-examples-tools.jar"
}

/**
  * Base Task for any task in the bfx-examples-tools jar.
  *
  * @param compressionLevel the compress level to use for HTSJDK.
  */
abstract class BfxExamplesTask(var compressionLevel: Option[Int] = None)
  extends ProcessTask with JarTask with FixedResources with Configuration {
  requires(Cores(1), Memory("4G"))

  /** Looks up the first super class that does not have "\$anon\$" in its name. */
  lazy val commandName: String = JarTask.findCommandName(getClass, Some("BfxExamplesTask"))

  name = commandName

  override final def args: Seq[Any] = {
    val buffer = ListBuffer[Any]()
    val jvmProps = mutable.Map[String,String]()
    compressionLevel.foreach(c => jvmProps("samjdk.compression_level") = c.toString)
    buffer.appendAll(jarArgs(this.bfxExamplesJar, jvmProperties=jvmProps, jvmMemory=this.resources.memory))
    buffer += commandName
    addBfxExamplesArgs(buffer)
    buffer
  }

  /** Can be overridden to use a specific Bfx Examples Tools jar. */
  protected def bfxExamplesJar: Path = configure[Path](BfxExamplesTask.BfxExamplesJarConfigPath)

  /** Implement this to add the tool-specific arguments */
  protected def addBfxExamplesArgs(buffer: ListBuffer[Any]): Unit

  /** Sets the compression level to a specific value. */
  def withCompression(i: Int) : this.type = { this.compressionLevel = Some(i); this }
}