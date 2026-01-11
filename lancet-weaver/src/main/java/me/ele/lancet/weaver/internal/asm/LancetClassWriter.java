package me.ele.lancet.weaver.internal.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public class LancetClassWriter extends ClassWriter {

    private final ClassLoader classLoader;

    public LancetClassWriter(ClassReader reader, int flags, ClassLoader classLoader) {
        super(reader, flags);
        this.classLoader = classLoader;
    }

    public LancetClassWriter(int flags, ClassLoader classLoader) {
        super(flags);
        this.classLoader = classLoader;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            Class<?> class1 = Class.forName(type1.replace('/', '.'), false, classLoader);
            Class<?> class2 = Class.forName(type2.replace('/', '.'), false, classLoader);
            if (class1.isAssignableFrom(class2)) {
                return type1;
            }
            if (class2.isAssignableFrom(class1)) {
                return type2;
            }
            if (class1.isInterface() || class2.isInterface()) {
                return "java/lang/Object";
            }
            do {
                class1 = class1.getSuperclass();
            } while (class1 != null && !class1.isAssignableFrom(class2));
            return class1 == null ? "java/lang/Object" : class1.getName().replace('.', '/');
        } catch (Throwable ignored) {
            return "java/lang/Object";
        }
    }
}
