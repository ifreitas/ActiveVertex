# MyActiveRecord
A proof of concept for building an asynchronous functional ActiveRecord for Apache Tinkerpop Gremlin, using Gremlin-Scala and Titan

## Usage
Define the Model
``` scala
// Mixing with the CompanionActiveRecord[A] trait
object Person extends CompanionActiveRecord[Person]

// Mixing with the ActiveRecord[A] trait
// It supports Option[_]. Thanks Gremlin-Scala team.
case class Person(name:String, age:Int, homePage:Option[String]=None) extends ActiveRecord[Person]{
  override def validate(){ // Several basic validators available
    validate("name", name, required, minLength(2), maxLength(60))
    validate("age", age, minValue(18), maxValue())
  }
}
```
Use it!
``` scala
val futurePerson = GraphDb.transaction { implicit multiThreadedTransactionalGraph => //It supports several concurrent operations within that same transaction.
  Person("John", 35, Some("somesite.com")).create
}
futurePerson.map{person => s"${person.name} successfully saved!"}
```

## Test
``` bash
git clone git@github.com:ifreitas/MyActiveRecord.git
cd MyActiveRecord/
sbt
test
```

## License
The MIT License (MIT)

Copyright (c) 2016 Israel Freitas

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
