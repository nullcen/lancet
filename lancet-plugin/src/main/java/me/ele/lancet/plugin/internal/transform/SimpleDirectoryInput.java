package me.ele.lancet.plugin.internal.transform;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class SimpleDirectoryInput implements DirectoryInput {

    private final File file;
    private final Map<File, Status> changedFiles;
    private final String name;

    public SimpleDirectoryInput(File file, Map<File, Status> changedFiles) {
        this.file = file;
        this.changedFiles = changedFiles == null ? Collections.emptyMap() : changedFiles;
        this.name = file.getName();
    }

    @Override
    public Map<File, Status> getChangedFiles() {
        return Collections.unmodifiableMap(changedFiles);
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public String getName() {
        return name;
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
