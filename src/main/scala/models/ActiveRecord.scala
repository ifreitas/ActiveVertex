package models

import java.util.Date
import scala.reflect.runtime.{universe => ru}
import org.apache.tinkerpop.gremlin.structure.Vertex
import com.thinkaurelius.titan.core.TitanTransaction
import gremlin.scala.Marshallable
import gremlin.scala.ScalaGraph
import gremlin.scala.wrap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.thinkaurelius.titan.core.TitanTransaction

// Thanks to piotrga. From: http://stackoverflow.com/questions/11020746/get-companion-object-instance-with-new-scala-reflection-api
// and https://gist.github.com/piotrga/5928581 
trait ReflectionSugars{
  private lazy val universeMirror = ru.runtimeMirror(getClass.getClassLoader)
  protected def companionOf[T](implicit tt: ru.TypeTag[T])  = {
    val companionMirror = universeMirror.reflectModule(ru.typeOf[T].typeSymbol.companion.asModule)
    companionMirror.instance
  }
}

trait CompanionActiveRecord[T <: Product with ActiveRecord[T]]{
	def findById(id:Any)(implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T], ec:ExecutionContext):Future[Option[T]] = Future{db.v(id).map(fromVertex(_))}
	def apply(id:Any)(implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T], ec:ExecutionContext):Future[Option[T]] = findById(id)
	def apply(vertex:Vertex)(implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T]):T = fromVertex(vertex)
  private def fromVertex(vertex:Vertex)(implicit m:Marshallable[T]):T = {
    val model = vertex.toCC[T]
    model.id = Some(vertex.id.asInstanceOf[Number].longValue())
    model
  }
	// TODO 1: Usar uma Exception mais apropriada
	// TODO 2: Melhorar mensagem
	// TODO 3: Este método deve ser privado no Companion (objeto que estende esta trait)! Para isso a implementação deve ser feita através de macro annotations 
	// (Por enquanto está publico para que possa ser a case class tenha acesso a ela.)
	def findVertex(id:Any)(implicit db:ScalaGraph[TitanTransaction], ec:ExecutionContext) = Future{db.v(id).getOrElse(throw new RuntimeException("Record not found!"))}
	
	def create(model:T) (implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T], typeTag:ru.TypeTag[T], ec:ExecutionContext):Future[T] = {
	  for{
      _ <- beforeSave(db)
      _ <- beforeCreate(db)
      vtx <- doCreate(model)
      _ <- afterCreate(db, vtx)
      _ <- afterSave(db, vtx)
    }yield{
      val newModel = fromVertex(vtx)
		  newModel
    }
	}
	private def doCreate(model: T)(implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T], typeTag:ru.TypeTag[T], ec: ExecutionContext):Future[Vertex] = Future {
	  db.addVertex[T](model)
  }

  /**
   * Diferente do método save, que ocorre após a validação do modelo, o update  poderia
   * gravar no banco um valor inválido uma vez que não passa mais pela validação. Porém
   * este problema é mitigado já que, após alterar o vertex, uma nova instancia do mode
   * lo é construida e então imediatamente validada. Caso seja disparada uma excecao, 
   * ocorre o rollback e a gravação é desfeita.
   */
  def update(id:Long, properties:Map[String, Any])(implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T], typeTag:ru.TypeTag[T], ec: ExecutionContext):Future[T] = {
    for{
      vertex <- findVertex(id) // TODO: Caso nao encontre o vertex: "are you trying to update a delete record?"
      _ <- beforeSave(db)
      propertiesToUse <- beforeUpdate(db, vertex, properties)
      _ <- doUpdate(vertex, propertiesToUse)
      _ <- afterUpdate(db, vertex, propertiesToUse)
      _ <- afterSave(db, vertex)
    }yield{
      fromVertex(vertex) // validação pós-escrita no bd.
    }
  }
  private def doUpdate(vertex:Vertex, properties:Map[String, Any])(implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T], typeTag:ru.TypeTag[T], ec: ExecutionContext):Future[Unit] = Future {
    //TODO certificar que todos os 'key' do 'properties' estão presentes no vertex antes de setá-los neste.
    for((k,v)<-properties){
      v match {
        case None        => vertex.property(k).remove
        case Some(value) => vertex.property(k, value)
        case value       => vertex.property(k, value)
      }
    }
  }
	
  def delete(id:Long)(implicit db:ScalaGraph[TitanTransaction], ec: ExecutionContext):Future[Unit] = {
    for{
      vertex <- findVertex(id) // TODO: Caso nao encontre o vertex: "are you trying to delete an already deleted record?"
    	_ <- beforeDelete(db, vertex)
    	_ <- Future{vertex.remove}
    	_ <- afterDelete(db)
    }yield{()}
  }
	
  // Hooks
  protected def afterSave   (db:ScalaGraph[TitanTransaction], vtx:Vertex):Future[Unit]=Future.successful(())
  protected def afterCreate (db:ScalaGraph[TitanTransaction], vtx:Vertex):Future[Unit]=Future.successful(())
  protected def beforeUpdate(db:ScalaGraph[TitanTransaction], vtx:Vertex, originalProperties:Map[String, Any]):Future[Map[String, Any]]=Future.successful(originalProperties)
  protected def afterUpdate (db:ScalaGraph[TitanTransaction], vtx:Vertex, usedProperties:Map[String, Any]):Future[Unit]=Future.successful(())
  protected def beforeDelete(db:ScalaGraph[TitanTransaction], vtx:Vertex):Future[Unit]=Future.successful(())
  protected def afterDelete (db:ScalaGraph[TitanTransaction]):Future[Unit]=Future.successful(())// Vertex inexistente
  protected def beforeSave  (db:ScalaGraph[TitanTransaction]):Future[Unit]=Future.successful(())// Vertex inexistente
  protected def beforeCreate(db:ScalaGraph[TitanTransaction]):Future[Unit]=Future.successful(())// Vertex inexistente
}


