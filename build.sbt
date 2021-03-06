import com.typesafe.sbt.SbtGit.GitCommand
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyKeys.assembly
import scoverage.ScoverageKeys._

////////////////////////////////////////////////////////////////////////////////////////////////
// For the aggregate (root) jar, override the name.  For the sub-projects,
// see the build.sbt in each project folder.
////////////////////////////////////////////////////////////////////////////////////////////////
assemblyJarName in assembly := "bfx-examples-all.jar"

////////////////////////////////////////////////////////////////////////////////////////////////
// Coverage settings
////////////////////////////////////////////////////////////////////////////////////////////////
coverageExcludedPackages := "<empty>"
val htmlReportsDirectory: String = "target/test-reports"

////////////////////////////////////////////////////////////////////////////////////////////////
// scaladoc options
////////////////////////////////////////////////////////////////////////////////////////////////
val docScalacOptions = Seq("-groups", "-implicits")

//////////////////////////////////////////////////////////////////////////////////////////////// 
// Common settings for all projects
////////////////////////////////////////////////////////////////////////////////////////////////

lazy val commonSettings = Seq(
  organization         := "com.nilshomer",
  organizationName     := "Nils Homer",
  organizationHomepage := Some(url("http://www.nilshomer.com")),
  homepage             := Some(url("http://github.com/nh13/bfx-examples")),
  startYear            := Some(2017),
  scalaVersion         := "2.12.2",
  scalacOptions        += "-target:jvm-1.8",
  scalacOptions in (Compile, doc) ++= docScalacOptions,
  scalacOptions in (Test, doc) ++= docScalacOptions,
  autoAPIMappings := true,
  testOptions in Test  += Tests.Argument(TestFrameworks.ScalaTest, "-h", Option(System.getenv("TEST_HTML_REPORTS")).getOrElse(htmlReportsDirectory)),
  // uncomment for full stack traces
  // testOptions in Test  += Tests.Argument("-oD"),
  fork in Test         := true,
  resolvers            += Resolver.jcenterRepo,
  resolvers            += Resolver.sonatypeRepo("public"),
  resolvers            += Resolver.mavenLocal,
  shellPrompt          := { state => "%s| %s> ".format(GitCommand.prompt.apply(state), version.value) },
  coverageExcludedPackages := "<empty>",
  updateOptions        := updateOptions.value.withCachedResolution(true)
) ++ Defaults.coreDefaultSettings

////////////////////////////////////////////////////////////////////////////////////////////////
// tools project
////////////////////////////////////////////////////////////////////////////////////////////////
lazy val tools = Project(id="bfx-examples-tools", base=file("tools"))
  .settings(commonSettings: _*)
  .settings(description := "Command line tools for STR duplex sequencing.")
  .settings(
    libraryDependencies ++= Seq(
      "com.fulcrumgenomics" %% "commons"      % "0.2.0",
      "com.fulcrumgenomics" %% "sopt"         % "0.2.0",
      "com.fulcrumgenomics" %% "fgbio"        % "0.2.0",
      //---------- Test libraries -------------------//
      "org.scalatest"        %%  "scalatest"  %  "3.0.1" % "test->*" excludeAll ExclusionRule(organization="org.junit", name="junit")
    )
  )

////////////////////////////////////////////////////////////////////////////////////////////////
// pipeline project
////////////////////////////////////////////////////////////////////////////////////////////////
lazy val htsjdkAndPicardExcludes = Seq(
  ExclusionRule(organization="org.apache.ant"),
  ExclusionRule(organization="gov.nih.nlm.ncbi"),
  ExclusionRule(organization="org.testng"),
  ExclusionRule(organization="com.google.cloud.genomics")
)

lazy val pipelines = Project(id="bfx-examples-pipeline", base=file("pipelines"))
  .settings(description := "dagr pipelines for processing STR duplex sequencing data.")
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "com.fulcrumgenomics" %% "commons"      % "0.2.0",
      "com.fulcrumgenomics" %% "sopt"         % "0.2.0",
      "com.fulcrumgenomics" %% "dagr"         % "0.2.0" excludeAll(htsjdkAndPicardExcludes:_*),
      "com.fulcrumgenomics" %% "fgbio"        % "0.2.0"
    )
  ).dependsOn(tools)

////////////////////////////////////////////////////////////////////////////////////////////////
// root project
////////////////////////////////////////////////////////////////////////////////////////////////
lazy val assemblySettings = Seq(
  test in assembly     := {},
  logLevel in assembly := Level.Info
)

lazy val root = Project(id="bfx-examples", base=file("."))
  .settings(commonSettings: _*)
  .settings(assemblySettings: _*)
  .aggregate(pipelines)
  .dependsOn(pipelines)
  .aggregate(tools, pipelines)
  .dependsOn(tools, pipelines)
