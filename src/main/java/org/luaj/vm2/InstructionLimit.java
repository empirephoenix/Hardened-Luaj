package org.luaj.vm2;


/**
 * instruction counter
 * 
 * @author empire
 *
 */
public class InstructionLimit {
	private int	currentInstructions;
	private int	maxInstructions;
	private int	maxStringSize;

	public void increase(final int instructions) {
		this.currentInstructions += instructions;
		if (this.currentInstructions > this.maxInstructions) {
			throw new LuaLimitException("Max instructions reached " + this.maxInstructions);
		}
	}

	public void executionLimit() {
		this.increase(1);
	}

	public void setMaxInstructions(final int maxInstructions) {
		this.maxInstructions = maxInstructions;
	}

	public void reset() {
		this.currentInstructions = 0;
	}

	public int getCurrentInstructions() {
		return this.currentInstructions;
	}

	public int maxStringSize() {
		return this.maxStringSize;
	}

	public void setMaxStringSize(final int maxStringSize) {
		this.maxStringSize = maxStringSize;
	}

}
