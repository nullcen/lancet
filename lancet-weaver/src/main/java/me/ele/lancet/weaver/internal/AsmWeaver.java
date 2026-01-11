package me.ele.lancet.weaver.internal;

import me.ele.lancet.weaver.ClassData;
import me.ele.lancet.weaver.Weaver;
import me.ele.lancet.weaver.internal.asm.ClassTransform;
import me.ele.lancet.weaver.internal.entity.TransformInfo;
import me.ele.lancet.weaver.internal.graph.Graph;
import me.ele.lancet.weaver.internal.log.Log;


/**
 * Created by gengwanpeng on 17/3/21.
 */
public class AsmWeaver implements Weaver {

    /**
     * Create a AsmWeaver instance. In a compilation process, the AsmWeaver instance will only be created once.
     *
     * @param transformInfo the transformInfo for this compilation process.
     * @param graph
     * @return
     */
    public static Weaver newInstance(TransformInfo transformInfo, Graph graph, ClassLoader classLoader) {
        return new AsmWeaver(transformInfo, graph, classLoader);
    }

    private final TransformInfo transformInfo;
    private final Graph graph;
    private final ClassLoader classLoader;

    private AsmWeaver(TransformInfo transformInfo, Graph graph, ClassLoader classLoader) {
        Log.d(transformInfo.toString());
        this.graph = graph;
        this.transformInfo = transformInfo;
        this.classLoader = classLoader;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClassData[] weave(byte[] input, String relativePath) {
        if(!relativePath.endsWith(".class")){
            throw new IllegalArgumentException("relativePath is not a class: " + relativePath);
        }
        String internalName = relativePath.substring(0, relativePath.lastIndexOf('.'));
        try {
            return ClassTransform.weave(transformInfo, graph, input, internalName, classLoader);
        }catch (RuntimeException e){
            Log.e("error in transform: " + relativePath, e);
            return new ClassData[]{new ClassData(input, internalName)};
        }
    }

}
