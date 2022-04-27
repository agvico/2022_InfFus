/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package utils

import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.util
import java.util.Arrays

/**
  * A simple, fixed-size bit set implementation. This implementation is fast because it avoids
  * safety/bound checking.
  */
class BitSet(numBits: Int) extends Serializable {

  private var words = new Array[Long](bit2words(numBits))
  private var numWords = bit2words(numBits) //words.length
  //this.numWords = bit2words(numBits)
  private val lastPosition = numBits % 64

  /**
    * Compute the capacity (number of bits) that can be represented
    * by this bitset.
    */
  def capacity: Int = numWords * 64

  /**
    * Clear all set bits.
    */
  def clear(): Unit = Arrays.fill(words, 0)

  /**
    * Set all the bits up to a given index
    */
  def setUntil(bitIndex: Int): Unit = {
    val wordIndex = bitIndex >> 6 // divide by 64
    Arrays.fill(words, 0, wordIndex, -1)
    if(wordIndex < words.length) {
      // Set the remaining bits (note that the mask could still be zero)
      val mask = ~(-1L << (bitIndex & 0x3f))
      words(wordIndex) |= mask
    }
  }

  /**
    * Clear all the bits up to a given index
    */
  def clearUntil(bitIndex: Int): Unit = {
    val wordIndex = bitIndex >> 6 // divide by 64
    Arrays.fill(words, 0, wordIndex, 0)
    if(wordIndex < words.length) {
      // Clear the remaining bits
      val mask = -1L << (bitIndex & 0x3f)
      words(wordIndex) &= mask
    }
  }

  /**
    * Compute the bit-wise AND of the two sets returning the
    * result.
    */
  def &(other: BitSet): BitSet = {
    val newBS = new BitSet(math.max(capacity, other.capacity))
    val smaller = math.min(numWords, other.numWords)
    assert(newBS.numWords >= numWords)
    assert(newBS.numWords >= other.numWords)
    var ind = 0
    while( ind < smaller ) {
      newBS.words(ind) = words(ind) & other.words(ind)
      ind += 1
    }
    newBS
  }

  /**
    * Compute the bit-wise NAND of the two sets returning the
    * result.
    */
  def ~&(other: BitSet): BitSet = {
    val newBS = new BitSet(math.max(capacity, other.capacity))
    val smaller = math.min(numWords, other.numWords)
    assert(newBS.numWords >= numWords)
    assert(newBS.numWords >= other.numWords)
    var ind = 0
    while( ind < smaller ) {
      newBS.words(ind) = ~(words(ind) & other.words(ind))
      ind += 1
    }
    newBS
  }

  /**
    * It performs the NOT operation over this BitSet, returnin a new one.
    * @return
    */
  def unary_~ :BitSet = {
    this ~& this
  }

  /**
    * Compute the bit-wise OR of the two sets returning the
    * result.
    */
  def |(other: BitSet): BitSet = {
    val newBS = new BitSet(math.max(capacity, other.capacity))
    assert(newBS.numWords >= numWords)
    assert(newBS.numWords >= other.numWords)
    val smaller = math.min(numWords, other.numWords)
    var ind = 0
    while( ind < smaller ) {
      newBS.words(ind) = words(ind) | other.words(ind)
      ind += 1
    }
    while( ind < numWords ) {
      newBS.words(ind) = words(ind)
      ind += 1
    }
    while( ind < other.numWords ) {
      newBS.words(ind) = other.words(ind)
      ind += 1
    }
    newBS
  }

  /**
    * Compute the symmetric difference by performing bit-wise XOR of the two sets returning the
    * result.
    */
  def ^(other: BitSet): BitSet = {
    val newBS = new BitSet(math.max(capacity, other.capacity))
    val smaller = math.min(numWords, other.numWords)
    var ind = 0
    while (ind < smaller) {
      newBS.words(ind) = words(ind) ^ other.words(ind)
      ind += 1
    }
    if (ind < numWords) {
      Array.copy( words, ind, newBS.words, ind, numWords - ind )
    }
    if (ind < other.numWords) {
      Array.copy( other.words, ind, newBS.words, ind, other.numWords - ind )
    }
    newBS
  }


  /**
   * Computes the difference between two bitsets.
   *
   * It returns the elements in {@code this} that are not in {@code other}.
   *
   * It is a handy short of the andNot operation, i.e., {@code this} andNot {@code other}
   *
   * @param other
   * @return
   */
  def -(other: BitSet): BitSet = {
    this andNot other
  }


