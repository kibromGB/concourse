/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2013 Jeff Nelson, Cinchapi Software Collective
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.cinchapi.concourse.server.model;

import java.nio.ByteBuffer;
import java.util.Objects;

import javax.annotation.concurrent.Immutable;

import org.cinchapi.concourse.annotate.DoNotInvoke;
import org.cinchapi.concourse.cache.ReferenceCache;
import org.cinchapi.concourse.server.io.Byteable;
import org.cinchapi.concourse.server.io.Byteables;
import org.cinchapi.concourse.util.ByteBuffers;

import com.google.common.base.Preconditions;

/**
 * A Position is an abstraction for the association between a
 * relative location and a {@link PrimaryKey} that is used in a
 * {@link SearchIndex} to specify the location of a term in a record.
 * 
 * @author jnelson
 */
@Immutable
public final class Position implements Byteable, Comparable<Position> {

	/**
	 * Return the Position encoded in {@code bytes} so long as those bytes
	 * adhere to the format specified by the {@link #getBytes()} method. This
	 * method assumes that all the bytes in the {@code bytes} belong to the
	 * Value. In general, it is necessary to get the appropriate Value slice
	 * from the parent ByteBuffer using
	 * {@link ByteBuffers#slice(ByteBuffer, int, int)}.
	 * 
	 * @param bytes
	 * @return the Position
	 */
	public static Position fromByteBuffer(ByteBuffer bytes) {
		return Byteables.read(bytes, Position.class); // We are using
														// Byteables#read(ByteBuffer,
														// Class) instead of
														// calling
														// the constructor
														// directly
														// so as to take
														// advantage
														// of the automatic
														// reference caching
														// that is
														// provided in the
														// utility
														// class
	}

	/**
	 * Return a Position that is backed by {@code primaryKey} and {@code index}.
	 * 
	 * @param primaryKey
	 * @param index
	 * @return the Position
	 */
	public static Position wrap(PrimaryKey primaryKey, int index) {
		Object[] cacheKey = { primaryKey, index };
		Position position = CACHE.get(cacheKey);
		if(position == null) {
			position = new Position(primaryKey, index);
			CACHE.put(position, cacheKey);
		}
		return position;
	}

	/**
	 * The total number of bytes used to encode a Position.
	 */
	public static final int MIN_SIZE = PrimaryKey.SIZE + 1; // index is stored
															// as either 1, 2 or
															// 4 bytes depending
															// upon its value

	/**
	 * Cache to store references that have already been loaded in the JVM.
	 */
	private static final ReferenceCache<Position> CACHE = new ReferenceCache<Position>();

	/**
	 * The PrimaryKey of the record that this Position represents.
	 */
	private final PrimaryKey primaryKey;

	/**
	 * The index that this Position represents.
	 */
	private final int index;

	/**
	 * A cached copy of the binary representation that is returned from
	 * {@link #getBytes()}.
	 */
	private transient ByteBuffer bytes;
	private transient final int size;

	/**
	 * Construct an instance that represents an existing Position from a
	 * ByteBuffer. This constructor is public so as to comply with the
	 * {@link Byteable} interface. Calling this constructor directly is not
	 * recommend. Use {@link #fromByteBuffer(ByteBuffer)} instead to take
	 * advantage of reference caching.
	 * 
	 * @param bytes
	 */
	@DoNotInvoke
	public Position(ByteBuffer bytes) {
		bytes.rewind();
		this.size = bytes.capacity();
		this.bytes = bytes;
		this.primaryKey = PrimaryKey.fromByteBuffer(ByteBuffers.get(bytes,
				PrimaryKey.SIZE));
		this.index = bytes.remaining() == 1 ? bytes.get()
				: (bytes.remaining() == 2 ? bytes.getShort() : bytes.getInt());
	}

	/**
	 * Construct a new instance.
	 * 
	 * @param primaryKey
	 * @param index
	 */
	private Position(PrimaryKey primaryKey, int index) {
		Preconditions.checkArgument(index > 0, "Cannot have an negative index");
		this.primaryKey = primaryKey;
		this.index = index;
		this.size = MIN_SIZE
				+ (index <= Byte.MAX_VALUE ? 0 : (index <= Short.MAX_VALUE ? 1
						: 3));
	}

	@Override
	public int compareTo(Position other) {
		int comparison;
		return (comparison = primaryKey.compareTo(other.primaryKey)) != 0 ? comparison
				: Integer.compare(index, other.index);
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof Position) {
			Position other = (Position) obj;
			return primaryKey.equals(other.primaryKey)
					&& Objects.equals(index, other.index);
		}
		return false;
	}

	/**
	 * Return a byte buffer that represents this Value with the following order:
	 * <ol>
	 * <li><strong>primaryKey</strong> - position 0</li>
	 * <li><strong>index</strong> - position 8</li>
	 * </ol>
	 * 
	 * @return the ByteBuffer representation
	 */
	@Override
	public ByteBuffer getBytes() {
		if(bytes == null) {
			bytes = ByteBuffer.allocate(size());
			bytes.put(primaryKey.getBytes());
			if(index <= Byte.MAX_VALUE) {
				bytes.put((byte) index);
			}
			else if(index <= Short.MAX_VALUE) {
				bytes.putShort((short) index);
			}
			else {
				bytes.putInt(index);
			}
		}
		return ByteBuffers.asReadOnlyBuffer(bytes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(primaryKey, index);
	}

	@Override
	public int size() {
		return size;
	}

	@Override
	public String toString() {
		return "Position " + index + " in Record " + primaryKey;
	}

}
