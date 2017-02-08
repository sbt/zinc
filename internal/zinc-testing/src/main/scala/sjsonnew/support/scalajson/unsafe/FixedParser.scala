/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sjsonnew
package support.scalajson.unsafe

import scala.json.ast.unsafe._
import scala.collection.mutable
import jawn.{ SupportParser, MutableFacade }

object FixedParser extends SupportParser[JValue] {
  implicit val facade: MutableFacade[JValue] =
    new MutableFacade[JValue] {
      def jnull() = JNull
      def jfalse() = JFalse
      def jtrue() = JTrue
      def jnum(s: String) = JNumber(s)
      def jint(s: String) = JNumber(s)
      def jstring(s: String) = JString(s)
      def jarray(vs: mutable.ArrayBuffer[JValue]) = JArray(vs.toArray)
      def jobject(vs: mutable.Map[String, JValue]) = {
        val array = new Array[JField](vs.size)
        var i = 0
        vs.foreach {
          case (key, value) =>
            array(i) = JField(key, value)
            i += 1
        }
        JObject(array)
      }
    }
}