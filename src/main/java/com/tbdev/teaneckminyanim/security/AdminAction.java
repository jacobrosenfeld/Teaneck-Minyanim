package com.tbdev.teaneckminyanim.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tags an admin write action in {@code area.verb} form (for example {@code override.create}).
 * The admin MVC handler and the API endpoint that perform the same action carry the same tag, which
 * is how the admin/API parity test pairs them up.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminAction {
    /** One or more {@code area.verb} action names. */
    String[] value();
}
