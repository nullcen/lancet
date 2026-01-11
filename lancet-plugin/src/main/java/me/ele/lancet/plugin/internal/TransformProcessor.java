package me.ele.lancet.plugin.internal;

import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.google.common.io.Files;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import me.ele.lancet.plugin.internal.context.ClassFetcher;
import me.ele.lancet.weaver.ClassData;
import me.ele.lancet.weaver.Weaver;

/**
 * Created by gengwanpeng on 17/5/4.
 */
public class TransformProcessor implements ClassFetcher, Closeable {

    private final Weaver weaver;
    private final File outputFile;
    private final Object outputLock = new Object();
    private final Set<String> writtenEntries = ConcurrentHashMap.newKeySet();
    private JarOutputStream jarOutputStream;

    public TransformProcessor(TransformContext context, Weaver weaver) {
        this.weaver = weaver;
        this.outputFile = context.getOutputFile();
    }

    @Override
    public boolean onStart(QualifiedContent content) throws IOException {
        if (content instanceof JarInput && ((JarInput) content).getStatus() == Status.REMOVED) {
            return false;
        }
        ensureOutputOpen();
        return true;
    }

    @Override
    public void onClassFetch(QualifiedContent content, Status status, String relativePath, byte[] bytes) throws IOException {
        if (status == Status.REMOVED) {
            return;
        }
        if (!relativePath.endsWith(".class")) {
            writeEntry(relativePath, bytes);
            return;
        }
        for (ClassData classData : weaver.weave(bytes, relativePath)) {
            writeEntry(classData.getClassName() + ".class", classData.getClassBytes());
        }
    }

    @Override
    public void onComplete(QualifiedContent content) throws IOException {
    }

    @Override
    public void close() throws IOException {
        synchronized (outputLock) {
            if (jarOutputStream != null) {
                jarOutputStream.close();
                jarOutputStream = null;
            }
        }
    }

    private void ensureOutputOpen() throws IOException {
        synchronized (outputLock) {
            if (jarOutputStream == null) {
                Files.createParentDirs(outputFile);
                jarOutputStream = new JarOutputStream(
                        new BufferedOutputStream(new FileOutputStream(outputFile)));
            }
        }
    }

    private void writeEntry(String entryName, byte[] bytes) throws IOException {
        ensureOutputOpen();
        synchronized (outputLock) {
            if (!writtenEntries.add(entryName)) {
                return;
            }
            ZipEntry entry = new ZipEntry(entryName);
            jarOutputStream.putNextEntry(entry);
            jarOutputStream.write(bytes);
            jarOutputStream.closeEntry();
        }
    }
}
