package io.ch.java.opt.chain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method for compile-time optional-chain weaving (JS {@code ?.} style).
 * <p>
 * After {@code process-classes}, instance field reads and instance method calls
 * inside the method are rewritten so a null receiver short-circuits: reference
 * results become {@code null}, primitives become zero/false, and the call is skipped.
 * </p>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Every instance GETFIELD / INVOKEVIRTUAL / INVOKEINTERFACE in the method is
 *       null-checked — not only "logical" chains.</li>
 *   <li>Static calls, constructors, and {@code INVOKESPECIAL} are left unchanged.</li>
 *   <li>No full control-flow / data-flow analysis; side effects on null receivers
 *       are skipped by design (like JS optional call).</li>
 * </ul>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface OptChain {
}
