package me.ele.lancet.plugin.internal.context;

import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;

import java.io.File;
import java.util.Set;

/**
 * Created by Jude on 2017/7/14.
 */

public class StatusOverrideJarInput implements JarInput {
    private final JarInput jarInput;
    private final Status status;


    public StatusOverrideJarInput(JarInput jarInput, Status status) {
        this.jarInput = jarInput;
        this.status = status;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public String getName() {
        return Hashing.sha1().hashString(jarInput.getFile().getPath() + status, Charsets.UTF_16LE).toString();
    }

    @Override
    public File getFile() {
        return jarInput.getFile();
    }

    @Override
    public Set<ContentType> getContentTypes() {
        return jarInput.getContentTypes();
    }

    @Override
    public Set<? super Scope> getScopes() {
        return jarInput.getScopes();
    }
}
