package me.ele.lancet.weaver.internal.asm.classvisitor;

import me.ele.lancet.weaver.internal.asm.MethodChain;
import org.objectweb.asm.MethodVisitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import me.ele.lancet.weaver.internal.asm.LinkedClassVisitor;
import me.ele.lancet.weaver.internal.asm.classvisitor.methodvisitor.ProxyMethodVisitor;
import me.ele.lancet.weaver.internal.entity.ProxyInfo;
import me.ele.lancet.weaver.internal.graph.Graph;
import me.ele.lancet.weaver.internal.log.Log;

/**
 * Created by Jude on 17/4/26.
 */
public class ProxyClassVisitor extends LinkedClassVisitor {

    private List<ProxyInfo> infos;
    private Map<String, List<ProxyInfo>> matches;
    private Map<String, MethodChain.Invoker> maps = new HashMap<>();
    public ProxyClassVisitor(List<ProxyInfo> infos) {
        this.infos = infos;
        Log.tag("transform").i("ProxyClassVisitor created with " + infos.size() + " ProxyInfo(s)");
        if (infos.size() > 0) {
            Log.tag("transform").i("First ProxyInfo: targetClass=" + infos.get(0).targetClass + ", regex=" + infos.get(0).regex);
        }
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        // 对于 @Proxy，nameRegex 用于过滤调用方法的类，而不是目标类
        // 如果没有 nameRegex，应该为所有类创建 ProxyMethodVisitor
        // 这样可以在 visitMethodInsn 中检查方法调用
        boolean hasNoRegex = infos.stream().anyMatch(t -> t.regex == null || t.regex.isEmpty());
        if (hasNoRegex) {
            // 为所有无 nameRegex 的 ProxyInfo 创建 matches（为所有类创建 ProxyMethodVisitor）
            matches = infos.stream()
                    .filter(t -> t.regex == null || t.regex.isEmpty())
                    .collect(Collectors.groupingBy(t -> t.targetClass + " " + t.targetMethod + " " + t.targetDesc));
        } else {
            // 有 nameRegex 的情况，只匹配符合 regex 的类
            matches = infos.stream()
                    .filter(t -> t.match(name))
                    .collect(Collectors.groupingBy(t -> t.targetClass + " " + t.targetMethod + " " + t.targetDesc));
            if (matches.size() > 0) {
                Log.tag("transform").d("ProxyClassVisitor: Class " + name + " - Matched by nameRegex. Matches size: " + matches.size());
            }
        }

        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        String className = getContext() != null ? getContext().name : "unknown";
        if (matches != null && matches.size() > 0) {
            Graph graph = getContext() != null ? getContext().getGraph() : null;
            if (graph == null) {
                Log.tag("transform").w("ProxyClassVisitor: Graph is null for class " + className + ", skipping ProxyMethodVisitor");
            } else {
                mv = new ProxyMethodVisitor(getContext().getChain(), mv, maps, matches, className, name, getClassCollector(), graph);
            }
        }
        return mv;
    }
}
