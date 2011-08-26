package com.twitter.scrooge

import scala.collection.mutable
import scopt.OptionParser

object Main {
  var destFolder: String = "."
  val thriftFiles = new mutable.ListBuffer[String]
  val flags = new mutable.HashSet[scalagen.ScalaServiceOption]

  def main(args: Array[String]) {
    val parser = new OptionParser("scrooge") {
      help(None, "help", "show this help screen")
      opt("d", "dest", "write generated code to a folder (default: %s)".format(destFolder), { x =>
        destFolder = x
      })
      opt("finagle", "generate finagle service classes", { flags += scalagen.WithFinagle; () })
      opt("ostrich", "generate ostrich service interface", { flags += scalagen.WithOstrich; () })
      arglist("<files...>", "thrift files to compile", { thriftFiles += _ })
    }
    if (!parser.parse(args)) {
      System.exit(1)
    }

    // for now, just read in a thrift file and dump out generated crap.
    if (args.size < 1) {
      println("usage: scrooge <file>  =>  generate crap")
      exit(1)
    }

    val scrooge = new ScroogeParser(Importer.fileImporter())
    val doc = scrooge.parseFile(args(0))

    val gen = new scalagen.ScalaGenerator()
    println(gen(doc))
  }
}
