package org.infinispan.protostream.integrationtests.processor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.tools.FileObject;
import javax.tools.JavaFileObject;

import org.infinispan.protostream.annotations.impl.processor.AutoProtoSchemaBuilderAnnotationProcessor;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.io.Resources;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;

/**
 * @author anistor@redhat.com
 * @since 4.3.4
 */
public class AnnotationProcessorCompilationTest {

   @Test
   public void testBasic() {
      Compilation compilation = compile(
            "org/infinispan/protostream/integrationtests/processor/TestMessage.java",
            "org/infinispan/protostream/integrationtests/processor/AbstractFirstInitializer.java",
            "org/infinispan/protostream/integrationtests/processor/DependentInitializer.java"
      );
      assertThat(compilation).succeeded();
      assertTrue(compilation.generatedFile(SOURCE_OUTPUT, "test_basic_stuff/FirstInitializer.java").isPresent());
      assertTrue(compilation.generatedFile(SOURCE_OUTPUT, "test_basic_stuff_dependent/DependentInitializerImpl.java").isPresent());
      assertThat(compilation).hadWarningContaining("Code generated by @AutoProtoSchemaBuilder processor will override your test_basic_stuff_dependent.DependentInitializer.getProtoFileName method.");
      assertTrue(compilation.generatedFile(CLASS_OUTPUT, "first_initializer/FirstInitializer.proto").isPresent());
      assertTrue(compilation.generatedFile(CLASS_OUTPUT, "DependentInitializer.proto").isPresent());

      Optional<JavaFileObject> serviceFile = compilation.generatedFile(CLASS_OUTPUT, "META-INF/services/org.infinispan.protostream.SerializationContextInitializer");
      assertTrue(serviceFile.isPresent());
      assertFileContains(serviceFile, "test_basic_stuff.FirstInitializer");
      assertFileContains(serviceFile, "test_basic_stuff_dependent.DependentInitializerImpl");
   }

   @Test
   public void testBadSchemaFileName() {
      Compilation compilation = compile("org/infinispan/protostream/integrationtests/processor/BadSchemaFileName.java");
      assertThat(compilation).succeeded();
      assertThat(compilation).hadWarningContaining("@AutoProtoSchemaBuilder.schemaFileName should end with '.proto'");
   }

