package me.ele.lancet.weaver.internal.asm;

import org.objectweb.asm.ClassReader;

import me.ele.lancet.weaver.ClassData;
import me.ele.lancet.weaver.internal.asm.classvisitor.HookClassVisitor;
import me.ele.lancet.weaver.internal.asm.classvisitor.InsertClassVisitor;
import me.ele.lancet.weaver.internal.asm.classvisitor.ProxyClassVisitor;
import me.ele.lancet.weaver.internal.asm.classvisitor.TryCatchInfoClassVisitor;
import me.ele.lancet.weaver.internal.entity.TransformInfo;
import me.ele.lancet.weaver.internal.graph.Graph;
import me.ele.lancet.weaver.internal.log.Log;

/**
 * Created by Jude on 2017/4/25.
 */

public class ClassTransform {

    public static final String AID_INNER_CLASS_NAME = "_lancet";

    public static ClassData[] weave(TransformInfo transformInfo, Graph graph, byte[] classByte, String internalName,
                                    ClassLoader classLoader) {
        ClassCollector classCollector = new ClassCollector(new ClassReader(classByte), graph, classLoader);

        classCollector.setOriginClassName(internalName);

        MethodChain chain = new MethodChain(internalName, classCollector.getOriginClassVisitor(), graph);
        ClassContext context = new ClassContext(graph, chain, classCollector.getOriginClassVisitor());

        ClassTransform transform = new ClassTransform(classCollector, context);
        transform.connect(new HookClassVisitor(transformInfo.hookClasses));
        transform.connect(new ProxyClassVisitor(transformInfo.proxyInfo));
        transform.connect(new InsertClassVisitor(transformInfo.executeInfo));
        transform.connect(new TryCatchInfoClassVisitor(transformInfo.tryCatchInfo));
        transform.startTransform();
        return classCollector.generateClassBytes();
    }

    private LinkedClassVisitor mHeadVisitor;
    private LinkedClassVisitor mTailVisitor;
    private ClassCollector mClassCollector;
    private final ClassContext context;

    public ClassTransform(ClassCollector mClassCollector, ClassContext context) {
        this.mClassCollector = mClassCollector;
        this.context = context;
    }

    void connect(LinkedClassVisitor visitor) {
        if (mHeadVisitor == null) {
            mHeadVisitor = visitor;
        } else {
            mTailVisitor.setNextClassVisitor(visitor);
        }
        mTailVisitor = visitor;
        visitor.setClassCollector(mClassCollector);
        visitor.setContext(context);
    }

    void startTransform() {
        mTailVisitor.setNextClassVisitor(mClassCollector.getOriginClassVisitor());
        // Temporarily disable EXPAND_FRAMES to avoid Frame calculation issues
        // TODO: Re-enable EXPAND_FRAMES once Frame calculation issues are resolved
        mClassCollector.mClassReader.accept(mHeadVisitor, 0);
    }
}
