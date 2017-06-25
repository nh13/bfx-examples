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

import com.fulcrumgenomics.FgBioDef.{DirPath, FilePath, PathToBam}
import com.fulcrumgenomics.basecalling.BasecallingParams
import com.fulcrumgenomics.commons.io.PathUtil
import com.fulcrumgenomics.illumina.{RunInfo, Sample, SampleSheet}
import com.fulcrumgenomics.sopt.{arg, clp}
import com.fulcrumgenomics.util.{Io, ReadStructure}
import com.nilshomer.bfx.examples.pipelines.IlluminaDemultiplexingPipeline.SampleAndLanePipeline
import dagr.core.execsystem.Resource
import dagr.core.tasksystem._
import dagr.tasks.picard._
import htsjdk.samtools.SAMFileHeader.SortOrder
import htsjdk.samtools.ValidationStringency
import htsjdk.samtools.util.Iso8601Date


object IlluminaDemultiplexingPipeline {
  private case class SampleAndLanePipeline(lane: Int, sample: Sample, lanePipeline: Pipeline) {
    /** Produces the path to the BAM file for the given sample (optionally lane-level). */
    def toBamFile(outputDir: DirPath): PathToBam = {
      BasecallingParams.bamFileFrom(output=outputDir, sample=sample, lane=lane)
    }
  }
}


/**
  * Notes:
  * Read structure can be given in the following precedence:
  * 1. command line.
  * 2. sample sheat ("ReadStructure" column, must be the same for all samples).
  * 3. inferred from the RunInfo.xml.
  * Read structure must match run info...
  *
  */
@clp(group=PipelineGroups.SequencingRun, description=
  """
    |Demultiplexes an Illumina sequencing run.
    |
    |
    |First chains together Picard's CollectIlluminaLaneMetrics and CheckIlluminaDirectory to gather metrics on cluster
    |density and check that the provided data for the sequencing run is present and well-formed.  Next each lane is
    |de-multiplexed to produce a BAM per sample per lane using Picard's ExtractIlluminaBarcodes,
    |CollectIlluminaBasecallingMetrics, and IlluminaBasecallsToSam.  Finally, the BAMs for a sample are merged across
    |lanes (if necessary) and basic yield metrics are collected using Picard's CollectQualityYieldMetrics.
    |
    |# Inputs
    |
    |Basecalls should be located at `<run-dir>/Data/Intensities/BaseCalls`.
    |
    |## Sample Sheet
    |
    |The sample sheet is used to specify the various information and parameters for each sample.
    |
    |The required column headers for the Sample Sheet are:
    | * 'Sample_ID'          - the sample identifier (unique)
    | * 'Sample_Name'        - the sample name
    | * 'Index'              - the index bases for the i7 read
    |
    |Optional column headers for the Sample Sheet are:
    | * 'Library_ID'         - the identifier for the library (will be used for the LB tag in the BAM's read group). If
    |                          not present, then the 'Sample_Id' will be used.
    | * 'Sample_Project'     - the project for the given sample (will be added to the DS tag in the BAM's read group).
    | * 'Description'        - the description of the sample (will be added to the DS tag in the BAM's read group).
    |
    |For dual-indexed runs, please add the following column header:
    | * 'Index2'             - the index bases for the i5 read
    |
    |## Read Structure
    |
    |Read structures are made up of `<number><operator>` pairs much like the CIGAR string in BAM files. Four kinds of
    |operators are recognized:
    |
    |1. `T` identifies a template read
    |2. `B` identifies a sample barcode read
    |3. `M` identifies a unique molecular index read
    |4. `S` identifies a set of bases that should be skipped or ignored
    |
    |The last `<number><operator>` pair may be specified using a `+` sign instead of number to denote "all remaining
    |bases".  For more information on read structures, see the
    |[Read Structure Wiki Page](https://github.com/fulcrumgenomics/fgbio/wiki/Read-Structures)
    |
    |## Outputs
    |
    |A BAM file will be produced per-sample and placed in the output directory with name `<sample-name>.<library-id>.bam`.
    |NB: if the library identifier column is not found in the sample sheet, it will default to the sample identifier, so
    |in this case the output BAM will be named `<sample-name>.<sample-id>`.
    |
    |Basecalling metrics will be found in the `basecalling` sub-directory of the output directory:
    | * `<flowcell-barcode>.illumina_lane_metrics` : stores cluster density metrics.
    | * `<flowcell-barcode>.illumina_phasing_metrics` : stores illumina basecaling phasing metrics.
    | * `basecalling.lane-<lane>.metrics.txt` : stores the per-lane metrics for sample barcode demultiplexing.
    |
    |The yield of raw reads will be found in the output directory for each sample:
    | * `<sample-name>.<library-id>.quality_yield_metrics.txt`.
    |
    |For more information about metrics produced by Picard, see the
    |[Picard Metric Definitions Page](https://broadinstitute.github.io/picard/picard-metric-definitions.html)
  """)