  /**
    * Compute the difference of the two sets by performing bit-wise AND-NOT returning the
    * result.
    */
  def andNot(other: BitSet): BitSet = {
    val newBS = new BitSet(capacity)
    val smaller = math.min(numWords, other.numWords)
    var ind = 0
    while (ind < smaller) {
      newBS.words(ind) = words(ind) & ~other.words(ind)
      ind += 1
    }
    if (ind < numWords) {
      Array.copy( words, ind, newBS.words, ind, numWords - ind )
    }
    newBS
  }

  /**
    * Sets the bit at the specified index to true.
    * @param index the bit index
    */
  def set(index: Int) {
    this.expandTo(index >> 6)
    val bitmask = 1L << (index & 0x3f)  // mod 64 and shift
    words(index >> 6) |= bitmask        // div by 64 and mask
  }

  def unset(index: Int) {
    this.expandTo(index >> 6)
    val bitmask = 1L << (index & 0x3f)  // mod 64 and shift
    words(index >> 6) &= ~bitmask        // div by 64 and mask
  }

  /**
    * Return the value of the bit with the specified index. The value is true if the bit with
    * the index is currently set in this BitSet; otherwise, the result is false.
    *
    * @param index the bit index
    * @return the value of the bit with the specified index
    */
  def get(index: Int): Boolean = {
    val bitmask = 1L << (index & 0x3f)   // mod 64 and shift
    (words(index >> 6) & bitmask) != 0  // div by 64 and mask
  }


  /**
    * Returns a subset of the current bitset formed from bits from var1 to var2
    *
    * @param var1
    * @param var2
    * @return
    */
  def get(var1: Int, var2: Int): BitSet = {
    //checkRange(var1, var2)
    //this.checkInvariants()
    val var3 = this.capacity
    if (var3 > var1 && var1 != var2) {
      val v2: Int = if (var2 > var3) var3 else var2
      val var4 = new BitSet(v2 - var1)
      val var5 = wordIndex(v2 - var1 - 1) + 1
      var var6 = wordIndex(var1)
      val var7 = (var1 & 63) == 0
      var var8 = 0
      while (var8 < var5 - 1) {
        var4.words(var8) = if (var7)
          this.words(var6)
        else
          this.words(var6) >>> var1 | this.words(var6 + 1) << -var1
        var8 += 1
        var6 += 1; var6

      }
      val var10 = -1L >>> -var2
      var4.words(var5 - 1) = if ((var2 - 1 & 63) < (var1 & 63)) this.words(var6) >>> var1 | (this.words(var6 + 1) & var10) << -var1
      else (this.words(var6) & var10) >>> var1
      var4.numWords = var5
      var counter = this.numWords - 1
      while(counter >= 0 && this.words(counter) == 0L){
        counter -= 1
      }
      //this.numWords = counter + 1
      //var4.recalculateWordsInUse()
      //var4.checkInvariants()
      var4
    } else
      new BitSet(0)
  }


  /**
    * Get an iterator over the set bits.
    */
  def iterator: Iterator[Int] = new Iterator[Int] {
    var ind = nextSetBit(0)
    override def hasNext: Boolean = ind >= 0
    override def next(): Int = {
      val tmp = ind
      ind = nextSetBit(ind + 1)
      tmp
    }
  }


  /** Return the number of bits set to true in this BitSet. */
  def cardinality(): Int = {
    var sum = 0
    var i = 0
    while (i < numWords - 1) {
      sum += java.lang.Long.bitCount(words(i))
      i += 1
    }
    var a = words(i) >>> (64 - lastPosition)
    a = a << lastPosition
    sum += java.lang.Long.bitCount(a)
    sum
  }

  /**
    * Returns the index of the first bit that is set to true that occurs on or after the
    * specified starting index. If no such bit exists then -1 is returned.
    *
    * To iterate over the true bits in a BitSet, use the following loop:
    *
    *  for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
    *    // operate on index i here
    *  }
    *
    * @param fromIndex the index to start checking from (inclusive)
    * @return the index of the next set bit, or -1 if there is no such bit
    */
  def nextSetBit(fromIndex: Int): Int = {
    var wordIndex = fromIndex >> 6
    if (wordIndex >= numWords) {
      return -1
    }

    // Try to find the next set bit in the current word
    val subIndex = fromIndex & 0x3f
    var word = words(wordIndex) >> subIndex
    if (word != 0) {
      return (wordIndex << 6) + subIndex + java.lang.Long.numberOfTrailingZeros(word)
    }

    // Find the next set bit in the rest of the words
    wordIndex += 1
    while (wordIndex < numWords) {
      word = words(wordIndex)
      if (word != 0) {
        return (wordIndex << 6) + java.lang.Long.numberOfTrailingZeros(word)
      }
      wordIndex += 1
    }

    -1
  }

