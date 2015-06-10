package org.sleuthkit.autopsy.coreutils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD})
@Inherited
@Documented
public @interface ThreadConfined {

    ThreadType type();

    public enum ThreadType {

        ANY, UI, JFX, AWT, NOT_UI
    }
}