class IlluminaDemultiplexingPipeline
(
  @arg(flag='i', doc="The input run directory.") val runFolder: DirPath,
  @arg(flag='s', doc="The Illumina Experiment Manager Sample Sheet.") val sampleSheet: FilePath,
  @arg(flag='o', doc="Output directory.") val output: DirPath,
  @arg(flag='t', doc="Path to a temporary directory.  Use output if none is given.") val tmp: Option[DirPath] = None,
  @arg(flag='r', doc="The read structure to use.") val readStructure: Option[ReadStructure] = None,
  private val maxThreadsPerIlluminaBasecallsToSam: Int = 16
) extends Pipeline(outputDirectory=Some(output)) {

  def build(): Unit = {
    val basecallsDir = runFolder.resolve("Data/Intensities/BaseCalls")
    val sampleSheet  = SampleSheet(this.sampleSheet)
    val runInfo      = RunInfo(runFolder.resolve("RunInfo.xml"))
    val outputDir    = output.resolve("basecalling") // the location to write any basecalling specific data

    Io.assertListable(runFolder)
    Io.assertListable(basecallsDir)

    Seq(output, outputDir).foreach(Io.mkdirs)

    // Get the # of lanes to analyze.
    val lanesToAnalyze = Range(1, runInfo.num_lanes+1)

    // Get the read structure.  Tries the command line then the RunInfo.xml, in that order.
    val readStructure: ReadStructure = this.readStructure.map(ReadStructure(_)).getOrElse(runInfo.read_structure)

    // Verify that the input read structure has the same # of total bases as the one from the RunInfo.  We have to
    // be careful as the fgbio read structure may end with a segment with an indefinite length.
    val runInfoTotalBases = runInfo.read_structure.flatMap(_.length).sum
    val readStructureTotalBases = readStructure.flatMap(_.length).sum
    if (readStructure.lastOption.exists(_.length.isEmpty)) {
      // Validate that we have at least as many
      if (runInfoTotalBases < readStructureTotalBases) {
        throw new IllegalStateException(s"Input read structure (with the last segment matching 0 or more bases) has more bases as in the RunInfo.xml: '$readStructureTotalBases' > '$runInfoTotalBases'")
      }
    }
    else {
      if (readStructureTotalBases != runInfoTotalBases) {
        throw new IllegalStateException(s"Input read structure does not have the same # of bases as in the RunInfo.xml: '$readStructureTotalBases' != '$runInfoTotalBases'")
      }
    }

    ///////////////////////////////////////////////////////////////////////
    // Gather some initial metrics and ensure that all the files are present
    ///////////////////////////////////////////////////////////////////////

    // NB: we use the read structure from the RunInfo.xml since CollectIlluminaLaneMetrics does not support skips
    // and molecular barcodes.  In fact, we care only about distinguishing the index and non-index reads.
    val laneMetrics = new CollectIlluminaLaneMetrics(
      runDirectory    = runFolder,
      output          = outputDir,
      flowcellBarcode = runInfo.flowcell_barcode,
      readStructure   = Some(runInfo.read_structure.toString)
    )
    // Set the validation stringency to LENIENT so that we do not fail in the case of a corrupt TileMetricsOut.bin, see:
    // https://github.com/broadinstitute/picard/pull/812
    laneMetrics.validationStringency = Some(ValidationStringency.LENIENT)

    // Check that the input basecalling directory has all the necessary data is not corrupt.
    val checkIlluminaDirectory = new CheckIlluminaDirectory(
      basecallsDir      = basecallsDir,
      lanes             = lanesToAnalyze,
      readStructure     = readStructure.toString
    )

    root ==> (laneMetrics :: checkIlluminaDirectory)

    ///////////////////////////////////////////////////////////////////////
    // De-multiplex by lane and merge sample data across lanes
    ///////////////////////////////////////////////////////////////////////

    // NB: we set the maximum # of threads for sample demultiplexing to be sixteen for one lane, as any more and
    // we either run out of memory or we don't really gain in speed (sorting dominates).  For more than one lane,
    // we lower the number of threads to ensure that we are less likely to run out memory and have to retry as we
    // are not so concerned with running the single demultiplexing as fast as possible.
    val maxThreadsForDemux = if (lanesToAnalyze.length > 1) Math.max(1, maxThreadsPerIlluminaBasecallsToSam) else maxThreadsPerIlluminaBasecallsToSam

    lanesToAnalyze.flatMap { lane =>
      // Get only that have this specific lane.  If the lane is not found, include the sample
      val samples = sampleSheet.filter(_.lane.forall(_ == lane)).toSeq
      val pipeline = new SingleLaneDemultiplexingPipeline(
        basecallsDir       = basecallsDir,
        lane               = lane,
        samples            = samples,
        runBarcode         = runInfo.run_barcode,
        readStructure      = readStructure,
        runDate            = Some(runInfo.run_date),
        outputDir          = Some(outputDir),
        tmpDir             = tmp,
        maxThreadsForDemux = maxThreadsForDemux
      )

      checkIlluminaDirectory ==> pipeline

      samples.map(sample => SampleAndLanePipeline(lane, sample, pipeline))
    }
    .groupBy(_.sample) // group by sample so we can merge data across lanes
    .foreach { case (sample, sampleAndLanePipelines) =>
      // Merge samples spread across multiple lanes
      val demuxPipelines  = sampleAndLanePipelines.map(_.lanePipeline)
      val inputBams       = sampleAndLanePipelines.map(_.toBamFile(basecallsDir))
      val outputName      = PathUtil.sanitizeFileName(s"${sample.sampleName}.${sample.libraryId}")
      val outputPrefix    = output.resolve(outputName)
      val unmappedBam     = PathUtil.pathTo(outputPrefix +  ".bam")

      val createUnmappedBam = inputBams match {
        case Seq(bam) =>
          // Just move it into place
          ShellCommand("mv", bam, unmappedBam.toString).withName(s"Move BAM")
        case bams     =>
          // Merge the input BAMs
          new MergeSamFiles(
            in        = bams,
            out       = unmappedBam,
            sortOrder = SortOrder.queryname
          ) withAsyncIo(true)
      }

      demuxPipelines.foreach(_ ==> createUnmappedBam)
      createUnmappedBam ==> (new CollectQualityYieldMetrics(in=unmappedBam) :: new DeleteBam(inputBams:_*))
    }
  }
}

