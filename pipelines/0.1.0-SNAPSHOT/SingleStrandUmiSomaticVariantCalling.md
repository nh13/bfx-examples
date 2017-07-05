---
title: SingleStrandUmiSomaticVariantCalling
---

# SingleStrandUmiSomaticVariantCalling

## Overview
**Group:** Single Sample Analysis Pipelines

Creates single-UMI consensus reads and calls somatic variants.

Starts with an unmapped BAM file with the UMI in a tag (`RX` by default).

The reads are aligned with bwa-mem, then duplicate marked using the UMI tag, grouped by molecular ID to
create a "grouped" BAM file, and consensus called to produce per-molecule consensus reads.

The consensus reads are aligned with bwa-mem and subsequently filtered.

The de-duplicated BAM and consensus reads are both variant called with VarDictJava and are there run through a
panel of metrics.  This allows us to compare the difference between using the single-UMIs simply for better
duplicate marking, or for improving the quality of reads through consensus calling.

## Arguments

|Name|Flag|Type|Description|Required?|Max Values|Default Value(s)|
|----|----|----|-----------|---------|----------|----------------|
|input-bam|i|DirPath|Path to the unmapped BAM file.|Required|1||
|ref|r|PathToFasta|Path to the reference FASTA.|Required|1||
|intervals|l|PathToIntervals|Regions to analyze.|Required|1||
|truth-vcf|v|PathToVcf|Truth VCF for the sample being sequenced.|Optional|1||
|output|o|PathPrefix|Path prefix for output files.|Required|1||
|umi-tag|U|String|The tag containing the raw UMI.|Optional|1|RX|
|molecular-id-tag|I|String|The tag to store the molecular identifier.|Optional|1|MI|
|min-map-q|m|Int|Minimum mapping quality to include reads.|Optional|1|10|
|strategy|s|String|The UMI assignment strategy; one of 'Identity', 'Edit', 'Adjacency'.|Optional|1|Adjacency|
|edits|e|Int|The allowable number of edits between UMIs.|Optional|1|1|
|error-rate-pre-umi|1|Int|The Phred-scaled error rate for an error prior to the UMIs being integrated.|Optional|1|45|
|error-rate-post-umi|2|Int|The Phred-scaled error rate for an error post the UMIs have been integrated.|Optional|1|30|
|min-input-base-quality|q|Int|The minimum input base quality for bases to go into consensus.|Optional|1|30|
|min-consensus-base-quality|Q|Int|Mask (make 'N') consensus bases with quality less than this threshold.|Optional|1|40|
|min-reads|M|Int|The minimum number of reads to produce a consensus base.|Optional|1|1|
|max-base-error-rate||Double|The maximum consensus error rate per base.|Optional|1|0.1|
|max-read-error-rate||Double|The maximum consensus error rate for per read|Optional|1|0.05|
|max-no-call-fraction||Double|The maximum fraction of bases that are Ns in a consensus read.|Optional|1|0.1|
|minimum-af||Double|The minimum allele frequency to use for variant calling.|Optional|1|0.0025|

