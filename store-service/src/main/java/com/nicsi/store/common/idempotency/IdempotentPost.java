package com.nicsi.store.common.idempotency;

import java.lang.annotation.*;

/**
 * Marks a controller method as a posting endpoint that requires
 * an Idempotency-Key header (8-150 characters).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdempotentPost {
}
