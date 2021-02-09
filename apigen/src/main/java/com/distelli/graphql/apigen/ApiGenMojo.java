package com.distelli.graphql.apigen;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.net.URL;
import java.net.URLClassLoader;

import org.apache.maven.model.Resource;

import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.Arrays;

@Mojo(name = "apigen",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE)
@Execute(goal = "apigen")
public class ApiGenMojo extends AbstractMojo {

    private static final String SCHEMA_LOCATION = "classpath*:graphql-apigen-schema/*.graphql{,s}";

    private final Log logger = getLog();

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(name = "sourceDirectory", defaultValue = "schema")
    private File sourceDirectory;

    @Parameter(name = "outputDirectory", defaultValue = "target/generated-sources/apigen")
    private File outputDirectory;

    @Parameter(name = "guiceModuleName")
    private String guiceModuleName;

    @Parameter(name = "defaultPackageName", defaultValue = "com.graphql.generated")
    private String defaultPackageName;

    @Parameter(name = "generateOnlyPojo", defaultValue = "true")
    private Boolean generateOnlyPojo;

    @Override
    public void execute() {
        try {
            sourceDirectory = makeAbsolute(sourceDirectory);
            outputDirectory = makeAbsolute(outputDirectory);

            if (sourceDirectory.exists()) {
                logger.debug("Running ApiGen");
                logger.debug(String.format("sourceDirectory=%s", sourceDirectory));
                logger.debug(String.format("outputDirectory=%s", outputDirectory));

                setupApigen(getCompileClassLoader());

                Resource schemaResource = getResource();

                project.addResource(schemaResource);
                project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
            }
        } catch (Exception e) {
            String msg = e.getMessage();

            if (msg == null) {
                msg = e.getClass().getName();
            }

            logger.error(String.format("%s when trying to build sources from GraphQL schema.", msg), e);
        }
    }

    private void setupApigen(ClassLoader loader) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);

        ApiGen apiGen = new ApiGen.Builder()
                .withOutputDirectory(outputDirectory.toPath())
                .withGuiceModuleName(guiceModuleName)
                .withDefaultPackageName(defaultPackageName)
                .build();

        Arrays.stream(resolver.getResources(SCHEMA_LOCATION)).forEach(resource -> {
            try {
                URL url = resource.getURL();
                logger.debug("Processing " + url);
                apiGen.addForReference(url);
            } catch (IOException ioe) {
                logger.error("I/O error while processing URL");
            }
        });

        findGraphql(sourceDirectory, apiGen::addForGeneration);

        apiGen.generate();
    }

    private Resource getResource() {
        Resource resource = new Resource();

        resource.setTargetPath("graphql-apigen-schema");
        resource.setFiltering(false);
        resource.setIncludes(Arrays.asList("*.graphqls", "*.graphql"));
        resource.setDirectory(sourceDirectory.toString());

        return resource;
    }

    private File makeAbsolute(File in) {
        if (in.isAbsolute()) {
            return in;
        }

        return new File(project.getBasedir(), in.toString());
    }

    private ClassLoader getCompileClassLoader() throws Exception {
        List<URL> urls = new ArrayList<>();

        String ignored = project.getBuild().getOutputDirectory();
        logger.debug(String.format("ignore=%s", ignored));

        project.getCompileClasspathElements().forEach(path -> {
            if (!path.equals(ignored)) {
                File file = makeAbsolute(new File(path));
                String name = file.toString();

                if (file.isDirectory() || !file.exists()) {
                    name = name + "/";
                }

                try {
                    URL url = new URL("file", null, name);
                    urls.add(url);
                    logger.debug(String.format("classpath += %s", url));
                } catch (MalformedURLException e) {
                    logger.error(String.format("Failed to construct URL for file %s", name), e);
                }
            }
        });

        return new URLClassLoader((URL[]) urls.toArray());
    }

    private interface VisitPath {
        void visit(Path path) throws IOException;
    }

    private void findGraphql(File rootDir, VisitPath visitPath) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.graphql{,s}");

        Files.walkFileTree(rootDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (matcher.matches(file)) {
                    logger.debug("Processing " + file);
                    visitPath.visit(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
