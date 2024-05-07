sys.props.get("plugin.version") match {
  case Some(version) => addSbtPlugin("io.paymenthighway.sbt" % "sbt-cxf" % version)
  case _ => throw new RuntimeException("The system property 'plugin.version' is not defined. Specify this property using the scriptedLaunchOpts -D.")
}
