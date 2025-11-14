scriptedLaunchOpts ++= Seq(
  "-Xmx1024M",
  s"-Dplugin.version=${(ThisBuild / version).value}"
)
