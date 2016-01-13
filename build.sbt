name := """MyActiveRecord"""

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.6"

val gremlinScalaV = "3.0.2-incubating.1"

val titanV = "1.0.0"

val scalatestV = "2.2.1"

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

resolvers += Resolver.mavenLocal

resolvers += ("Atlassian Releases" at "https://maven.atlassian.com/public/")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(
  "org.apache.tinkerpop" % "gremlin-core" % "3.0.1-incubating" withSources,
  "com.thinkaurelius.titan" % "titan-core" % titanV withSources,
  "com.michaelpollmeier" %% "gremlin-scala" % gremlinScalaV withSources,
  "org.scalatest" % "scalatest_2.11" % "2.2.6" % "test",
  "org.mockito" % "mockito-core" % "2.0.26-beta" % "test"
)

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint",
  "-Ywarn-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-inaccessible",
  "-Ywarn-nullary-override",
  "-Ywarn-value-discard",
  "-language:reflectiveCalls"
)

// Compile the project before generating Eclipse files, so that generated .scala or .class files for views and routes are present
//EclipseKeys.preTasks := Seq(compile in Compile)
EclipseKeys.withSource := true
