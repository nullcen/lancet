package me.ele.lancet.plugin.internal;

import com.google.common.base.Strings;
import org.gradle.api.Project;

import java.io.File;

/**
 * Created by gengwanpeng on 17/4/26.
 */
public class GlobalContext {

    private final Project project;
    private final String variantName;

    public GlobalContext(Project project) {
        this.project = project;
        this.variantName = null;
    }

    public GlobalContext(Project project, String variantName) {
        this.project = project;
        this.variantName = variantName;
    }


    public File getLancetDir() {
        if (Strings.isNullOrEmpty(variantName)) {
            return new File(project.getBuildDir(), "lancet");
        }
        return new File(new File(project.getBuildDir(), "lancet"), variantName);
    }
}
