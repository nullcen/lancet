package me.ele.lancet.plugin;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.Status;
import com.google.common.base.Strings;
import com.google.common.io.Files;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import me.ele.lancet.plugin.internal.GlobalContext;
import me.ele.lancet.plugin.internal.LocalCache;
import me.ele.lancet.plugin.internal.TransformContext;
import me.ele.lancet.plugin.internal.TransformProcessor;
import me.ele.lancet.plugin.internal.context.ContextReader;
import me.ele.lancet.plugin.internal.preprocess.PreClassAnalysis;
import me.ele.lancet.plugin.internal.transform.SimpleDirectoryInput;
import me.ele.lancet.plugin.internal.transform.SimpleJarInput;
import me.ele.lancet.weaver.MetaParser;
import me.ele.lancet.weaver.Weaver;
import me.ele.lancet.weaver.internal.AsmWeaver;
import me.ele.lancet.weaver.internal.entity.TransformInfo;
import me.ele.lancet.weaver.internal.log.Impl.FileLoggerImpl;
import me.ele.lancet.weaver.internal.log.Log;
import me.ele.lancet.weaver.internal.parser.AsmMetaParser;

public abstract class LancetTransformTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ListProperty<RegularFile> getAllJars();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ListProperty<Directory> getAllDirs();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Input
    public abstract Property<Integer> getLogLevel();

    @Optional
    @Input
    public abstract Property<String> getLogFileName();

    @Input
    public abstract Property<Boolean> getEnableIncremental();

    @Optional
    @Input
    public abstract Property<String> getVariantName();

    @TaskAction
    public void executeTask() throws IOException, InterruptedException {
        String variantName = getVariantName().getOrNull();
        GlobalContext global = new GlobalContext(getProject(), variantName);
        LocalCache cache = new LocalCache(global.getLancetDir());

        initLog(global);

        Log.i("start time: " + System.currentTimeMillis());

        List<JarInput> jarInputs = getAllJars().get().stream()
                .map(RegularFile::getAsFile)
                .map(file -> new SimpleJarInput(file, Status.ADDED))
                .collect(Collectors.toList());
        List<DirectoryInput> directoryInputs = getAllDirs().get().stream()
                .map(Directory::getAsFile)
                .map(dir -> new SimpleDirectoryInput(dir, Collections.emptyMap()))
                .collect(Collectors.toList());

        if (Boolean.TRUE.equals(getEnableIncremental().getOrElse(false))) {
            Log.i("Incremental mode is not supported in AGP 8; running a full Lancet transform.");
        }
        boolean incremental = false;

        TransformContext context = new TransformContext(jarInputs, directoryInputs, incremental,
                getOutput().get().getAsFile(), global);

        Log.i("after android plugin, incremental: " + context.isIncremental());
        Log.i("now: " + System.currentTimeMillis());

        PreClassAnalysis preClassAnalysis = new PreClassAnalysis(cache);
        incremental = preClassAnalysis.execute(incremental, context);

        Log.i("after pre analysis, incremental: " + incremental);
        Log.i("now: " + System.currentTimeMillis());

        ClassLoader classLoader = createClassLoader(context);
        MetaParser parser = new AsmMetaParser(classLoader);
        if (incremental && !context.getGraph().checkFlow()) {
            incremental = false;
            context.clear();
        }
        Log.i("after check flow, incremental: " + incremental);
        Log.i("now: " + System.currentTimeMillis());

        context.getGraph().flow().clear();
        TransformInfo transformInfo = parser.parse(context.getHookClasses(), context.getGraph());

        Weaver weaver = AsmWeaver.newInstance(transformInfo, context.getGraph(), classLoader);
        TransformProcessor processor = new TransformProcessor(context, weaver);
        try {
            new ContextReader(context).accept(incremental, processor);
        } finally {
            processor.close();
        }
        Log.i("build successfully done");
        Log.i("now: " + System.currentTimeMillis());

        cache.saveToLocal();
        Log.i("cache saved");
        Log.i("now: " + System.currentTimeMillis());
    }

    private ClassLoader createClassLoader(TransformContext context) {
        URL[] urls = Stream.concat(context.getAllJars().stream(), context.getAllDirs().stream())
                .map(content -> content.getFile())
                .map(File::toURI)
                .map(u -> {
                    try {
                        return u.toURL();
                    } catch (MalformedURLException e) {
                        throw new AssertionError(e);
                    }
                })
                .toArray(URL[]::new);
        Log.d("urls:\n" + com.google.common.base.Joiner.on("\n ").join(urls));
        return URLClassLoader.newInstance(urls, null);
    }

    private void initLog(GlobalContext global) throws IOException {
        int level = getLogLevel().getOrElse(Log.Level.INFO.ordinal());
        Log.setLevel(Log.Level.values()[level]);
        String fileName = getLogFileName().getOrNull();
        if (!Strings.isNullOrEmpty(fileName)) {
            if (fileName.contains(File.separator)) {
                throw new IllegalArgumentException("Log file name can't contains file separator");
            }
            File logFile = new File(global.getLancetDir(), "log_" + fileName);
            Files.createParentDirs(logFile);
            Log.setImpl(FileLoggerImpl.of(logFile.getAbsolutePath()));
        }
    }
}
