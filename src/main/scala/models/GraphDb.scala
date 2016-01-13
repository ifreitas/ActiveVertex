package framework.db

import com.thinkaurelius.titan.core.TitanGraph
import com.thinkaurelius.titan.core.TitanFactory
import com.thinkaurelius.titan.core.TitanTransaction
import org.apache.commons.configuration.BaseConfiguration
import gremlin.scala._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import com.thinkaurelius.titan.core.schema.TitanManagement
import scala.util.Failure

/**
 * @author Israel Freitas
 */
object GraphDb {
  private var titan: TitanGraph = _;
//  private var gremlin: ScalaGraph[TitanGraph] = _;
  
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
  
//  private def getGremlinConnection = {
//    if(gremlin == null || titan.isClosed()){
//      gremlin = getConnection.asScala
//    }
//    gremlin
//  }
  
  def init(){
	  getConnection
	  println("Titan's connection opened.")
	  ()
  }
  
  def shutdown(){
    println("closing Titan's connection...")
    getConnection.close
    titan = null
//    gremlin = null
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
  def transaction[T](block: TitanTransaction => Future[T])(implicit ec:ExecutionContext): Future[T] = {
	  for {
	    transactionalGraph <- Future{getConnection.newTransaction}
	    operation          <- block(transactionalGraph).andThen{
                      	      case Success(r) => transactionalGraph.commit
                      	      case Failure(e) => transactionalGraph.rollback
	                          }
	  }yield{operation}
  }

}
