package io.paymenthighway.sbt.cxf

import java.io.File
import java.net.URLClassLoader

import sbt.Keys._
import sbt.io.{FileFilter, SimpleFileFilter}
import sbt.{SettingKey, TaskKey, _}

object CxfPlugin extends AutoPlugin {

  object Import {
    val CXF = config("CXF").hide

    lazy val cxfDefaultArgs = SettingKey[Seq[String]]("wsdl2java-default-arguments")
    lazy val cxfWSDLs = SettingKey[Seq[Wsdl]]("wsdl-list", "WSDLs to generate java files from")
    lazy val cxfExcludeFilter = SettingKey[PartialFunction[String, Boolean] => FileFilter]("cxf-exclude-filter")

    lazy val cxfGenerate = TaskKey[Seq[File]]("run-wsdl-to-java")

    case class Wsdl(key: String, file: File, args: Seq[String])
  }

  override val trigger = noTrigger
  override val requires = sbt.plugins.IvyPlugin && sbt.plugins.JvmPlugin

  val autoImport: Import.type = Import

  import autoImport._

  protected[this] object CxfVersion {
    def unapply(version: String): Option[(Int, Int, Int)] = {
      val Version = """(\d+)\.(\d+)\.(\d+).*""".r
      version match {
        case Version(major, minor, patch) => Some((major.toInt, minor.toInt, patch.toInt))
        case _ => None
      }
    }
  }

  override def projectSettings: Seq[Def.Setting[_]] = baseProjectSettings

  private lazy val baseProjectSettings: Seq[Def.Setting[_]] = Seq(
    ivyConfigurations += CXF,

    libraryDependencies ++= Seq(
      "org.apache.cxf" % "cxf-tools-wsdlto-core" % (CXF / version).value % CXF,
      "org.apache.cxf" % "cxf-tools-wsdlto-databinding-jaxb" % (CXF / version).value % CXF,
      "org.apache.cxf" % "cxf-tools-wsdlto-frontend-jaxws" % (CXF / version).value % CXF,
    ) ++ ((CXF / version).value match {
      case CxfVersion(4, _, _) => Seq(
        "jakarta.jws" % "jakarta.jws-api" % "3.0.0",
        "jakarta.xml.ws" % "jakarta.xml.ws-api" % "4.0.2",
        "jakarta.xml.bind" % "jakarta.xml.bind-api" % "4.0.4",
      )

      case CxfVersion(3, minor, _) if minor > 2 => Seq(
        "jakarta.jws" % "jakarta.jws-api" % "2.1.0",
        "jakarta.xml.ws" % "jakarta.xml.ws-api" % "2.3.3",
        "jakarta.xml.bind" % "jakarta.xml.bind-api" % "2.3.3",
      )
      case _ => throw new IllegalArgumentException("Unsupported CXF version")
    }),

    cxfWSDLs := Nil,
    cxfDefaultArgs := Seq("-exsh", "true", "-validate"),

    // Test resources must be manually defined
    Test / cxfWSDLs := Nil,
    Test / cxfDefaultArgs := Seq("-exsh", "true", "-validate"),

    CXF / managedClasspath := {
      Classpaths.managedJars(CXF, (CXF / classpathTypes).value, update.value)
    },

    CXF / version := "4.1.3"
  ) ++
    inConfig(Compile)(settings) ++
    inConfig(Test)(settings)

  private val settings = Seq(
    cxfGenerate / target := crossTarget.value / "cxf" / Defaults.nameForSrc(configuration.value.name),

    cxfGenerate := Def.taskDyn {
      val s = streams.value

      val basedir = (cxfGenerate / target).value
      val classpath = (CXF / managedClasspath).value.files

      val wsdlFiles = cxfWSDLs.value

      Def.task {
        if (wsdlFiles.nonEmpty && (!basedir.exists() || wsdlFiles.exists(_.file.lastModified() > basedir.lastModified()))) {
          if (basedir.exists()) {
            s.log.info("Removing output directory...")
            IO.delete(basedir)
          }
          IO.createDirectory(basedir)

          val oldContextClassLoader = Thread.currentThread.getContextClassLoader

          val classLoader = new URLClassLoader(Path.toURLs(classpath), oldContextClassLoader)

          val WSDLToJava = classLoader.loadClass("org.apache.cxf.tools.wsdlto.WSDLToJava")
          val ToolContext = classLoader.loadClass("org.apache.cxf.tools.common.ToolContext")

          try {
            Thread.currentThread.setContextClassLoader(classLoader)

            wsdlFiles.flatMap { wsdl =>
              val args = Seq("-d", basedir.getAbsolutePath) ++ cxfDefaultArgs.value ++ wsdl.args :+ wsdl.file.getAbsolutePath
              callWsdl2java(wsdl.key, basedir, args, classpath, s.log)(WSDLToJava, ToolContext)

              (basedir ** "*.java").get
            }.distinct
          } catch {
            case e: Throwable =>
              s.log.error("Failed to compile wsdl with exception: " + e.getMessage)
              s.log.trace(e)

              (basedir ** "*.java").get
          } finally {
            Thread.currentThread.setContextClassLoader(oldContextClassLoader)

            classLoader.close()
          }
        } else {
          (basedir ** "*.java").get
        }
      }
    }.value,
    sourceGenerators += cxfGenerate.taskValue map { files =>
      val filter = (cxfGenerate / excludeFilter).value

      files.filter {
        case file if !filter.accept(file) => true
        case file =>
          IO.delete(file)

          false
      }
    },
    managedSourceDirectories += (cxfGenerate / target).value,

     cxfGenerate / excludeFilter := NothingFilter,

    cxfExcludeFilter := ExcludeFilter((cxfGenerate / target).value.getPath),

    cxfGenerate / clean := IO.delete((cxfGenerate / target).value)
  )

  private def callWsdl2java(key: String, output: File, arguments: Seq[String], classpath: Seq[File], logger: Logger)(
    WSDLToJava: Class[_],
    ToolContext: Class[_]
  ) {
    logger.info("WSDL: key=" + key + ", args=" + arguments.mkString(" "))
    logger.info("Compiling WSDL...")

    val start = System.currentTimeMillis()

    val constructor = WSDLToJava.getConstructor(classOf[Array[String]])
    val run = WSDLToJava.getMethod("run", ToolContext)

    try {
      val instance = constructor.newInstance(arguments.toArray)
      run.invoke(instance, ToolContext.getConstructor().newInstance().asInstanceOf[AnyRef])
    } catch { case e: Throwable =>
      logger.error("Failed to compile wsdl with exception: " + e.getMessage)
      logger.trace(e)
    } finally {
      val end = System.currentTimeMillis()
      logger.info("Compiled WSDL in " + (end - start) + "ms.")
    }
  }

  case class ExcludeFilter(prefix: String)(acceptFunction: PartialFunction[String, Boolean]) extends FileFilter {
    final def accept(file: File): Boolean = {
      acceptFunction.applyOrElse[String, Boolean](
        file.getPath.stripPrefix(prefix).stripPrefix("/"),
        path => false
      )
    }
    override def equals(o: Any): Boolean = o match {
      // Note that anonymous functions often get compiled to a constant value so this equality
      // check may be true more often than one might naively assume given that this is often
      // a reference comparison.
      case that: SimpleFileFilter => this.acceptFunction == that.acceptFunction
      case _                      => false
    }
    override def hashCode(): Int = acceptFunction.hashCode
    override def toString: String = s"ExcludeFilter($acceptFunction)"
  }
}