/**
 * A trait ActiveRecord, assim como o paradigma funcional, impõe que modelo seja imutável.
 * Assim, o modelo tem que ser construido com um estado válido, do contrário uma exceção
 * será disparada.
 * 
 * LIMITAÇÕES/TODO'S:
 *   1. Método 'update' não suporta Value Objects.
 *   2. Método 'update' não está verificando se todas as propriedades são válidas (se os 
 *      nomes estão corretos e todos pertencem ao objeto que está sendo atualizado)
 *   3. Não suporta Enums
 */
trait ActiveRecord[T <: Product with ActiveRecord[T]] extends ReflectionSugars with Validators { this: T =>
  
  /**
   * Deve ser implementada nas subclasses de forma que garanta um estado válido.
   */
  protected def validate()
  
  /**
   * Garante que o objeto já seja construido em um estado válido.
   */
  validate()
  
  private var _id:Option[Long] = _
  private[models] def id_=(theId:Option[Long]){if(_id!=null){throw new DeveloperException("It is not allowed to sets the id twice!")}else{this._id = theId}}
  def id = _id
  
  // Companion
  // TODO: Usar Macro Annotations para criar o companion object e sua implementação inicial!
  // TODO: Outro motivo para usar as Macro Annotations é que os métodos privados "implementados" no companion ficam visíveis para o case class
  private[this] var _companion: CompanionActiveRecord[T] = _
  protected[models] def companion(implicit typeTag:ru.TypeTag[T]):CompanionActiveRecord[T] = {
	  if(_companion == null) _companion = companionOf[T].asInstanceOf[CompanionActiveRecord[T]]
			  _companion
  }
  
  def create()(implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T], typeTag:ru.TypeTag[T], ec:ExecutionContext):Future[T] = companion.create(this)
  def update(properties:Map[String, Any])(implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T], typeTag:ru.TypeTag[T], ec: ExecutionContext) = companion.update(id.getOrElse(throw new RuntimeException("Are you trying to update an already deleted record or a detached yet object?")), properties)
  def delete()(implicit db:ScalaGraph[TitanTransaction], m:Marshallable[T], typeTag:ru.TypeTag[T], ec:ExecutionContext):Future[Unit] = companion.delete(this.id.getOrElse(throw new RuntimeException("Are you trying to delete an already deleted record or a detached yet object?")))

}
class DeveloperException(msg:String) extends Exception(msg)