   @Test
   public void testBadBasePackages() {
      Compilation compilation = compile("org/infinispan/protostream/integrationtests/processor/BadBasePackages.java");
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@AutoProtoSchemaBuilder.value contains an invalid package name : \"test!!!\"");
   }

   @Test
   public void testNestedEnum() {
      Compilation compilation = compile("org/infinispan/protostream/integrationtests/processor/TestEnumInitializer.java");
      assertThat(compilation).succeededWithoutWarnings();
      assertTrue(compilation.generatedFile(SOURCE_OUTPUT, "test_enum_initializer/TestEnumInitializerImpl.java").isPresent());
   }

   @Test
   public void testDependsOnCycle() {
      Compilation compilation = compile("org/infinispan/protostream/integrationtests/processor/DependsOnCycle.java");
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Illegal recursive dependency on test_depends_on_cycle.DependsOnCycle");
   }

   @Test
   public void testIncludeExcludeOverlap() {
      Compilation compilation = compile("org/infinispan/protostream/integrationtests/processor/IncludeExcludeOverlap.java");
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@AutoProtoSchemaBuilder.includeClasses and @AutoProtoSchemaBuilder.excludeClasses are mutually exclusive");
   }

   @Test
   public void testIncludeClassesVsBasePackagesConflict() {
      Compilation compilation = compile("org/infinispan/protostream/integrationtests/processor/IncludeClassesVsBasePackagesConflict.java");
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@AutoProtoSchemaBuilder.includeClasses and @AutoProtoSchemaBuilder.value/basePackages are mutually exclusive");
   }

   @Test
   public void testExcludeClassesVsBasePackagesConflict() {
      Compilation compilation = compile("org/infinispan/protostream/integrationtests/processor/ExcludeClassesVsBasePackagesConflict.java");
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@AutoProtoSchemaBuilder.excludeClasses and @AutoProtoSchemaBuilder.value/basePackages are conflicting. Class 'test_exclude_classes_vs_base_packages_conflict.ExcludeClassesVsBasePackagesConflict.Msg' must belong to a base package.");
   }

   @Test
   public void testDiscoveryWithoutAutoImport() {
      Compilation compilation = compile("org/infinispan/protostream/integrationtests/processor/DiscoveryWithoutAutoImport.java");
      assertThat(compilation).hadErrorContaining("Found a reference to class"
            + " test_discovery_without_auto_import.InnerMessage1 which was not explicitly included by"
            + " @AutoProtoSchemaBuilder and the combination of relevant attributes"
            + " (basePackages, includeClasses, excludeClasses, autoImportClasses) do not allow it to be included.");
   }

   @Test
   public void testNestedDiscoveryWithoutAutoImport() {
      Compilation compilation = compile("org/infinispan/protostream/integrationtests/processor/NestedDiscoveryWithoutAutoImport.java");
      assertThat(compilation).succeededWithoutWarnings();
      assertTrue(compilation.generatedFile(SOURCE_OUTPUT, "test_nested_discovery_without_auto_import/NestedDiscoveryWithoutAutoImportImpl.java").isPresent());
      assertTrue(compilation.generatedFile(SOURCE_OUTPUT, "test_nested_discovery_without_auto_import/NestedDiscoveryWithoutAutoImport2Impl.java").isPresent());
      assertTrue(compilation.generatedFile(SOURCE_OUTPUT, "test_nested_discovery_without_auto_import/NestedDiscoveryWithoutAutoImport3Impl.java").isPresent());

      Optional<JavaFileObject> schemaFile = compilation.generatedFile(CLASS_OUTPUT, "NestedDiscoveryWithoutAutoImport.proto");
      assertTrue(schemaFile.isPresent());
      assertFileContains(schemaFile, "message OuterMessage2");
      assertFileContains(schemaFile, "message InnerMessage2");
      assertFileContains(schemaFile, "baseField1");
      assertFileContains(schemaFile, "message OuterMessage3");
      assertFileDoesNotContain(schemaFile, "message InnerMessage3");

      schemaFile = compilation.generatedFile(CLASS_OUTPUT, "NestedDiscoveryWithoutAutoImport2.proto");
      assertTrue(schemaFile.isPresent());
      assertFileContains(schemaFile, "message OuterMessage4");
      assertFileContains(schemaFile, "message InnerMessage4");

      schemaFile = compilation.generatedFile(CLASS_OUTPUT, "NestedDiscoveryWithoutAutoImport3.proto");
      assertTrue(schemaFile.isPresent());
      assertFileContains(schemaFile, "message OuterMessage4");
      assertFileContains(schemaFile, "message InnerMessage4");
   }

   /**
    * Asserts that the file contains a given expected string.
    */
   static void assertFileContains(Optional<? extends FileObject> file, String string) {
      assertFileContains(file.orElse(null), string);
   }

   /**
    * Asserts that the file does not contain a given string.
    */
   static void assertFileDoesNotContain(Optional<? extends FileObject> file, String string) {
      assertFileDoesNotContain(file.orElse(null), string);
   }

   /**
    * Asserts that the file contains a given expected string.
    */
   static void assertFileContains(FileObject file, String string) {
      assertTrue("File " + file.getName() + " is expected to contain '"
            + string + "' but it doesn't.", checkFileContainsString(file, string));
   }

   /**
    * Asserts that the file does not contain a given string.
    */
   static void assertFileDoesNotContain(FileObject file, String string) {
      assertFalse("File " + file.getName() + " is not expected to contain '"
            + string + "' but it does.", checkFileContainsString(file, string));
   }

   private static boolean checkFileContainsString(FileObject file, String string) {
      if (file == null) {
         throw new IllegalArgumentException("The file argument must not be null");
      }
      if (string == null || string.length() == 0) {
         throw new IllegalArgumentException("The string argument must not be null or empty");
      }

      String src;
      try {
         src = file.getCharContent(true).toString();
      } catch (IOException ioe) {
         throw new UncheckedIOException(ioe);
      }

      return src.contains(string);
   }

   /**
    * Compiles several Java source files that are represented by resource files form classpath.
    */
   private static Compilation compile(String... resourceNames) {
      if (resourceNames == null || resourceNames.length == 0) {
         throw new IllegalArgumentException("resourceNames argument must not be null or empty");
      }

      List<JavaFileObject> files = new ArrayList<>(resourceNames.length);
      for (String resourceName : resourceNames) {
         files.add(createJavaFileObject(resourceName));
      }

      return javac()
            .withProcessors(new AutoProtoSchemaBuilderAnnotationProcessor())
            .compile(files);
   }

   /**
    * Loads a resource file from classpath that is expected to be a Java source file and creates a JavaFileObject.
    */
   private static JavaFileObject createJavaFileObject(String resourceName) {
      String fullyQualifiedName;
      String suffix = ".java";
      if (resourceName.endsWith(suffix)) {
         fullyQualifiedName = resourceName.substring(0, resourceName.length() - suffix.length());
      } else {
         throw new IllegalArgumentException("The resource must be a Java source file and the file name must end with '.java' suffix");
      }
      fullyQualifiedName = fullyQualifiedName.replace('/', '.');

      String source;
      try {
         source = Resources.asByteSource(Resources.getResource(resourceName))
               .asCharSource(StandardCharsets.UTF_8)
               .read();
      } catch (IOException ioe) {
         throw new UncheckedIOException(ioe);
      }
      return JavaFileObjects.forSourceString(fullyQualifiedName, source);
   }
}
