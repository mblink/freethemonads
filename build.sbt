scalaVersion := "2.11.8"

lazy val commonSettings = Seq(
  version := "0.1.0",
  scalaVersion := "2.11.8",
  organization := "bondlink",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8", // yes, this is 2 args
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-infer-any",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture"),
  addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1"))

lazy val core = project.in(file("core")).
  settings(commonSettings: _*).
  settings(
    name := "freethemonads",
    libraryDependencies ++= Seq(
      "org.scalaz" %% "scalaz-core" % "7.2.4",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided"),
    publish := {},
    publishLocal := {},
    bintrayOrganization := Some("bondlink"),
    bintrayReleaseOnPublish in ThisBuild := false,
    bintrayRepository := "freethemonads",
    publishArtifact in Test := true)

lazy val example = project.in(file("example")).
  dependsOn(core).
  settings(commonSettings: _*).
  settings(name := "freethemonads-example")
