name := "CELL_FL"

version := "1.0"

scalaVersion := "2.12.10"

val jMetalVersion = "5.9"
val moaVersion = "2019.05.0"
val commonsMathVersion = "3.6.1"
val picoCLIVersion = "4.0.0"
val sparkVersion = "2.4.7"
val akkaVersion = "2.6.10"

mainClass in Compile := Some("MainAkka")

resolvers += "OW2 public" at "https://repository.ow2.org/nexus/content/repositories/public/"
resolvers += "Maven Central repository" at "https://repo1.maven.org/maven2"


libraryDependencies ++= Seq(

  // JMetal
  "org.uma.jmetal" % "jmetal-core" % jMetalVersion exclude("nz.ac.waikato.cms.weka", "weka-stable"),
  "org.uma.jmetal" % "jmetal-algorithm" % jMetalVersion exclude("nz.ac.waikato.cms.weka", "weka-stable"),

  // PicoCLI
  "info.picocli" % "picocli" % picoCLIVersion,

  // MOA
  "nz.ac.waikato.cms.moa" % "moa" % moaVersion,

  // Spark
  "org.apache.spark" %% "spark-core" % sparkVersion,// % "provided",
  "org.apache.spark" %% "spark-sql" % sparkVersion,// % "provided",
  "org.apache.spark" %% "spark-streaming" % sparkVersion,// % "provided",
  "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkVersion,// % "provided",

  // Commons Math
  "org.apache.commons" % "commons-math3" % commonsMathVersion,

  // Akka
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,

  // Scala test
  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "org.scalactic" %% "scalactic" % "3.2.2"

)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs@_*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

scalacOptions ++= Seq(
  "-encoding", "utf8", // Option and arguments on same line
  //"-Xfatal-warnings",  // New lines for each options
  "-explaintypes",
  "-deprecation",
  "-unchecked",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
  "-opt:l:default"
)

/*assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("org.apache.kafka.**" -> "shadeio.@1").inAll
)*/
