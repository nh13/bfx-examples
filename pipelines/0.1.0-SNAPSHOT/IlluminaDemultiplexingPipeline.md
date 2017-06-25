---
title: IlluminaDemultiplexingPipeline
---

# IlluminaDemultiplexingPipeline

## Overview
**Group:** Sequencing Run Pipelines

Demultiplexes an Illumina sequencing run.


First chains together Picard's CollectIlluminaLaneMetrics and CheckIlluminaDirectory to gather metrics on cluster
density and check that the provided data for the sequencing run is present and well-formed.  Next each lane is
de-multiplexed to produce a BAM per sample per lane using Picard's ExtractIlluminaBarcodes,
CollectIlluminaBasecallingMetrics, and IlluminaBasecallsToSam.  Finally, the BAMs for a sample are merged across
lanes (if necessary) and basic yield metrics are collected using Picard's CollectQualityYieldMetrics.

# Inputs

Basecalls should be located at `<run-dir>/Data/Intensities/BaseCalls`.

## Sample Sheet

The sample sheet is used to specify the various information and parameters for each sample.

The required column headers for the Sample Sheet are:
 * 'Sample_ID'          - the sample identifier (unique)
 * 'Sample_Name'        - the sample name
 * 'Index'              - the index bases for the i7 read

Optional column headers for the Sample Sheet are:
 * 'Library_ID'         - the identifier for the library (will be used for the LB tag in the BAM's read group). If
                          not present, then the 'Sample_Id' will be used.
 * 'Sample_Project'     - the project for the given sample (will be added to the DS tag in the BAM's read group).
 * 'Description'        - the description of the sample (will be added to the DS tag in the BAM's read group).

For dual-indexed runs, please add the following column header:
 * 'Index2'             - the index bases for the i5 read

## Read Structure

Read structures are made up of `<number><operator>` pairs much like the CIGAR string in BAM files. Four kinds of
operators are recognized:

1. `T` identifies a template read
2. `B` identifies a sample barcode read
3. `M` identifies a unique molecular index read
4. `S` identifies a set of bases that should be skipped or ignored

The last `<number><operator>` pair may be specified using a `+` sign instead of number to denote "all remaining
bases".  For more information on read structures, see the
[Read Structure Wiki Page](https://github.com/fulcrumgenomics/fgbio/wiki/Read-Structures)

## Outputs

A BAM file will be produced per-sample and placed in the output directory with name `<sample-name>.<library-id>.bam`.
NB: if the library identifier column is not found in the sample sheet, it will default to the sample identifier, so
in this case the output BAM will be named `<sample-name>.<sample-id>`.

Basecalling metrics will be found in the `basecalling` sub-directory of the output directory:
 * `<flowcell-barcode>.illumina_lane_metrics` : stores cluster density metrics.
 * `<flowcell-barcode>.illumina_phasing_metrics` : stores illumina basecaling phasing metrics.
 * `basecalling.lane-<lane>.metrics.txt` : stores the per-lane metrics for sample barcode demultiplexing.

The yield of raw reads will be found in the output directory for each sample:
 * `<sample-name>.<library-id>.quality_yield_metrics.txt`.

For more information about metrics produced by Picard, see the
[Picard Metric Definitions Page](https://broadinstitute.github.io/picard/picard-metric-definitions.html)

## Arguments

|Name|Flag|Type|Description|Required?|Max Values|Default Value(s)|
|----|----|----|-----------|---------|----------|----------------|
|run-folder|i|DirPath|The input run directory.|Required|1||
|sample-sheet|s|FilePath|The Illumina Experiment Manager Sample Sheet.|Required|1||
|output|o|DirPath|Output directory.|Required|1||
|tmp|t|DirPath|Path to a temporary directory.  Use output if none is given.|Optional|1||
|read-structure|r|ReadStructure|The read structure to use.|Optional|1||
|max-threads-per-illumina-basecalls-to-sam||Int||Optional|1|16|

