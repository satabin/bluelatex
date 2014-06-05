package blue

import sbt._
import Keys._
import aQute.bnd.osgi._

import java.io.File

import sbtbuildinfo.Plugin._

import com.typesafe.sbt.web.SbtWeb

import com.typesafe.sbt.web.SbtWeb.autoImport._

import com.typesafe.sbt.less.SbtLess.autoImport._

object BlueBuild extends BlueBuild

class BlueBuild extends Build with Pack with Server with Distrib with Tests {

  val blueVersion = "1.0.2"

  lazy val bluelatex = (Project(id = "bluelatex",
    base = file(".")) settings (
      resolvers in ThisBuild += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
      resolvers in ThisBuild += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
      organization in ThisBuild := "org.gnieh",
      name := "bluelatex",
      version in ThisBuild := blueVersion,
      scalaVersion in ThisBuild := "2.10.4",
      compileOptions,
      // fork jvm when running
      fork in run := true)
    settings(packSettings: _*)
    settings(blueServerSettings: _*)
  ) aggregate(blueCommon, blueCore, blueCompile, blueSync, blueWeb)

  lazy val compileOptions = scalacOptions in ThisBuild ++=
      Seq("-deprecation", "-feature")

  lazy val blueCommon =
    (Project(id = "blue-common", base = file("blue-common"))
      settings (
        libraryDependencies ++= commonDeps
      )
      settings(buildInfoSettings: _*)
      settings(
        sourceGenerators in Compile <+= buildInfo,
        buildInfoKeys := Seq[BuildInfoKey](
          version,
          scalaVersion,
          BuildInfoKey.action("buildTime") {
            System.currentTimeMillis
          }
        ),
        buildInfoPackage := "gnieh.blue",
        buildInfoObject := "BlueInfo"
      )
    )

  lazy val blueCore =
    (Project(id = "blue-core", base = file("blue-core"))
      settings(
        libraryDependencies ++= commonDeps
      )
    ) dependsOn(blueCommon)

  lazy val commonDeps = Seq(
    "io.spray" % "spray-routing" % "1.3.1",
    "net.tanesha.recaptcha4j" % "recaptcha4j" % "0.0.7",
    "org.apache.pdfbox" % "pdfbox" % "1.8.4" exclude("commons-logging", "commons-logging"),
    "commons-beanutils" % "commons-beanutils" % "1.8.3" exclude("commons-logging", "commons-logging"),
    "commons-collections" % "commons-collections" % "3.2.1",
    "org.fusesource.scalate" %% "scalate-core" % "1.6.1",
    "com.typesafe.akka" %% "akka-osgi" % "2.3.5",
    "org.gnieh" %% "sohva-client" % "1.0.0",
    "org.gnieh" %% "sohva-entities" % "1.0.0",
    "org.gnieh" %% "diffson" % "0.3",
    "org.gnieh" %% "spray-session" % "0.1.0-SNAPSHOT",
    "javax.mail" % "mail" % "1.4.7",
    "ch.qos.logback" % "logback-classic" % "1.0.13",
    "org.slf4j" % "jcl-over-slf4j" % "1.7.5",
    "com.jsuereth" %% "scala-arm" % "1.3",
    "org.osgi" % "org.osgi.core" % "4.3.0" % "provided",
    "org.osgi" % "org.osgi.compendium" % "4.3.0" % "provided",
    "com.typesafe" % "config" % "1.0.2",
    "org.scalatest" %% "scalatest" % "2.2.0" % "test",
    "com.typesafe.akka" %% "akka-testkit" % "2.3.5" % "test"
  )

  lazy val blueMobwrite =
    (Project(id = "blue-mobwrite",
      base = file("blue-mobwrite"))
      settings (
        libraryDependencies ++= commonDeps
      )
    ) dependsOn(blueCommon)

  lazy val blueCompile =
    (Project(id = "blue-compile",
      base = file("blue-compile"))
      settings (
        libraryDependencies ++= commonDeps
      )
    ) dependsOn(blueCommon)

  lazy val blueSync =
    (Project(id = "blue-sync",
      base = file("blue-sync"))
      settings (
        libraryDependencies ++= commonDeps
      )
    ) dependsOn(blueCommon)

  lazy val blueWeb =
  (Project(id = "blue-web",
    base = file("blue-web")) 
    enablePlugins(SbtWeb)
    settings (
      libraryDependencies ++= commonDeps,
      resourceGenerators in Compile += LessKeys.less.taskValue,
      includeFilter in (Assets, LessKeys.less) := "css.less",
      (LessKeys.compress in (Compile, LessKeys.less)) := true,
    (mappings in (Compile, packageBin)) ~= { _ map {
       case (file, path) => (file, path.replaceAll("^css.css", "webapp/css/css.css"))
     }}
    )
  ) dependsOn(blueCommon)
}
