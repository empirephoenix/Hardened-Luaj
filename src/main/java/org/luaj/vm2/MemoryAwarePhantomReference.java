package org.luaj.vm2;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

public class MemoryAwarePhantomReference<T> extends PhantomReference<T> {

	private int	size;

	public MemoryAwarePhantomReference(final T referent, final ReferenceQueue<T> q, final int size) {
		super(referent, q);
		this.size = size;
	}

	public int getSize() {
		return this.size;
	}
}
