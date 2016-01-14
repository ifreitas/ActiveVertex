package models

trait Validators {
  protected def validate[PropertyType](propertyName:String, value: PropertyType,  validators: ((String, PropertyType) => Unit)*){ doValidateProperty(propertyName, value, validators) }
  protected def oneOf[FieldType <: Any](values:Set[FieldType])(name:String, value:FieldType){check(name, values contains value,"validation.notrecognizedoption")}
  protected def notNull             (name:String, value:String){check(name, value != null,             "validation.notnull")}
  protected def notEmpty            (name:String, value:String){check(name, !value.isEmpty(),          "validation.notempty")}
  protected def required            (name:String, value:String){check(name, value != null && !value.trim.isEmpty, "validation.required")}
  protected def length(l:Int)       (name:String, value:String){check(name, value.trim.length == l,    "validation.length")}
  protected def minLength(min:Int)  (name:String, value:String){check(name, value.trim.length >= min,  "validation.minLength")}
  protected def maxLength(max:Int)  (name:String, value:String){check(name, value.trim.length <= max,  "validation.maxLength")}
  protected def emailFormat         (name:String, value:String){check(name, isValidateEmail(value),    "validation.email")}
  protected def minValue(min:Int)   (name:String, value:Int)   {check(name, value >= min,              "validation.minValue")}
  protected def maxValue(max:Int)   (name:String, value:Int)   {check(name, value <= max,              "validation.maxValue")}
  protected def minValue(min:Double)(name:String, value:Double){check(name, value >= min,              "validation.minValue")}
  protected def maxValue(max:Double)(name:String, value:Double){check(name, value <= max,              "validation.maxValue")}
  protected def minValue(min:Long)  (name:String, value:Long)  {check(name, value >= min,              "validation.minValue")}
  protected def maxValue(max:Long)  (name:String, value:Long)  {check(name, value <= max,              "validation.maxValue")}
  
  private def doValidateProperty[FieldType](propertyName:String, value: FieldType,  validators: Seq[((String, FieldType) => Unit)]){
	  validators.foreach{_(propertyName, value)}
  }
  private def check(propertyName:String, validCondition:Boolean, errorKey:String) {
    if(!validCondition) throw new ValidationException(propertyName, errorKey)
  }
  private def isValidateEmail(aEmail:String): Boolean = {
    val emailRegex = """^[-0-9a-zA-Z.+_]+@[-0-9a-zA-Z.+_]+\.[a-zA-Z]{2,4}$""".r
    emailRegex.findFirstIn(aEmail).isDefined
  }
}
class ValidationException(val propertyName:String, val errorKey:String) extends Exception(s"Field '$propertyName': $errorKey")
