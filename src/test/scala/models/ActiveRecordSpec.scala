package models

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.runtime.universe
import gremlin.scala.Marshallable
import java.util.UUID
import gremlin.scala.{Key,ScalaGraph}
import com.thinkaurelius.titan.core.TitanTransaction
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.scalatest.DoNotDiscover
import scala.util.Success
import scala.util.Failure

object Person extends CompanionActiveRecord[Person]
case class Person(name:String, age:Int, homePage:Option[String]=None) extends ActiveRecord[Person]{
  def validate(){}
}

object PersonWhoCrashsOnBeforeSave extends CompanionActiveRecord[PersonWhoCrashsOnBeforeSave]{
	override def beforeSave (db:ScalaGraph[TitanTransaction]):Future[Unit]=Future.failed(new RuntimeException)
}
case class PersonWhoCrashsOnBeforeSave(name:String, age:Int, homePage:Option[String]=None) extends ActiveRecord[PersonWhoCrashsOnBeforeSave]{
  def validate(){}
}

@DoNotDiscover
class ActiveRecordSpec extends BaseSpec {
  

	"create" must {
    "returns a Future[T]," which {
      "on Success" can {
        "commit " in {
        	val futurePerson = GraphDb.transaction { implicit db =>
          	Person("John", 35, Some("somesite.com")).create
        	}
        	whenReady(futurePerson){ savedPerson => 
        	  val futureOptionalVertex:Future[Option[Vertex]] = GraphDb.transaction { implicit db =>
        	    Future{db.v(savedPerson.id.get)}
        	  }
        	  whenReady(futureOptionalVertex){ optionalVertex =>
        	    optionalVertex must be ('defined)
        	  }
        	}
        }
      }
      "on Failure" can {
        "rollback " in {
          val randomName = UUID.randomUUID.toString
          val nameField = Key[String]("name")
          val futureCrashedPerson = GraphDb.transaction { implicit db =>
          	PersonWhoCrashsOnBeforeSave(randomName, 35, Some("somesite.com")).create
        	}
        	whenReady(futureCrashedPerson.failed){ exeption => 
        	  val futureListWithRandomName = GraphDb.transaction { implicit db =>
        	    Future{db.V.has(nameField, randomName).toList()}
        	  }
        	  whenReady(futureListWithRandomName){ list =>
        	    list must be ('empty)
        	  }
        	}
        }
      }
      "the returned T" must {
        "contains an ID" in {
        	val futurePerson = GraphDb.transaction { implicit db =>
          	Person("John", 35, Some("somesite.com")).create
        	}
        	whenReady(futurePerson){ savedPerson => 
          	savedPerson.id must be (defined)
        	}
        }
      }
    }
  }
  
  "update" must {
    "returns a Future[T]" which {
      "on Success" can{
        "commit" in {val p = pending}
      }
      "on Failure" can{
        "rollback" in {val p = pending}
      }
    }
  }
  
  "delete" must {
    "returns a Future[Unit]" which {
      "on Success" can{
        "commit" in {val p = pending}
      }
      "on Failure" can{
        "rollback" in {val p = pending}
      }
    }
  }
}
