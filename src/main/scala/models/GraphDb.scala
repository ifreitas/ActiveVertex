package models

import org.apache.commons.configuration.BaseConfiguration
import com.thinkaurelius.titan.core.TitanFactory
import com.thinkaurelius.titan.core.TitanGraph
import com.thinkaurelius.titan.core.schema.TitanManagement
import com.thinkaurelius.titan.core.TitanTransaction
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import gremlin.scala._

/**
 * @author Israel Freitas
 */
object GraphDb {
  private var titan: TitanGraph = _;
  
  // TODO: Externalizar a configuração
  private def getTitanConf = {
    val conf = new BaseConfiguration()
    conf.setProperty("storage.backend", "inmemory")
    conf
  }
  
  private def getConnection = {
    if(titan == null || titan.isClosed()){
      titan = TitanFactory.open(getTitanConf)
    }
    titan
  }
  
  def init(){
	  getConnection
	  println("Titan's connection opened.")
	  ()
  }
  
  def shutdown(){
    println("closing Titan's connection...")
    getConnection.close
    titan = null
    println("Titan's connection closed.")
  }
  
  def createSchema(block: TitanManagement => Any) = {
    val schema = getConnection.openManagement()
    block(schema)
    schema.commit()
  }
  
  /**
   * 
   * transaction
   *   must provides a transactional graph, which
   *   - can be operated by multiple threads
   *   must returns a Future[T], which
   *     on Success
   *     - must closes the transaction
   *     - must commits the changes
   *     on Failure
   *     - must closes the transaction
   *     - must rollbacks the changes
   */
  def transaction[T](block: ScalaGraph[TitanTransaction] => Future[T])(implicit ec:ExecutionContext): Future[T] = {
	  for {
	    transactionalGraph <- Future { getConnection.newTransaction.asScala }
	    operation          <- block(transactionalGraph).andThen{
                      	      case Success(r) => transactionalGraph.graph.commit
                      	      case Failure(e) => transactionalGraph.graph.rollback
	                          }
	  }yield{operation}
  }
}
