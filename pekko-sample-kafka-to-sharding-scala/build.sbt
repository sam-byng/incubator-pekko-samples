val pekkoVersion = "0.0.0+26623-85c2a469-SNAPSHOT"
val pekkoHttpVersion = "0.0.0+4335-81a9800e-SNAPSHOT"

val pekkoConnectorsKafkaVersion = "0.0.0+1717-267012de-SNAPSHOT"
val pekkoManagementVersion = "0.0.0+710-b49055bd-SNAPSHOT"
val EmbeddedKafkaVersion = "2.4.1.1"
val LogbackVersion = "1.2.11"

ThisBuild / scalaVersion := "2.13.10"
ThisBuild / organization := "org.apache.pekko"
ThisBuild / Compile / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlog-reflective-calls",
  "-Xlint")
ThisBuild / Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
ThisBuild / Test / testOptions += Tests.Argument("-oDF")
ThisBuild / licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

// allow access to snapshots
ThisBuild / resolvers += "Apache Nexus Snapshots".at("https://repository.apache.org/content/groups/snapshots/")

Global / cancelable := true // ctrl-c

lazy val `pekko-sample-kafka-to-sharding` = project.in(file(".")).aggregate(producer, processor, client)

lazy val kafka = project
  .in(file("kafka"))
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % LogbackVersion,
      "org.slf4j" % "log4j-over-slf4j" % "1.7.26",
      "io.github.embeddedkafka" %% "embedded-kafka" % EmbeddedKafkaVersion),
    cancelable := false)

lazy val client = project
  .in(file("client"))
  .enablePlugins(PekkoGrpcPlugin, JavaAgent)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
      "org.apache.pekko" %% "pekko-discovery" % pekkoVersion))

lazy val processor = project
  .in(file("processor"))
  .enablePlugins(PekkoGrpcPlugin, JavaAgent)
  .settings(javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test")
  .settings(libraryDependencies ++= Seq(
    "org.apache.pekko" %% "pekko-connectors-kafka" % pekkoConnectorsKafkaVersion,
    "org.apache.pekko" %% "pekko-connectors-kafka-cluster-sharding" % pekkoConnectorsKafkaVersion,
    "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
    "org.apache.pekko" %% "pekko-discovery" % pekkoVersion,
    "org.apache.pekko" %% "pekko-cluster-sharding-typed" % pekkoVersion,
    "org.apache.pekko" %% "pekko-stream-typed" % pekkoVersion,
    "org.apache.pekko" %% "pekko-serialization-jackson" % pekkoVersion,
    "org.apache.pekko" %% "pekko-management" % pekkoManagementVersion,
    "org.apache.pekko" %% "pekko-management-cluster-http" % pekkoManagementVersion,
    "org.apache.pekko" %% "pekko-http-spray-json" % pekkoHttpVersion,
    "ch.qos.logback" % "logback-classic" % LogbackVersion,
    "org.apache.pekko" %% "pekko-actor-testkit-typed" % pekkoVersion % Test,
    "org.scalatest" %% "scalatest" % "3.2.15" % Test))

lazy val producer = project
  .in(file("producer"))
  .settings(Compile / PB.targets := Seq(scalapb.gen() -> (Compile / sourceManaged).value))
  .settings(libraryDependencies ++= Seq(
    "org.apache.pekko" %% "pekko-connectors-kafka" % pekkoConnectorsKafkaVersion,
    "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.11",
    "org.scalatest" %% "scalatest" % "3.2.15" % Test))
