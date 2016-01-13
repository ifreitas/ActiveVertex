package models

import java.util.UUID
import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure
import com.thinkaurelius.titan.core.Cardinality
import com.thinkaurelius.titan.core.TitanTransaction
import gremlin.scala.ScalaGraph
import gremlin.scala.Key
import framework.db.GraphDb

class GraphDbSpec extends BaseSpec {
  
  override def beforeAll{ 
    GraphDb.init 
    GraphDb.createSchema { schema =>
    	schema.makeVertexLabel("person").make()
    	schema.makePropertyKey("name").dataType(classOf[String]).cardinality(Cardinality.SINGLE).make()
    }
  }
  
  "transaction" must {
    "provides a transactional graph," which {
      "can be operated by multiple threads" in {
        // Creates four vertices concurrently within a single transaction.
        val futureBeatles = GraphDb.transaction { transactionalGraph =>
          val futureJohn   = Future { transactionalGraph.addVertex("person", ("name", "John"))  }
          val futurePaul   = Future { transactionalGraph.addVertex("person", ("name", "Paul"))  }
          val futureRingo  = Future { transactionalGraph.addVertex("person", ("name", "Ringo")) }
          val futureGeorge = Future { transactionalGraph.addVertex("person", ("name", "George"))}
          
          for {
          	john   <- futureJohn
            paul   <- futurePaul
            ringo  <- futureRingo
            george <- futureGeorge
          }yield{(
              john.id.toString.toLong, 
              paul.id.toString.toLong, 
              ringo.id.toString.toLong, 
              george.id.toString.toLong
          )}
        }// The transaction is closed and all the operations are commited
        
        // when it finishes the creation, it checks the presence of ids through a new transaction
        whenReady(futureBeatles){ ids =>
          val futureSavedIds = GraphDb.transaction { transactionalGraph =>
            Future{ transactionalGraph.V.toList().map{ _.id.toString.toLong } }
          }
          whenReady(futureSavedIds){ savedIds =>
            savedIds must contain allOf (ids._1, ids._2, ids._3, ids._4)
          }
        }
      }
    }
    
    "returns a Future[T]," which{
      
       "on Success" must {
         "closes the transaction" in {
           var transaction:ScalaGraph[TitanTransaction] = null
           val futureOperation = GraphDb.transaction { transactionalGraph =>
        			transaction = transactionalGraph
        			transaction.graph mustBe 'open
        			Future.successful{"fim"}
            }// commit
            whenReady(futureOperation){ _ => transaction.graph mustBe 'closed }
          }
         
         "commits the changes" in {
           val futureId = GraphDb.transaction { transactionalGraph =>
             Future{transactionalGraph.addVertex().id.toString.toLong}
           }
           whenReady(futureId){ id =>
             val futureSavedIds = GraphDb.transaction { transactionalGraph =>
               Future{transactionalGraph.V.toList.map(_.id)}
             }
             whenReady(futureSavedIds){ savedIds =>
               savedIds must contain (id)
             }
           }
         }
       }
       
       "on Failure" must {
        "closes the transaction"  in {
          var transaction:ScalaGraph[TitanTransaction] = null
          val futureOperation = GraphDb.transaction { transactionalGraph =>
      			transaction = transactionalGraph
      			transaction.graph mustBe 'open
      			Future.failed(new RuntimeException)
          }
          whenReady(futureOperation.failed){ _ => transaction.graph mustBe 'closed }
        }
        "rollbacks the changes" in {
          val randomName = UUID.randomUUID.toString
          val nameField  = Key[String]("name")
          
          // Case 1 - throwing an exception immediately after the vertex creation
          val future = GraphDb.transaction { transactionalGraph =>
            Future{
              val vertex = transactionalGraph.addVertex("person", ("name", randomName))
              throw new RuntimeException
            }
          }
          whenReady(future.failed){ e =>
            val futureListWithRandomName = GraphDb.transaction { transactionalGraph =>
              Future{transactionalGraph.V.has(nameField, randomName).toList}
            }
            whenReady(futureListWithRandomName){ vertexListWithRandomName =>
              vertexListWithRandomName mustBe 'empty
            }
          }
          
          // Case 2 - wait for the completion of the vertex creation, before throws an exception
          val futureCase2 = GraphDb.transaction { transactionalGraph =>
            val futureVertex = Future{
              val vertex = transactionalGraph.addVertex("person", ("name", randomName))
              vertex
            }
            for{ vertex <- futureVertex }
            yield{ throw new RuntimeException }
          }
          whenReady(futureCase2.failed){ e =>
            val futureListWithRandomName = GraphDb.transaction { transactionalGraph =>
              Future{transactionalGraph.V.has(nameField, randomName).toList}
            }
            whenReady(futureListWithRandomName){ listWithRandomName =>
              listWithRandomName mustBe 'empty
            }
          }
          
          // Case 3 - wait for the completion of the vertex creation, before return a failed future
          val futureCase3 = GraphDb.transaction { transactionalGraph =>
            transactionalGraph.addVertex("person", ("name", randomName))
            Thread.sleep(2000) // wait the vertex creation and update
            Future.failed(new RuntimeException)
          }
          whenReady(futureCase3.failed){ e =>
            val futureListWithRandomName = GraphDb.transaction { transactionalGraph =>
              Future{transactionalGraph.V.has(nameField, randomName).toList}
            }
            whenReady(futureListWithRandomName){ listWithRandomName =>
              listWithRandomName mustBe 'empty
            }
          }
        }
      }
    }
  }
}
