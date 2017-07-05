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

package com.nilshomer.bfx.examples.pipelines

import com.fulcrumgenomics.FgBioDef.{DirPath, SafelyClosable, yieldAndThen}
import com.fulcrumgenomics.commons.io.{Io, PathUtil}
import com.fulcrumgenomics.sopt.{arg, clp}
import dagr.core.tasksystem.{Dependable, Pipeline, ShellCommand}
import dagr.tasks.DagrDef.{PathPrefix, PathToBam, PathToFasta, PathToIntervals, PathToVcf}
import dagr.tasks.bwa.Bwa
import dagr.tasks.fgbio._
import dagr.tasks.picard._
import dagr.tasks.vc.VarDictJavaEndToEnd
import htsjdk.variant.vcf.VCFFileReader

@clp(group=PipelineGroups.SequencingRun, description=
  """
    |Creates single-UMI consensus reads and calls somatic variants.
    |
    |Starts with an unmapped BAM file with the UMI in a tag (`RX` by default).
    |
    |The reads are aligned with bwa-mem, then duplicate marked using the UMI tag, grouped by molecular ID to
    |create a "grouped" BAM file, and consensus called to produce per-molecule consensus reads.
    |
    |The consensus reads are aligned with bwa-mem and subsequently filtered.
    |
    |The de-duplicated BAM and consensus reads are both variant called with VarDictJava and are there run through a
    |panel of metrics.  This allows us to compare the difference between using the single-UMIs simply for better
    |duplicate marking, or for improving the quality of reads through consensus calling.
  """)
