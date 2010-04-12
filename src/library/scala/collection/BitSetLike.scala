/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2010, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$


package scala.collection

import BitSetLike._
import generic._
import mutable.StringBuilder

/** A template trait for bitsets.
 *  $bitsetinfo
 *
 * This trait provides most of the operations of a `BitSet` independently of its representation.
 * It is inherited by all concrete implementations of bitsets.
 *
 *  @tparam  This the type of the bitset itself.
 *
 *  @define bitsetinfo
 *  Bitsets are sets of non-negative integers which are represented as
 *  variable-size arrays of bits packed into 64-bit words. The size of a bitset is
 *  determined by the largest number stored in it.
 *  @author  Martin Odersky
 *  @version 2.8
 *  @since 2.8
 *  @define coll bitset
 *  @define Coll BitSet
 */
trait BitSetLike[+This <: BitSetLike[This] with Set[Int]] extends SetLike[Int, This] { self =>

  def empty: This

  /** The number of words (each with 64 bits) making up the set */
  protected def nwords: Int

  /** The words at index `idx', or 0L if outside the range of the set
   *  @note Requires `idx >= 0`
   */
  protected def word(idx: Int): Long

  /** Creates a new set of this kind from an array of longs
   */
  protected def fromArray(elems: Array[Long]): This

  override def size: Int = {
    var s = 0
    var i = nwords
    while (i > 0) {
      i -= 1
      s += popCount(word(i))
    }
    s
  }

  def iterator = new Iterator[Int] {
    private var current = 0
    private val end = nwords * WordLength
    def hasNext: Boolean = {
      while (current < end && !self.contains(current)) current += 1
      current < end
    }
    def next(): Int =
      if (hasNext) { val r = current; current += 1; r }
      else Iterator.empty.next
  }

  override def foreach[B](f: Int => B) {
    for (i <- 0 until nwords) {
      val w = word(i)
      for (j <- i * WordLength until (i + 1) * WordLength) {
        if ((w & (1L << j)) != 0L) f(j)
      }
    }
  }

  /** Computes the union between this bitset and another bitset by performing
   *  a bitwise "or".
   *
   *  @param   other  the bitset to form the union with.
   *  @return  a new bitset consisting of all bits that are in this
   *           bitset or in the given bitset `other`.
   */
  def | (other: BitSet): This = {
    val len = this.nwords max other.nwords
    val words = new Array[Long](len)
    for (idx <- 0 until len)
      words(idx) = this.word(idx) | other.word(idx)
    fromArray(words)
  }

  /** Computes the intersection between this bitset and another bitset by performing
   *  a bitwise "and".
   *  @param   that  the bitset to intersect with.
   *  @return  a new bitset consisting of all elements that are both in this
   *  bitset and in the given bitset `other`.
   */
  def & (other: BitSet): This = {
    val len = this.nwords min other.nwords
    val words = new Array[Long](len)
    for (idx <- 0 until len)
      words(idx) = this.word(idx) & other.word(idx)
    fromArray(words)
  }

  /** Computes the difference of this bitset and another bitset by performing
   *  a bitwise "and-not".
   *
   *  @param that the set of bits to exclude.
   *  @return     a bitset containing those bits of this
   *              bitset that are not also contained in the given bitset `other`.
   */
  def &~ (other: BitSet): This = {
    val len = this.nwords
    val words = new Array[Long](len)
    for (idx <- 0 until len)
      words(idx) = this.word(idx) & ~other.word(idx)
    fromArray(words)
  }

  /** Computes the symmetric difference of this bitset and another bitset by performing
   *  a bitwise "exclusive-or".
   *
   *  @param that the other bitset to take part in the symmetric difference.
   *  @return     a bitset containing those bits of this
   *              bitset or the other bitset that are not contained in both bitsets.
   */
  def ^ (other: BitSet): This = {
    val len = this.nwords max other.nwords
    val words = new Array[Long](len)
    for (idx <- 0 until len)
      words(idx) = this.word(idx) ^ other.word(idx)
    fromArray(words)
  }

  def contains(elem: Int): Boolean =
    0 <= elem && (word(elem >> LogWL) & (1L << elem)) != 0L

  /** Tests whether this bitset is a subset of another bitset.
   *
   *  @param that  the bitset to test.
   *  @return     `true` if this bitset is a subset of `other`, i.e. if
   *              every bit of this set is also an element in `other`.
   */
  def subsetOf(other: BitSet): Boolean =
    (0 until nwords) forall (idx => (this.word(idx) & ~ other.word(idx)) == 0L)

  override def addString(sb: StringBuilder, start: String, sep: String, end: String) = {
    sb append start
    var pre = ""
    for (i <- 0 until nwords * WordLength)
      if (contains(i)) {
        sb append pre append i
        pre = sep
      }
    sb append end
  }

  override def stringPrefix = "BitSet"
}

/** Companion object for BitSets. Contains private data only */
object BitSetLike {
  private[collection] val LogWL = 6
  private val WordLength = 64

  private[collection] def updateArray(elems: Array[Long], idx: Int, w: Long): Array[Long] = {
    var len = elems.length
    while (len > 0 && (elems(len - 1) == 0L || w == 0L && idx == len - 1)) len -= 1
    var newlen = len
    if (idx >= newlen && w != 0L) newlen = idx + 1
    val newelems = new Array[Long](newlen)
    Array.copy(elems, 0, newelems, 0, len)
    if (idx < newlen) newelems(idx) = w
    else assert(w == 0L)
    newelems
  }

  private val pc1: Array[Int] = {
    def countBits(x: Int): Int = if (x == 0) 0 else x % 2 + countBits(x >>> 1)
    Array.tabulate(256)(countBits _)
  }

  private def popCount(w: Long): Int = {
    def pc2(w: Int) = if (w == 0) 0 else pc1(w & 0xff) + pc1(w >>> 8)
    def pc4(w: Int) = if (w == 0) 0 else pc2(w & 0xffff) + pc2(w >>> 16)
    if (w == 0L) 0 else pc4(w.toInt) + pc4((w >>> 32).toInt)
  }
}
