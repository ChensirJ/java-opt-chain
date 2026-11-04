package io.github.chensirj.optchain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or constructor whose instance reads may be null-safe after weaving.
 * <p>
 * Preprocess emits {@code @OptChain(precise = true)} and wraps each {@code ?.} receiver in
 * {@link OptChainOps#opt(Object)}. The weaver then null-checks only those marked receivers.
 * A hand-written {@code @OptChain} with {@link #precise()} left at the default ({@code false})
 * and with no {@code opt} markers keeps the legacy behavior: every instance field read and
 * instance call in the method is null-checked.
 * </p>
 * <p>
 * Reference results become {@code null}. Primitive results become zero or {@code false}.
 * A {@code void} call is skipped.
 * </p>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface OptChain {

    /**
     * When {@code true}, weave only receivers passed through {@link OptChainOps#opt(Object)}.
     * Preprocess sets this. The default {@code false} is the legacy all-sites mode used by
     * hand-written annotations that do not insert markers.
     * <p>
     * If a method body contains {@code OptChainOps.opt} calls, those markers win and weaving
     * is precise even when this flag is {@code false}.
     * </p>
     */
    boolean precise() default false;
}
