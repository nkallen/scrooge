package com.twitter.scrooge
package scalagen

import AST._
import org.monkey.mustache.Dictionary

trait ServiceTemplate extends Generator with ScalaTemplate { self: ScalaGenerator =>
  def toDictionary(function: Function, async: Boolean): Dictionary = {
    val throwsDictionaries = function.throws map { t =>
      Dictionary().data("scalaType", scalaType(t.`type`))
    }
    val baseReturnType = scalaType(function.`type`)
    val returnType = if (async) "Future[" + baseReturnType + "]" else baseReturnType
    Dictionary()
      .dictionaries("throws", throwsDictionaries)
      .data("name", function.localName)
      .data("scalaType", returnType)
      .data("fieldArgs", fieldArgs(function.args))
  }

  lazy val functionTemplate = handlebar[Function]("function")(toDictionary(_, false))
  lazy val futureFunctionTemplate = handlebar[Function]("function")(toDictionary(_, true))

  def serviceFunctionArgsStruct(f: Function): Struct = {
    Struct(f.localName + "_args", f.args)
  }

  def serviceFunctionResultStruct(f: Function): Struct = {
    val throws = f.throws map { _.copy(requiredness = Requiredness.Optional) }
    val success = f.`type` match {
      case Void => Nil
      case fieldType: FieldType =>
        Seq(Field(0, "success", fieldType, None, Requiredness.Optional))
    }
    Struct(f.localName + "_result", success ++ throws)
  }

  lazy val finagleClientFunctionTemplate = handlebar[Function]("finagleClientFunction") { self =>
    val resultUnwrapper = {
      val exceptions = if (self.throws.isEmpty) "" else
         self.throws.map { "result." + _.name } mkString("(", " orElse ", ").map(Future.exception) getOrElse")
      val result = if (self.`type` eq AST.Void) " Future.Done" else
         "{ result.success.map(Future.value) getOrElse missingResult(\""+self.name+"\") }"
      exceptions + result
    }
    Dictionary()
      .data("functionDecl", futureFunctionTemplate(self))
      .data("name", self.name)
      .data("localName", self.localName)
      .data("argNames", self.args.map(_.name).mkString(", "))
      .data("resultUnwrapper", resultUnwrapper)
  }

  lazy val finagleClientTemplate = handlebar[Service]("finagleClient") { self =>
    val functionDictionaries = self.functions map { f =>
      Dictionary().data("function", finagleClientFunctionTemplate(f).indent())
    }
    val parentName = self.parent.flatMap(_.service).map(_.name)
    Dictionary()
      .data("override", if (parentName.nonEmpty) "override " else "")
      .data("extends", parentName.map { _ + ".FinagledClient(service, protocolFactory)" }.getOrElse("FinagleThriftClient"))
      .dictionaries("functions", functionDictionaries)
  }

  lazy val finagleServiceFunctionTemplate = handlebar[Function]("finagleServiceFunction") { self =>
    val exceptionDictionaries = self.throws map { t =>
      Dictionary()
        .data("exceptionType", scalaType(t.`type`))
        .data("fieldName", t.name)
    }
    Dictionary()
      .data("name", self.name)
      .data("localName", self.localName)
      .data("argNames", self.args map { "args." + _.name } mkString(", "))
      .data("scalaType", scalaType(self.`type`))
      .data("resultNamedArg", if (self.`type` ne Void) "success = Some(value)" else "")
      .dictionaries("exceptions", exceptionDictionaries)
  }

  lazy val finagleServiceTemplate = handlebar[Service]("finagleService"){ self =>
    val functionDictionaries = self.functions map { f =>
      Dictionary()
        .data("function", finagleServiceFunctionTemplate(f).indent())
    }
    val parentName = self.parent.flatMap(_.service).map(_.name)
    Dictionary()
      .data("override", if (parentName.nonEmpty) "override " else "")
      .data("extends", parentName.map { _ + ".FinagledService(iface, protocolFactory)" }.getOrElse("FinagleThriftService"))
      .dictionaries("functions", functionDictionaries)
  }

  lazy val ostrichServiceTemplate = handlebar[Service]("ostrichService") { _ => Dictionary() }

  lazy val serviceTemplate = handlebar[ScalaService]("service") { self =>
    val service = self.service
    val syncFunctions = service.functions.map(functionTemplate(_).indent(2)).mkString("\n")
    val asyncFunctions = service.functions.map(futureFunctionTemplate(_).indent(2)).mkString("\n")
    val functionStructs = service.functions flatMap { f =>
      Seq(serviceFunctionArgsStruct(f), serviceFunctionResultStruct(f))
    } map { structTemplate(_).indent } mkString("", "\n\n", "\n")
    val parentName = service.parent.flatMap(_.service).map(_.name)
    Dictionary()
      .data("name", service.name)
      .data("syncExtends", parentName.map { "extends " + _ + ".Iface " }.getOrElse(""))
      .data("asyncExtends", parentName.map { "extends " + _ + ".FutureIface " }.getOrElse(""))
      .data("syncFunctions", syncFunctions)
      .data("asyncFunctions", asyncFunctions)
      .data("functionStructs", functionStructs)
      .data("finagleClient",
        if (self.options contains WithFinagleClient) (finagleClientTemplate(service).indent + "\n") else "")
      .data("finagleService",
        if (self.options contains WithFinagleService) (finagleServiceTemplate(service).indent + "\n") else "")
      .data("ostrichServer",
        if (self.options contains WithOstrichServer) (ostrichServiceTemplate(service).indent + "\n") else "")
  }
}
