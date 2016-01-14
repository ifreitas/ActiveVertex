package models
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.SpanSugar

// I do not use *ParallelTestExecution* because it is breaking the order of the spec output.
abstract class BaseSpec extends WordSpec with MustMatchers with ScalaFutures with SpanSugar with BeforeAndAfterAll{
	implicit val defaultPatience = PatienceConfig(timeout = 10 seconds)
}