/** Performs sample demultiplexing on a single lane. */
private class SingleLaneDemultiplexingPipeline(basecallsDir: DirPath,
                                               lane: Int,
                                               samples: Seq[Sample],
                                               runBarcode: String,
                                               readStructure: ReadStructure,
                                               runDate: Option[Iso8601Date] = None,
                                               outputDir: Option[DirPath] = None,
                                               tmpDir: Option[DirPath] = None,
                                               maxThreadsForDemux: Int = Resource.systemCores.toInt)
  extends Pipeline(suffix=Some("." + lane.toString)) {

  def build(): Unit = {
    val baseOutputDir     = outputDir.getOrElse(basecallsDir)
    val barcodeFile       = BasecallingParams.barcodeFileFrom(baseOutputDir, lane)
    val libraryParamsFile = BasecallingParams.libraryParamsFileFrom(baseOutputDir, lane)
    val barcodesDir       = outputDir.map(_.resolve(s"barcodes.$lane"))

    barcodesDir.foreach(Io.mkdirs)

    val writeBarcodeAndLibraryParamFiles = new WriteBarcodeAndLibraryParamFiles(
      paramsOutputDir = baseOutputDir,
      bamsOutputDir   = basecallsDir,
      samples         = samples,
      lane            = lane
    )

    val extractBarcodes = new ExtractIlluminaBarcodes(
      basecallsDir  = basecallsDir,
      lane          = lane,
      readStructure = readStructure.toString,
      barcodeFile   = barcodeFile,
      prefix        = barcodesDir.map(_.resolve("barcode_counts"))
    )

    val basecallingMetrics = new CollectIlluminaBasecallingMetrics(
      basecallsDir  = basecallsDir,
      lane          = lane,
      readStructure = readStructure.toString,
      barcodeFile   = Some(barcodeFile),
      prefix        = Some(baseOutputDir.resolve("basecalling")),
      barcodesDir   = barcodesDir
    )

    val basecallsToSam = new IlluminaBasecallsToSam(
      basecallsDir         = basecallsDir,
      lane                 = lane,
      runBarcode           = runBarcode,
      readStructure        = readStructure.toString,
      libraryParamsFile    = libraryParamsFile,
      runDate              = runDate,
      barcodesDir          = barcodesDir,
      maxThreads           = maxThreadsForDemux,
      maxReadsInRamPerTile = Some(300000), // This is set low as not to use too much memory!
      tmpDir               = tmpDir
    ).withAsyncIo(true)

    // NB: if we process a HiSeqX, run CollectHiSeqXPfailMetrics
    root ==> writeBarcodeAndLibraryParamFiles ==> extractBarcodes ==> (basecallsToSam :: basecallingMetrics)
  }
}

/** Writes the barcode file needed for Picard's ExtractIlluminaBarcodes and the library parameter file needed by
  * Picard's IlluminaBasecallsToSam.  Supports both single-indexed and dual-indexed runs. */
private class WriteBarcodeAndLibraryParamFiles(paramsOutputDir: DirPath,
                                               bamsOutputDir: DirPath,
                                               samples: Seq[Sample],
                                               lane: Int
                                              ) extends SimpleInJvmTask {
  withName(s"WriteBarcodeAndLibraryParamFiles.$lane")

  def run(): Unit = {
    val params = BasecallingParams.from(sampleSheet=new SampleSheet(samples), lanes=Seq(lane), output=paramsOutputDir, bamOutput=Some(bamsOutputDir))
    params.foreach { param =>
      logger.info(s"Barcode params for lane ${param.lane} written to: ${param.barcodeFile}")
      logger.info(s"Library params for lane ${param.lane} written to: ${param.libraryParamsFile}")
    }
  }
}
