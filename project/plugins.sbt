// Comment to get more information during initialization
logLevel := Level.Warn

// Build & Development
addSbtPlugin("com.typesafe.sbt"   %  "sbt-javaversioncheck"   %  "0.1.0")
addSbtPlugin("com.timushev.sbt"   %  "sbt-updates"            %  "0.1.10")
addSbtPlugin("com.eed3si9n"       %  "sbt-buildinfo"          %  "0.6.1")
addSbtPlugin("com.eed3si9n"       %  "sbt-assembly"           %  "0.14.3")
addSbtPlugin("com.github.gseitz"  %  "sbt-release"            %  "1.0.3")
addSbtPlugin("org.ensime"         %  "sbt-ensime"             %  "1.0.0")
addSbtPlugin("net.virtual-void"   %  "sbt-dependency-graph"   %  "0.8.2")

// Scoverage isn't currently compatible with ScalaJS but a PR is pending.
// See: https://github.com/scoverage/sbt-scoverage/issues/101
addSbtPlugin("org.scoverage"      %  "sbt-scoverage"          %  "1.3.5")

// Code analysis/formatting
addSbtPlugin("org.scalastyle"     %% "scalastyle-sbt-plugin"  %  "0.8.0")
addSbtPlugin("com.geirsson"       %  "sbt-scalafmt"           %  "0.2.12")
addSbtPlugin("de.heikoseeberger"  %  "sbt-header"             %  "1.6.0")
addSbtPlugin("com.scalapenos"     %  "sbt-prompt"             %  "1.0.0")
