import sbt._

object Dependencies {

  val akkaVersion = "2.6.13"
  val akkaHttpVersion = "10.2.4"
  val circeVersion = "0.13.0"
  val kamonVersion = "2.1.13"
  val graalVersion = "21.0.0.2"

  val logging = Seq(
    "com.typesafe.scala-logging" %% "scala-logging"   % "3.9.3",
    "ch.qos.logback"              % "logback-classic" % "1.2.3",
    "ch.qos.logback"              % "logback-core"    % "1.2.3",
    "org.slf4j"                   % "slf4j-api"       % "1.7.30"
  )

  val test = Seq(
    "org.scalatest"      %% "scalatest"         % "3.2.6"   % "test",
    "org.scalactic"      %% "scalactic"         % "3.2.6"   % "test",
    "org.scalacheck"     %% "scalacheck"        % "1.15.3"  % "test",
    "org.scalatestplus"  %% "scalacheck-1-14"   % "3.2.2.0" % "test",
    "com.spotify"         % "docker-client"     % "8.16.0"  % "test",
    "org.asynchttpclient" % "async-http-client" % "2.12.3"  % "test",
    "org.scalamock"      %% "scalamock"         % "5.1.0"   % "test"
  )

  val it = Seq(
    "org.scalatest"     %% "scalatest"           % "3.2.6"         % "it",
    "com.spotify"        % "docker-client"       % "8.16.0"        % "it",
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion     % "it",
    "com.typesafe.akka" %% "akka-http-testkit"   % akkaHttpVersion % "it"
  )

  val akka = Seq(
    "com.typesafe.akka" %% "akka-actor"          % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster"        % akkaVersion,
    "com.typesafe.akka" %% "akka-stream"         % akkaVersion,
    "com.typesafe.akka" %% "akka-http"           % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-core"      % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-remote"         % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"          % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion     % Test,
    "com.typesafe.akka" %% "akka-http-testkit"   % akkaHttpVersion % Test
  )

  val network = Seq(
    "org.bitlet"  % "weupnp"      % "0.1.4",
    "commons-net" % "commons-net" % "3.8.0"
  )

  val json = Seq(
    "io.circe" %% "circe-core"    % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser"  % circeVersion,
    "io.circe" %% "circe-literal" % circeVersion,
    "io.circe" %% "circe-optics"  % circeVersion
  )

  val akkaCirceDependencies = Seq(
    "de.heikoseeberger" %% "akka-http-circe" % "1.36.0"
  )

  val crypto = Seq(
    "org.scorexfoundation" %% "scrypto"         % "2.1.10",
    "org.bouncycastle"      % "bcprov-jdk15on"  % "1.68",
    "org.whispersystems"    % "curve25519-java" % "0.5.0"
  )

  val misc = Seq(
    "com.chuusai"            %% "shapeless"               % "2.3.3",
    "com.iheart"             %% "ficus"                   % "1.5.0",
    "org.rudogma"            %% "supertagged"             % "1.5",
    "com.lihaoyi"            %% "mainargs"                % "0.2.1",
    "org.scalanlp"           %% "breeze"                  % "1.1",
    "io.netty"                % "netty"                   % "3.10.6.Final",
    "com.google.guava"        % "guava"                   % "30.1.1-jre",
    "com.typesafe"            % "config"                  % "1.4.1",
    "com.github.pureconfig"  %% "pureconfig"              % "0.14.1",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.3"
  )

  val monitoring = Seq(
    "io.kamon" %% "kamon-bundle"   % kamonVersion,
    "io.kamon" %% "kamon-core"     % kamonVersion,
    "io.kamon" %% "kamon-influxdb" % kamonVersion,
    "io.kamon" %% "kamon-zipkin"   % kamonVersion
  )

  val graal = Seq(
    // https://mvnrepository.com/artifact/org.graalvm.sdk/graal-sdk
    // https://mvnrepository.com/artifact/org.graalvm.js/js
    // https://mvnrepository.com/artifact/org.graalvm.truffle/truffle-api
    "org.graalvm.sdk"     % "graal-sdk"   % graalVersion,
    "org.graalvm.js"      % "js"          % graalVersion,
    "org.graalvm.truffle" % "truffle-api" % graalVersion
  )

  val node: Seq[ModuleID] =
    logging ++
    test ++
    it ++
    akka ++
    network ++
    json ++
    crypto ++
    misc ++
    monitoring ++
    graal

  lazy val common: Seq[ModuleID] =
    akka ++
    logging ++
    json ++
    crypto

  lazy val chainProgram: Seq[ModuleID] =
    json ++
    test ++
    graal

  lazy val brambl: Seq[ModuleID] =
    akka ++
    akkaCirceDependencies ++
    test

  lazy val akkaHttpRpc: Seq[ModuleID] =
    json ++
    akka ++
    akkaCirceDependencies ++
    test

  lazy val toplRpc: Seq[ModuleID] =
    json ++
    akka ++
    akkaCirceDependencies ++
    test

  lazy val gjallarhorn: Seq[ModuleID] =
    akka ++
    test ++
    crypto ++
    json ++
    logging ++
    misc ++
    it

  lazy val benchmarking: Seq[ModuleID] = Seq()

}
