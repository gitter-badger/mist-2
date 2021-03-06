package io.hydrosphere.mist.job

import java.io.File

import io.hydrosphere.mist.core.CommonData.Action
import io.hydrosphere.mist.core.jvmjob.{JobInfoData, JobsLoader}
import mist.api.args.{InternalArgument, UserInputArgument}
import mist.api.internal.{BaseJobInstance, JavaJobInstance, JobInstance}
import org.apache.commons.io.FilenameUtils
import org.apache.spark.util.SparkClassLoader

import scala.util.{Success, Try}

case class JobInfo(
  instance: BaseJobInstance = JobInstance.NoOpInstance,
  data: JobInfoData
)

trait JobInfoExtractor {
  def extractInfo(file: File, className: String): Try[JobInfo]
}

class JvmJobInfoExtractor(jobsLoader: File => JobsLoader) extends JobInfoExtractor {

  override def extractInfo(file: File, className: String): Try[JobInfo] = {
    val executeJobInstance = extractInstance(file, className, Action.Execute)
    executeJobInstance orElse extractInstance(file, className, Action.Serve) map { instance =>
      val lang = instance match {
        case _: JavaJobInstance => JobInfoData.JavaLang
        case _ => JobInfoData.ScalaLang
      }
      JobInfo(instance, JobInfoData(
        lang = lang,
        execute = instance.describe().collect { case x: UserInputArgument => x },
        isServe = !executeJobInstance.isSuccess,
        className = className,
        tags = instance.describe()
          .collect { case InternalArgument(t) => t }
          .flatten
      ))
    }
  }

  private def extractInstance(file: File, className: String, action: Action): Try[BaseJobInstance] = {
    jobsLoader(file).loadJobInstance(className, action)
  }

}

object JvmJobInfoExtractor {

  def apply(): JvmJobInfoExtractor = new JvmJobInfoExtractor(file => {
    val loader = prepareClassloader(file)
    new JobsLoader(loader)
  })

  private def prepareClassloader(file: File): ClassLoader = {
    val existing = this.getClass.getClassLoader
    val url = file.toURI.toURL
    val patched = SparkClassLoader.withURLs(existing, url)
    Thread.currentThread().setContextClassLoader(patched)
    patched
  }
}

class PythonJobInfoExtractor extends JobInfoExtractor {
  override def extractInfo(file: File, className: String) = Success(
    JobInfo(data = JobInfoData(
      lang = JobInfoData.PythonLang,
      className = className
    )))
}

class BaseJobInfoExtractor(
  jvmJobInfoExtractor: JvmJobInfoExtractor,
  pythonJobInfoExtractor: PythonJobInfoExtractor
) extends JobInfoExtractor {

  override def extractInfo(file: File, className: String): Try[JobInfo] =
    selectExtractor(file)
      .extractInfo(file, className)

  private def selectExtractor(file: File): JobInfoExtractor =
    FilenameUtils.getExtension(file.getAbsolutePath) match {
      case "py" => pythonJobInfoExtractor
      case "jar" => jvmJobInfoExtractor
    }
}

object JobInfoExtractor {
  def apply(): BaseJobInfoExtractor =
    new BaseJobInfoExtractor(JvmJobInfoExtractor(), new PythonJobInfoExtractor)
}