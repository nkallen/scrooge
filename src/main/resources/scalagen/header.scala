/**
 * Autogenerated by Scrooge
 * Edit this shit, I dare you.
 */

package {{scalaNamespace}}

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.Arrays
import scala.collection.{Map, Set}
import scala.collection.mutable
import com.twitter.conversions.time._
import com.twitter.scrooge.{ThriftStruct, ThriftStructCodec}
import com.twitter.util.Future
import org.apache.thrift.TApplicationException
import org.apache.thrift.TEnum
import org.apache.thrift.protocol._
import org.apache.thrift.transport.{TMemoryInputTransport, TMemoryBuffer}
{{#imports}}
import {{namespace}}._
{{/imports}}
