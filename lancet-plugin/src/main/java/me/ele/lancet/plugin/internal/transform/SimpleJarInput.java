package me.ele.lancet.plugin.internal.transform;

import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class SimpleJarInput implements JarInput {

    private final File file;
    private final Status status;
    private final String name;

    public SimpleJarInput(File file, Status status) {
        this.file = file;
        this.status = status;
        this.name = file.getName();
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public Set<QualifiedContent.ContentType> getContentTypes() {
        return Collections.emptySet();
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return Collections.emptySet();
    }
}
