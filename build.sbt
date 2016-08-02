
import de.heikoseeberger.sbtheader.HeaderPattern

name := "stinkly"

organization in ThisBuild := "gov.navy"
scalaVersion in ThisBuild := "2.11.8"

lazy val stinkly = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "stinkly",
    buildInfoKeys += BuildInfoKey.action("gitDescribe") {
      Process("git describe --always").lines.head
    }
  )

resolvers += Resolver.jcenterRepo
libraryDependencies ++= Seq(
  "org.scala-lang.modules"         %% "scala-java8-compat"      % "0.7.0",
  "org.specs2"                     %% "specs2-core"             % "3.8.4"     %   "test"
)

cancelable in Global := true

javaVersionPrefix in javaVersionCheck := Some("1.8")

scalacOptions ++= Seq(
  "-deprecation",                  // Emit warning and location for usages of deprecated APIs
  "-encoding", "UTF-8",            // Specify character encoding used by source files
  "-feature",                      // Warn of usages of features that should be imported explicitly
  "-language:existentials",        // Existential types (besides wildcard types) can be written and inferred
  "-language:implicitConversions", // Allow definition of implicit functions called views
  "-language:higherKinds",         // Allow higher-kinded types
  "-target:jvm-1.8",               // Target platform for object files
  "-unchecked",                    // Enable additional warnings where generated code depends on assumptions
  "-Xfatal-warnings",              // Fail the compilation if there are any warnings
  "-Xfuture",                      // Turn on future language features
  "-Xlint",                        // recommended additional warnings
  "-Ybackend:GenBCode",            // New backend and optimizer
  "-Ydelambdafy:method",           // Speed up anonymous function compilation
  "-Ywarn-adapted-args",           // Warn if an argument list is modified to match the receiver
  "-Ywarn-dead-code",              // Warn when dead code is identified
  "-Ypatmat-exhaust-depth", "off", // Unlimited pattern match analysis depth
  "-Ywarn-inaccessible",           // Warn about inaccessible types in method signatures
  "-Ywarn-numeric-widen",          // Warn when numerics are widened
  "-Ywarn-unused-import"           // Warn when imports are unused -- enabling this makes the REPL unusable
  // "-Ywarn-value-discard"        // Warn when non-Unit expression results are unused
)

scalacOptions in (Compile, console) ~= (_ filterNot (_ == "-Ywarn-unused-import"))
scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value

fork in run := true

jarName in assembly := {
  name.value + "-" + version.value + ".jar"
}

coverageExcludedPackages := ".*BuildInfo.*"

scalafmtConfig := Some(file(".scalafmt"))
reformatOnCompileSettings
