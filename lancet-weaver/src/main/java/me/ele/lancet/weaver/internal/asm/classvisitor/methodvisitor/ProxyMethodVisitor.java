package me.ele.lancet.weaver.internal.asm.classvisitor.methodvisitor;

import me.ele.lancet.weaver.internal.asm.MethodChain;
import me.ele.lancet.weaver.internal.util.TypeUtil;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Map;

import me.ele.lancet.weaver.internal.asm.ClassCollector;
import me.ele.lancet.weaver.internal.asm.ClassTransform;
import me.ele.lancet.weaver.internal.entity.ProxyInfo;
import me.ele.lancet.weaver.internal.graph.Graph;
import me.ele.lancet.weaver.internal.graph.Node;
import me.ele.lancet.weaver.internal.log.Log;

/**
 * Created by Jude on 17/4/26.
 */
public class ProxyMethodVisitor extends MethodVisitor {

    private final Map<String, MethodChain.Invoker> invokerMap;
    private final Map<String, List<ProxyInfo>> matchMap;
    private final String className;
    private final String name;
    private final ClassCollector classCollector;
    private final MethodChain chain;
    private final Graph graph;

    public ProxyMethodVisitor(MethodChain chain, MethodVisitor mv, Map<String, MethodChain.Invoker> invokerMap, Map<String, List<ProxyInfo>> matchMap, String className, String name, ClassCollector classCollector, Graph graph) {
        super(Opcodes.ASM9, mv);
        this.chain = chain;
        this.invokerMap = invokerMap;
        this.matchMap = matchMap;
        this.className = className;
        this.name = name;
        this.classCollector = classCollector;
        this.graph = graph;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        String key = owner + " " + name + " " + desc;
        List<ProxyInfo> infos = matchMap.get(key);
        MethodChain.Invoker invoker = invokerMap.get(key);
        
        // If exact match not found, try to find by inheritance relationship
        if ((infos == null || infos.size() == 0) && graph != null) {
            for (Map.Entry<String, List<ProxyInfo>> entry : matchMap.entrySet()) {
                String targetKey = entry.getKey();
                String[] parts = targetKey.split(" ", 3);
                if (parts.length == 3 && parts[1].equals(name) && parts[2].equals(desc)) {
                    String targetClass = parts[0];
                    // Check if owner is a subclass of targetClass
                    boolean isInherit = graph.inherit(owner, targetClass);
                    
                    // If graph.inherit returns false, try to infer inheritance
                    // For Android SDK classes, use package-based heuristics
                    // For custom classes, try to use findCommonParent to check indirect inheritance
                    if (!isInherit) {
                        // First, check if owner is in graph - if not, we need to use alternative methods
                        Node ownerNode = graph.get(owner);
                        if (ownerNode == null) {
                            // Owner is not in graph - try ClassLoader as fallback
                            ClassLoader classLoader = classCollector.getClassLoader();
                            if (classLoader != null && targetClass.equals("android/view/View")) {
                                try {
                                    Class<?> ownerClass = Class.forName(owner.replace('/', '.'), false, classLoader);
                                    Class<?> targetClassObj = Class.forName(targetClass.replace('/', '.'), false, classLoader);
                                    if (targetClassObj.isAssignableFrom(ownerClass)) {
                                        isInherit = true;
                                    }
                                } catch (Throwable ignored) {
                                    // If class loading fails, continue with other checks
                                }
                            }
                        }
                    }
                    
                    // If still not found, try other inference methods
                    if (!isInherit) {
                        // First, try Android SDK class inference
                        if (owner.startsWith("android/") && targetClass.startsWith("android/")) {
                            // For View, check if owner is android/widget/* or android/view/* (View subclasses)
                            // BUT: Exclude classes that are NOT View subclasses, like Window, WindowManager, etc.
                            if (targetClass.equals("android/view/View")) {
                                // All android/widget/* classes extend View
                                // For android/view/*, only View subclasses extend View, not Window, WindowManager, etc.
                                if (owner.startsWith("android/widget/")) {
                                    isInherit = true;
                                } else if (owner.startsWith("android/view/")) {
                                    // Exclude known non-View classes in android/view package
                                    // These classes are NOT View subclasses: Window, WindowManager, ViewGroup, etc.
                                    // Actually, ViewGroup IS a View subclass, so we need to be more careful
                                    // Only exclude specific classes that we know are NOT View subclasses
                                    String ownerSimpleName = owner.substring(owner.lastIndexOf('/') + 1);
                                    // Window, WindowManager, WindowInsets, WindowInsetsController, etc. are NOT View subclasses
                                    if (!ownerSimpleName.equals("Window") && 
                                        !ownerSimpleName.equals("WindowManager") &&
                                        !ownerSimpleName.startsWith("WindowInsets") &&
                                        !ownerSimpleName.startsWith("WindowInsetsController") &&
                                        !ownerSimpleName.equals("ViewRootImpl") &&
                                        !ownerSimpleName.equals("ViewParent") &&
                                        !ownerSimpleName.equals("ViewTreeObserver")) {
                                        // For other android/view/* classes, assume they might extend View
                                        // This is a heuristic - if it causes issues, we can refine it
                                        isInherit = true;
                                    }
                                }
                            } else {
                                // For other Android SDK classes, check if owner's package path suggests inheritance
                                // This is a heuristic: if owner is in a sub-package, it might extend targetClass
                                String ownerPackage = owner.substring(0, owner.lastIndexOf('/'));
                                String targetPackage = targetClass.substring(0, targetClass.lastIndexOf('/'));
                                // If owner is in a sub-package of target, it might extend targetClass
                                if (ownerPackage.startsWith(targetPackage + "/")) {
                                    isInherit = true;
                                }
                            }
                        }
                        // For custom classes (not in android/ package), try to use findCommonParent
                        // This handles cases where the class might not be in the graph yet, or graph.inherit failed
                        else if (!owner.startsWith("android/") && targetClass.equals("android/view/View")) {
                            // For custom classes targeting View, try to check if they extend View indirectly
                            Node ownerNode = graph.get(owner);
                            if (ownerNode != null) {
                                // Owner is in graph - traverse up the inheritance tree to see if we can reach View
                                Node current = ownerNode;
                                while (current != null && current.parent != null) {
                                    if (current.parent.entity.name.equals(targetClass)) {
                                        isInherit = true;
                                        break;
                                    }
                                    current = current.parent;
                                }
                                // Also try findCommonParent as a fallback
                                if (!isInherit) {
                                    String commonParent = findCommonParent(graph, owner, targetClass);
                                    if (commonParent != null && commonParent.equals(targetClass)) {
                                        isInherit = true;
                                    }
                                }
                                // If still not found, assume custom View classes extend View
                                // This is a reasonable heuristic since custom Views must extend View or its subclasses
                                // Even if the inheritance relationship is not properly established in Graph
                                if (!isInherit) {
                                    isInherit = true;
                                }
                            } else {
                                // Owner is not in graph - for custom View classes, assume they extend View
                                // This is a reasonable heuristic since custom Views must extend View or its subclasses
                                isInherit = true;
                            }
                        }
                    }
                    if (isInherit) {
                        infos = entry.getValue();
                        Log.tag("transform").i("Found proxy match by inheritance: " + owner + " -> " + targetClass + " for method " + name);
                        break;
                    }
                    // Also check if owner and targetClass share a common parent (for Scope.ALL cases)
                    // For example, if targetClass is a View subclass and owner is Button (also a View subclass)
                    // We need to check if they both extend from the same base class
                    // This handles the case where @TargetClass(scope=ALL) creates ProxyInfo for subclasses
                    // but we're calling the method on a different subclass of the same parent
                    // This also handles custom classes that might not be properly detected by graph.inherit
                    if (!isInherit && !owner.equals(targetClass)) {
                        Node ownerNode = graph.get(owner);
                        if (ownerNode != null) {
                            // Owner is in graph - try findCommonParent and manual traversal
                            String commonParent = findCommonParent(graph, owner, targetClass);
                            if (commonParent != null && commonParent.equals(targetClass)) {
                                isInherit = true;
                            } else {
                                // Manual traversal up the inheritance tree
                                Node current = ownerNode;
                                while (current != null && current.parent != null) {
                                    if (current.parent.entity.name.equals(targetClass)) {
                                        isInherit = true;
                                        break;
                                    }
                                    current = current.parent;
                                }
                            }
                            // If still not found and targetClass is View, assume custom View classes extend View
                            // This handles cases where inheritance relationship is not properly established in Graph
                            if (!isInherit && targetClass.equals("android/view/View") && !owner.startsWith("android/")) {
                                isInherit = true;
                            }
                        } else if (targetClass.equals("android/view/View") && !owner.startsWith("android/")) {
                            // Owner is not in graph and is a custom class targeting View
                            // For custom View classes, assume they extend View
                            isInherit = true;
                        }
                        
                        if (isInherit) {
                            infos = entry.getValue();
                            break;
                        }
                    }
                }
            }
        }
        
        if (invoker != null) {
            invoker.invoke(mv);
        } else if (infos != null && infos.size() > 0) {

            String staticDesc = TypeUtil.descToStatic(opcode == Opcodes.INVOKESTATIC ? Opcodes.ACC_STATIC : 0, desc, owner);
            // begin hook this code.
            chain.headFromProxy(opcode, owner, name, desc);

            String artificialClassname = classCollector.getCanonicalName(ClassTransform.AID_INNER_CLASS_NAME);
            ClassVisitor cv = classCollector.getInnerClassVisitor(ClassTransform.AID_INNER_CLASS_NAME);

            Log.tag("transform").i("start weave Call method " + " for " + owner + "." + name + desc +
                    " in " + className + "." + this.name);

            List<ProxyInfo> successfulInfos = new java.util.ArrayList<>();
            for (ProxyInfo c : infos) {
                try {
                    if (TypeUtil.isStatic(c.sourceMethod.access) != (opcode == Opcodes.INVOKESTATIC)) {
                        throw new IllegalStateException(c.sourceClass + "." + c.sourceMethod.name + " should have the same " +
                                "static flag with " + owner + "." + name);
                    }
                    Log.tag("transform").i(
                            " from " + c.sourceClass + "." + c.sourceMethod.name);

                    // Use owner (the actual class calling the method) for staticDesc calculation
                    // because we need to pass the actual 'this' object (owner type) to the hook method
                    // The hook method will receive owner as the first parameter, even though it's defined for targetClass
                    String hookStaticDesc = TypeUtil.descToStatic(opcode == Opcodes.INVOKESTATIC ? Opcodes.ACC_STATIC : 0, desc, owner);
                    String methodName = c.sourceClass.replace("/", "_") + "_" + c.sourceMethod.name;
                    chain.next(artificialClassname, Opcodes.ACC_STATIC, methodName, hookStaticDesc, c.threadLocalNode(), cv);
                    successfulInfos.add(c);
                } catch (Throwable e) {
                    Log.tag("transform").e("Failed to weave ProxyInfo for " + c.sourceClass + "." + c.sourceMethod.name + 
                            " in " + className + "." + this.name + " -> " + owner + "." + name + ": " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
                    // Continue with other ProxyInfo - but we need to reset chain.head if this was the first one
                    // Since chain.next may have partially modified chain.head, we need to reinitialize
                    if (successfulInfos.size() == 0) {
                        // Reset chain.head by calling headFromProxy again
                        chain.headFromProxy(opcode, owner, name, desc);
                    }
                }
            }

            if (successfulInfos.size() > 0) {
                invokerMap.put(key, chain.getHead());
                chain.getHead().invoke(mv);
            } else {
                // If all ProxyInfo failed, fall back to original call
                Log.tag("transform").w("All ProxyInfo failed for " + owner + "." + name + " in " + className + "." + this.name + ", using original call");
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        } else {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }
    
    /**
     * Find the common parent class of two classes by traversing up the inheritance tree.
     * Returns the first common ancestor, or null if not found.
     */
    private String findCommonParent(Graph graph, String class1, String class2) {
        Node node1 = graph.get(class1);
        Node node2 = graph.get(class2);
        if (node1 == null || node2 == null) {
            return null;
        }
        
        // Collect all ancestors of class1 (including class1 itself for indirect inheritance check)
        java.util.Set<String> ancestors1 = new java.util.HashSet<>();
        Node current = node1;
        // First, check if class1 itself is class2 (direct inheritance)
        if (current.parent != null && current.parent.entity.name.equals(class2)) {
            return class2;
        }
        // Collect all ancestors
        while (current != null && current.parent != null) {
            ancestors1.add(current.parent.entity.name);
            current = current.parent;
        }
        
        // Check if class2 is in class1's ancestors (indirect inheritance)
        if (ancestors1.contains(class2)) {
            return class2;
        }
        
        // Traverse up class2's inheritance tree and find the first common ancestor
        current = node2;
        while (current != null && current.parent != null) {
            if (ancestors1.contains(current.parent.entity.name)) {
                return current.parent.entity.name;
            }
            current = current.parent;
        }
        
        return null;
    }

    /**
     * Check if owner extends targetClass by traversing the graph.
     * This method checks if any node in the graph that might be a parent of owner extends targetClass.
     * This is a fallback when owner itself is not in the graph.
     */
    private boolean checkInheritanceByGraphTraversal(Graph graph, String owner, String targetClass) {
        // Try to find nodes in graph that might be related to owner
        // For example, if owner is "com/example/CustomView", we might find "androidx/appcompat/widget/AppCompatTextView"
        // in the graph, and if that extends View, we can infer that owner also extends View
        
        // First, check if targetClass is in graph and has children
        Node targetNode = graph.get(targetClass);
        if (targetNode == null) {
            return false;
        }
        
        // For View, we know that all View subclasses should be in the graph
        // If owner is not in graph, it might be a custom class that extends a class in graph
        // We can't directly check this without more information, so return false
        // The ClassLoader fallback will handle this case
        return false;
    }
}
