package models

import org.scalatest._
import org.scalatest.BeforeAndAfterAll
import com.thinkaurelius.titan.core.Cardinality

class Specs extends Spec with BeforeAndAfterAll{
  
  override def nestedSuites = Vector(new GraphDbSpec, new ActiveRecordSpec)
  
  override def beforeAll{ 
    GraphDb.init()
    GraphDb.createSchema { schema =>
      schema.makeVertexLabel("person").make
      schema.makePropertyKey("name").cardinality(Cardinality.SINGLE).dataType(classOf[String]).make
    }
  }
	override def afterAll {
	  super.afterAll
	}
}
