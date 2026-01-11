package me.ele.lancet.plugin;

import com.android.build.api.artifact.ScopedArtifact;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.ScopedArtifacts;
import com.android.build.api.variant.Variant;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.ProjectConfigurationException;
import org.gradle.api.tasks.TaskProvider;

public class LancetPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (project.getPlugins().findPlugin("com.android.application") == null
                && project.getPlugins().findPlugin("com.android.library") == null) {
            throw new ProjectConfigurationException("Need android application/library plugin to be applied first", (Throwable) null);
        }

        LancetExtension lancetExtension = project.getExtensions().create("lancet", LancetExtension.class);
        AndroidComponentsExtension<?, ?, Variant> androidComponents =
                project.getExtensions().getByType(AndroidComponentsExtension.class);

        androidComponents.onVariants(androidComponents.selector().all(), variant -> {
            String taskName = "lancetTransform" + capitalize(variant.getName());
            TaskProvider<LancetTransformTask> taskProvider = project.getTasks().register(taskName, LancetTransformTask.class, task -> {
                task.getLogLevel().set(lancetExtension.getLogLevel().ordinal());
                task.getEnableIncremental().set(lancetExtension.getIncremental());
                task.getVariantName().set(variant.getName());
                String fileName = lancetExtension.getFileName();
                if (fileName != null) {
                    task.getLogFileName().set(fileName);
                }
            });

            variant.getArtifacts()
                    .forScope(ScopedArtifacts.Scope.ALL)
                    .use(taskProvider)
                    .toTransform(ScopedArtifact.CLASSES.INSTANCE, LancetTransformTask::getAllJars,
                            LancetTransformTask::getAllDirs, LancetTransformTask::getOutput);
        });
    }

    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
