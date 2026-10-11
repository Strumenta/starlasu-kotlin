package com.strumenta.starlasu.javalib;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a getter of a {@link JavaNode} as a required property (multiplicity SINGULAR). Without it, properties are
 * optional, except for primitive types (always required) and collections (always MANY).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Mandatory {
}
