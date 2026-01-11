package me.ele.lancet.weaver.internal.parser.anno;

import com.google.common.base.Strings;
import me.ele.lancet.base.annotations.ClassOf;
import me.ele.lancet.weaver.internal.exception.IllegalAnnotationException;
import me.ele.lancet.weaver.internal.graph.Graph;
import me.ele.lancet.weaver.internal.meta.HookInfoLocator;
import me.ele.lancet.weaver.internal.parser.AnnoParser;
import me.ele.lancet.weaver.internal.parser.AnnotationMeta;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by gengwanpeng on 17/5/5.
 */
public class ClassOfAnnoParser implements AnnoParser {

    private Pattern pattern = Pattern.compile("^(((?![0-9])\\w+\\.)*((?![0-9])\\w+\\$)?(?![0-9])\\w+)((\\[])*)$");

    @SuppressWarnings("unchecked")
    @Override
    public AnnotationMeta parseAnno(AnnotationNode annotationNode) {
        List<Object> values;
        String className = null;
        int index = 0;
        if ((values = annotationNode.values) != null) {
            for (int i = 0; i < values.size(); i += 2) {
                switch ((String) values.get(i)) {
                    case "value":
                        className = (String) values.get(i + 1);
                        if (Strings.isNullOrEmpty(className)) {
                            throw new IllegalAnnotationException("@ClassOf value can't be empty or null");
                        }

                        break;
                    case ClassOf.INDEX:
                        index = (int) values.get(i + 1);
                        break;
                    default:
                        throw new IllegalAnnotationException();
                }
            }

            Type type = Type.getType(toDesc(className));
            return new ClassOfAnnoMeta(annotationNode.desc, index, type);
        }

        throw new IllegalAnnotationException("@ClassOf is illegal, must specify value field");
    }

    private String toDesc(String className) {
        Matcher matcher = pattern.matcher(className);
        if (!matcher.find()) {
            throw new IllegalAnnotationException("value in @ClassOf is not a legal type: " + className);
        }
        String clazz = matcher.group(1);
        String bracket = matcher.group(4);
        StringBuilder sb = new StringBuilder(clazz.length() + 10);
        if (bracket != null) {
            for (int i = 0, j = bracket.length() >> 1; i < j; i++) {
                sb.append('[');
            }
        }
        return sb.append('L').append(clazz.replace('.', '/')).append(';').toString();
    }

    private static class ClassOfAnnoMeta extends AnnotationMeta {
        private final int index;
        private final Type type;

        private ClassOfAnnoMeta(String desc, int index, Type type) {
            super(desc);
            this.index = index;
            this.type = type;
        }

        @Override
        public void accept(HookInfoLocator locator) {
            Graph graph = locator.graph();
            Type origin = locator.getArgsType()[index];
            if (origin.getSort() != Type.OBJECT && origin.getSort() != Type.ARRAY) {
                throw new IllegalArgumentException(
                    String.format("@ClassOf parameter[%d]: origin type '%s' should be Object or Array type, but got '%s'",
                        index, origin.getInternalName(), origin.getSort()));
            }

            String typeInternalName = internalClassName(type);
            String originInternalName = internalClassName(origin);

            if (type.getDimensions() == origin.getDimensions()) {
                // Check if type is a subclass of origin
                boolean typeExists = graph.get(typeInternalName) != null;
                boolean originExists = graph.get(originInternalName) != null;
                
                if (!typeExists || !originExists) {
                    // If either class is not in graph (e.g., third-party library classes),
                    // require origin to be java.lang.Object for type safety
                    if (!"java/lang/Object".equals(originInternalName)) {
                        if (!typeExists && !originExists) {
                            throw new IllegalArgumentException(
                                String.format("@ClassOf parameter[%d]: both type '%s' (from @ClassOf value) and origin type '%s' (parameter declaration) are not found in class graph. " +
                                    "When using third-party classes, the parameter type must be java.lang.Object.",
                                    index, typeInternalName.replace('/', '.'), originInternalName.replace('/', '.')));
                        } else if (!typeExists) {
                            throw new IllegalArgumentException(
                                String.format("@ClassOf parameter[%d]: type '%s' (from @ClassOf value) is not found in class graph. " +
                                    "When using third-party classes, the parameter type must be java.lang.Object.",
                                    index, typeInternalName.replace('/', '.')));
                        } else {
                            throw new IllegalArgumentException(
                                String.format("@ClassOf parameter[%d]: origin type '%s' (parameter declaration) is not found in class graph. " +
                                    "Use java.lang.Object as the parameter type.",
                                    index, originInternalName.replace('/', '.')));
                        }
                    }
                    // If origin is Object, allow it (all classes inherit from Object)
                } else {
                    // Both classes exist in graph, verify inheritance relationship
                    if (!graph.inherit(typeInternalName, originInternalName)) {
                        throw new IllegalArgumentException(
                            String.format("@ClassOf parameter[%d]: type '%s' (from @ClassOf value) is not a subclass of '%s' (parameter declaration). " +
                                "The parameter type should be a parent class of the @ClassOf value.",
                                index, typeInternalName.replace('/', '.'), originInternalName.replace('/', '.')));
                    }
                }
            } else {
                // Different array dimensions, origin must be Object
                if (origin.getSort() != Type.OBJECT || !"java/lang/Object".equals(origin.getInternalName())) {
                    throw new IllegalArgumentException(
                        String.format("@ClassOf parameter[%d]: when array dimensions differ, origin type must be java.lang.Object, but got '%s'",
                            index, originInternalName.replace('/', '.')));
                }
            }
            locator.adjustTargetMethodArgs(index, type);
        }

        private String internalClassName(Type type) {
            if (type.getSort() == Type.OBJECT) {
                return type.getInternalName();
            } else { // array
                return type.getElementType().getInternalName();
            }
        }
    }
}