  /** Return the number of longs it would take to hold numBits. */
  private def bit2words(numBits: Int) = ((numBits - 1) >> 6) + 1


  def toBitString(): String = {
  var result = ""

    for(i <- 0 until this.capacity){
      if(get(i)){
        result += "1"
      } else {
        result += "0"
      }
    }
    result
  }

  private def ensureCapacity(var1: Int): Unit = {
    if (this.words.length < var1) {
      val var2: Int = Math.max(2 * this.words.length, var1)
      this.words = util.Arrays.copyOf(this.words, var2)
    }
  }

  private def expandTo(var1: Int): Unit = {
    val var2: Int = var1 + 1
    if (this.numWords < var2) {
      this.ensureCapacity(var2)
      this.numWords = var2
    }
  }




  private def wordIndex(i: Int): Int = {
    i >> 6
  }

  /**
    * It concatenates the words of  two BitSets
    * @param other
    * @return
    */
  def ++(other: BitSet) : BitSet = {

    val a = new BitSet(0)
    a.words = this.words ++ other.words
    a.numWords = this.numWords + other.numWords

    a
  }


  /**
    * It sets from min to max the values of the given BitSet into this.
    *
    * @param min
    * @param max
    * @param other
    */
  def set(min: Int, max: Int, other: BitSet): Unit = {

  }


  /**
    * It concatenates two BitSets from the specified threshold of {@code this} until the {@code length} of {@code two}
    * @param tamFirst
    * @param other
    * @param tamSecond
    * @return
    */
  def concatenate(tamFirst: Int, other: BitSet, tamSecond: Int): BitSet = {

    if(this.capacity == 0 || tamFirst == 0){
      return other
    }

    if(other.capacity == 0 || tamSecond == 0)
      return this

    if(tamFirst % 64 == 0){
      return this ++ other
    }

    val totalSize = tamFirst + tamSecond
    val totalWords = Math.ceil(totalSize.toDouble / 64.0).toInt
    val newWords = new Array[Long](totalWords)
    val position = tamFirst % 64        // position of the last element of the first bitset.
    val displacement = 64 - position    // garbage bits on "this"
    val garbageSecond = 64 - (tamSecond % 64) // garbage bits on "other"

    var i = this.numWords - 1

    val newBitSet = this ++ other

    while(i < newBitSet.words.length - 1){
      newBitSet.words(i) = newBitSet.words(i + 1) << position | newBitSet.words(i) >>> displacement
      i += 1
    }
    newBitSet.words(i) = newBitSet.words(i) >>> displacement

    // discard whole garbage words.
    val garbageWords = Math.floor( (displacement  + garbageSecond).toDouble / 64.0).toInt
    newBitSet.words.dropRight(garbageWords)

    newBitSet.numWords = totalWords

    newBitSet
  }



  override def equals(obj: Any): Boolean = {
    val other = obj.asInstanceOf[BitSet]

    if(this.capacity != other.capacity) return false

    for(i <- words.indices){
      if(this.words(i) != other.words(i)) {
        println("not equals at word " + i)
        println("orig: " + this.get((i-1)*64, (i+1) * 64).toBitString())
        println("new : " + other.get((i-1)*64, (i+1) * 64).toBitString())
        println("Num different bits: " + (this ^ other).cardinality() + " first at: " + (this ^ other).nextSetBit(0))
        return false
      }

    }

    return true
  }




  def saveToDisk(path: String): Unit = {
    val oos = new ObjectOutputStream(new FileOutputStream(path))
    oos.writeObject(this)
    oos.close()
  }


  def load(path: String): BitSet = {
    val ois = new ObjectInputStream(new FileInputStream(path))
    val ret = ois.readObject().asInstanceOf[BitSet]
    ois.close()
    ret
  }

  def copy: BitSet = {
    new BitSet(this.numBits) | this
  }

  override def toString: String = get(0,1000).toBitString()
}

/**
 * Companion BitSet object.
 */
object BitSet {

  /**
   * It creates an empty bitset of size 0
   * @return
   */
  def empty: BitSet = new BitSet(0)


  /**
   * It creates a new empty bitset with the specified size.
   * @param size
   * @return
   */
  def ofSize(size: Int): BitSet = new BitSet(size)
}
