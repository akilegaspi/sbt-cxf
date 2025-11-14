name := "greeter"

scalaVersion := "2.11.8"

version := "1.0"

enablePlugins(CxfPlugin)

val CxfVersion = "4.0.4"

CXF / version := CxfVersion

cxfDefaultArgs := Seq("-exsh", "true", "-validate")

cxfWSDLs := Seq(Wsdl("HelloWorld", (resourceDirectory in Compile).value / "wsdl/HelloWorld.wsdl", Nil))
