package com.ggg.create_logic.metal;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CallableMetalFunction {
    int arguments() default 0;
}