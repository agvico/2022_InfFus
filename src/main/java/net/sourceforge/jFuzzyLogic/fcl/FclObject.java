package net.sourceforge.jFuzzyLogic.fcl;

import net.sourceforge.jFuzzyLogic.CompileCpp;

import java.io.Serializable;

/**
 * The root of all FCL objects
 * 
 * @author pcingola
 *
 */
public abstract class FclObject implements CompileCpp, Serializable {

	@Override
	public String toString() {
		return toStringFcl();
	}

	@Override
	public String toStringCpp() {
		return "// " + this.getClass().getName();
	}

	public abstract String toStringFcl();
}