class SingleStrandUmiSomaticVariantCalling
(
  @arg(flag='i', doc="Path to the unmapped BAM file.")                                               val inputBam: DirPath,
  @arg(flag='r', doc="Path to the reference FASTA.")                                                 val ref: PathToFasta,
  @arg(flag='l', doc="Regions to analyze.")                                                          val intervals: PathToIntervals,
  @arg(flag='v', doc="Truth VCF for the sample being sequenced.")                                    val truthVcf: Option[PathToVcf] = None,
  @arg(flag='o', doc="Path prefix for output files.")                                                val output: PathPrefix,
  @arg(flag='U', doc="The tag containing the raw UMI.")                                              val umiTag: String = "RX",
  @arg(flag='I', doc="The tag to store the molecular identifier.")                                   val molecularIdTag: String = "MI",
  @arg(flag='m', doc="Minimum mapping quality to include reads.")                                    val minMapQ: Int = 10,
  @arg(flag='s', doc="The UMI assignment strategy; one of 'Identity', 'Edit', 'Adjacency'.")         val strategy: String = "Adjacency",
  @arg(flag='e', doc="The allowable number of edits between UMIs.")                                  val edits: Int = 1,
  @arg(flag='1', doc="The Phred-scaled error rate for an error prior to the UMIs being integrated.") val errorRatePreUmi: Option[Int] = Some(45),
  @arg(flag='2', doc="The Phred-scaled error rate for an error post the UMIs have been integrated.") val errorRatePostUmi: Option[Int] = Some(30),
  @arg(flag='q', doc="The minimum input base quality for bases to go into consensus.")               val minInputBaseQuality: Option[Int] = Some(30),
  @arg(flag='Q', doc="Mask (make 'N') consensus bases with quality less than this threshold.")       val minConsensusBaseQuality: Int = 40,
  @arg(flag='M', doc="The minimum number of reads to produce a consensus base.")                     val minReads: Int = 1,
  @arg(          doc="The maximum consensus error rate per base.")                                   val maxBaseErrorRate: Double = 0.1,
  @arg(          doc="The maximum consensus error rate for per read")                                val maxReadErrorRate: Double = 0.05,
  @arg(          doc="The maximum fraction of bases that are Ns in a consensus read.")               val maxNoCallFraction: Double = 0.1,
  @arg(          doc="The minimum allele frequency to use for variant calling.")                     val minimumAf: Double = 0.0025
) extends Pipeline(outputDirectory=Some(output)) {

  Io.assertReadable(inputBam)
  Io.assertCanWriteFile(output, parentMustExist=false)
  Io.assertReadable(intervals)

  def build(): Unit = {

    // Files that we're going to keep around
    val dir = output.getParent
    val pre = output.getFileName
    def f(ext: String) = dir.resolve(pre + ext)

    val mappedRaw          = f(".raw.aligned.bam")
    val dedupedRaw         = f(".raw.deduped.bam")
    val grouped            = f(".grouped.bam")
    val familySizes        = f(".family_sizes.txt")
    val consensusUnaligned = f(".consensus.unmapped.bam")
    val consensusAligned   = f(".consensus.unfiltered.bam")
    val consensusFiltered  = f(".consensus.bam")

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Map, mark duplicates, and group the raw reads
    /////////////////////////////////////////////////////////////////////////////////////////////
    val bwaRaw    = Bwa.bwaMemStreamed(unmappedBam=inputBam, mappedBam=mappedRaw, ref=ref, samToFastqCores=0, bwaMemMemory="6G")
    val markDupes = new MarkDuplicates(inputs=Seq(mappedRaw), out=Some(dedupedRaw), templateUmiTag=Some(umiTag))
    val group     = new GroupReadsByUmi(in=mappedRaw, out=grouped, rawTag=Some(umiTag), familySizeOut=Some(familySizes),
      minMapQ=Some(minMapQ), strategy=AssignmentStrategy.withName(strategy), edits=Some(edits))
    root ==> bwaRaw ==> (markDupes :: group)

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Call & align the consensus reads
    /////////////////////////////////////////////////////////////////////////////////////////////
    val call = new CallMolecularConsensusReads(
      in                  = grouped,
      out                 = consensusUnaligned,
      tag                 = Some(molecularIdTag),
      readNamePrefix      = None,
      errorRatePreUmi     = errorRatePreUmi,
      errorRatePostUmi    = errorRatePostUmi,
      minInputBaseQuality = minInputBaseQuality,
      minReads            = minReads
    )
    val bwaConsensus = Bwa.bwaMemStreamed(unmappedBam=consensusUnaligned, mappedBam=consensusAligned, ref=ref, samToFastqCores=0, bwaMemMemory="6G")
    group ==> call ==> bwaConsensus ==> new DeleteBam(consensusUnaligned)

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Filter the consensus reads and clip overlapping reads
    /////////////////////////////////////////////////////////////////////////////////////////////
    val filter        = FilterConsensusReads(in=consensusAligned, out=consensusFiltered, ref=ref, minReads=minReads,
      maxReadErrorRate=maxReadErrorRate, maxBaseErrorRate=maxBaseErrorRate, minQuality=minConsensusBaseQuality,
      maxNoCallFraction=maxNoCallFraction)
    val regionsIl     = dir.resolve("calling_regions.interval_list")
    val regionsBed    = dir.resolve("calling_regions.bed")
    val copyIntervals = new ShellCommand("cp", this.intervals.toString, regionsIl.toString)
    val makeBed       = new IntervalListToBed(intervals=regionsIl, bed=regionsBed)
    bwaConsensus ==> filter
    root ==> copyIntervals ==> makeBed

    /////////////////////////////////////////////////////////////////////////////////////////////
    // Run variant calling and metrics on:
    // 1. the mapped and de-duplicated raw reads
    // 2. the mapped consensus reads
    /////////////////////////////////////////////////////////////////////////////////////////////
    Seq((dedupedRaw, markDupes), (consensusFiltered, filter)).foreach { case (bam: PathToBam, predecessor: Dependable) =>
      val casm    = new CollectAlignmentSummaryMetrics(in=bam, ref=ref)
      val chsm    = new CollectHsMetrics(in=bam, ref=ref, targets=regionsIl)
      val csam    = new CollectSequencingArtifactMetrics(in=bam, ref=ref, intervals=Some(regionsIl), minBq=Some(30), contextSize=Some(0), variants=None)
      val clipBam = PathUtil.replaceExtension(bam, ".clipped.bam")
      val clip    = new ClipOverlappingReads(in=consensusFiltered, out=clipBam, ref=ref, softClip=false)
      val vcf     = PathUtil.replaceExtension(bam, ".vcf")
      val vardict = new VarDictJavaEndToEnd(tumorBam=clipBam, tumorName=Some("tumor"), bed=regionsBed, ref=ref, out=vcf, minimumAf=minimumAf, minimumQuality=Some(30))
      truthVcf.foreach { truth =>

        vardict ==> copyIntervals ==> new GenotypeConcordance(truthVcf=truth, callVcf=vcf, prefix=PathUtil.removeExtension(vcf), intervals=List(regionsIl), truthSample=sampleIn(truth), callSample="tumor")
      }
      makeBed       ==> vardict
      copyIntervals ==> (chsm :: csam)
      predecessor   ==> (casm :: chsm :: csam :: vardict)
    }
  }

  /** Gets the first sample name in the VCF header. */
  private def sampleIn(vcf: PathToVcf): String = {
    val in = new VCFFileReader(vcf.toFile, false)
    yieldAndThen(in.getFileHeader.getSampleNamesInOrder.iterator().next()) { in.safelyClose() }
  }
}