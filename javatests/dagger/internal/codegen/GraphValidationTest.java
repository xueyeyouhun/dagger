/*
 * Copyright (C) 2014 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static dagger.internal.codegen.Compilers.daggerCompiler;
import static dagger.internal.codegen.NonNullableRequestForNullableBindingValidation.nullableToNonNullable;
import static dagger.internal.codegen.TestUtils.message;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GraphValidationTest {
  private static final JavaFileObject NULLABLE =
      JavaFileObjects.forSourceLines(
          "test.Nullable", // force one-string-per-line format
          "package test;",
          "",
          "public @interface Nullable {}");

  @Test public void componentOnConcreteClass() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.MyComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface MyComponent {",
        "  Foo getFoo();",
        "}");
    JavaFileObject injectable = JavaFileObjects.forSourceLines("test.Foo",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "class Foo {",
        "  @Inject Foo(Bar bar) {}",
        "}");
    JavaFileObject nonInjectable = JavaFileObjects.forSourceLines("test.Bar",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "interface Bar {}");
    Compilation compilation = daggerCompiler().compile(component, injectable, nonInjectable);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.Bar cannot be provided without an @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface MyComponent");
  }

  @Test
  public void componentProvisionWithNoDependencyChain_unqualified() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "",
            "  @Component()",
            "  interface AComponent {",
            "    A getA();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "[Dagger/MissingBinding] test.TestClass.A cannot be provided "
                + "without an @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  public void componentProvisionWithNoDependencyChain_qualified() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Qualifier;",
            "",
            "final class TestClass {",
            "  @Qualifier @interface Q {}",
            "  interface A {}",
            "",
            "  @Component()",
            "  interface AComponent {",
            "    @Q A qualifiedA();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "[Dagger/MissingBinding] @test.TestClass.Q test.TestClass.A cannot be provided "
                + "without an @Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test public void constructorInjectionWithoutAnnotation() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class TestClass {",
        "  static class A {",
        "    A() {}",
        "  }",
        "",
        "  @Component()",
        "  interface AComponent {",
        "    A getA();",
        "  }",
        "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.TestClass.A cannot be provided without an @Inject constructor or an "
                + "@Provides-annotated method.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test public void membersInjectWithoutProvision() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class TestClass {",
        "  static class A {",
        "    @Inject A() {}",
        "  }",
        "",
        "  static class B {",
        "    @Inject A a;",
        "  }",
        "",
        "  @Component()",
        "  interface AComponent {",
        "    B getB();",
        "  }",
        "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.TestClass.B cannot be provided without an @Inject constructor or an "
                + "@Provides-annotated method. This type supports members injection but cannot be "
                + "implicitly provided.")
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  public void membersInjectDependsOnUnboundedType() {
    JavaFileObject injectsUnboundedType =
        JavaFileObjects.forSourceLines(
            "test.InjectsUnboundedType",
            "package test;",
            "",
            "import dagger.MembersInjector;",
            "import java.util.ArrayList;",
            "import javax.inject.Inject;",
            "",
            "class InjectsUnboundedType {",
            "  @Inject MembersInjector<ArrayList<?>> listInjector;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void injectsUnboundedType(InjectsUnboundedType injects);",
            "}");

    Compilation compilation = daggerCompiler().compile(injectsUnboundedType, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Cannot inject members into types with unbounded type arguments: "
                    + "java.util.ArrayList<?>",
                "    dagger.MembersInjector<java.util.ArrayList<?>> is injected at",
                "        test.InjectsUnboundedType.listInjector",
                "    test.InjectsUnboundedType is injected at",
                "        test.TestComponent.injectsUnboundedType(test.InjectsUnboundedType)"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Ignore // TODO(b/77220343)
  @Test
  public void membersInjectPrimitive() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(int primitive);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into int")
        .inFile(component)
        .onLineContaining("void inject(int primitive);");
  }

  @Ignore // TODO(b/77220343)
  @Test
  public void membersInjectArray() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(Object[] array);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into java.lang.Object[]")
        .inFile(component)
        .onLineContaining("void inject(Object[] array);");
  }

  @Ignore // TODO(b/77220343)
  @Test
  public void membersInjectorOfArray() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MembersInjector;",
            "",
            "@Component",
            "interface TestComponent {",
            "  MembersInjector<Object[]> objectArrayInjector();",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Cannot inject members into java.lang.Object[]")
        .inFile(component)
        .onLineContaining("objectArrayInjector();");
  }

  @Test
  public void membersInjectRawType() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(Set rawSet);",
            "}");
    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("Cannot inject members into raw type java.util.Set");
  }

  @Test
  public void staticFieldInjection() {
    JavaFileObject injected =
        JavaFileObjects.forSourceLines(
            "test.Injected",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Injected {",
            "  @Inject static Object object;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  void inject(Injected injected);",
            "}");
    Compilation compilation = daggerCompiler().compile(injected, component);
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("static fields").inFile(injected).onLine(6);
  }

  @Test public void cyclicDependency() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class Outer {",
        "  static class A {",
        "    @Inject A(C cParam) {}",
        "  }",
        "",
        "  static class B {",
        "    @Inject B(A aParam) {}",
        "  }",
        "",
        "  static class C {",
        "    @Inject C(B bParam) {}",
        "  }",
        "",
        "  @Component()",
        "  interface CComponent {",
        "    C getC();",
        "  }",
        "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.A.<init>(cParam)",
                "    test.Outer.A is injected at",
                "        test.Outer.B.<init>(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C.<init>(bParam)",
                "    test.Outer.C is provided at",
                "        test.Outer.CComponent.getC()"))
        .inFile(component)
        .onLineContaining("interface CComponent");
  }

  @Test public void cyclicDependencyNotIncludingEntryPoint() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  static class A {",
            "    @Inject A(C cParam) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(A aParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(B bParam) {}",
            "  }",
            "",
            "  static class D {",
            "    @Inject D(C cParam) {}",
            "  }",
            "",
            "  @Component()",
            "  interface DComponent {",
            "    D getD();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.A.<init>(cParam)",
                "    test.Outer.A is injected at",
                "        test.Outer.B.<init>(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C.<init>(bParam)",
                "    test.Outer.C is injected at",
                "        test.Outer.D.<init>(cParam)",
                "    test.Outer.D is provided at",
                "        test.Outer.DComponent.getD()"))
        .inFile(component)
        .onLineContaining("interface DComponent");
  }

  @Test
  public void cyclicDependencyNotBrokenByMapBinding() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.MapKey;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import java.util.Map;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  static class A {",
            "    @Inject A(Map<String, C> cMap) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(A aParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(B bParam) {}",
            "  }",
            "",
            "  @Component(modules = CModule.class)",
            "  interface CComponent {",
            "    C getC();",
            "  }",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides @IntoMap",
            "    @StringKey(\"C\")",
            "    static C c(C c) {",
            "      return c;",
            "    }",
            "  }",
            "",
            "  @MapKey",
            "  @interface StringKey {",
            "    String value();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.CModule.c(c)",
                "    java.util.Map<java.lang.String,test.Outer.C> is injected at",
                "        test.Outer.A.<init>(cMap)",
                "    test.Outer.A is injected at",
                "        test.Outer.B.<init>(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C.<init>(bParam)",
                "    test.Outer.C is provided at",
                "        test.Outer.CComponent.getC()"))
        .inFile(component)
        .onLineContaining("interface CComponent");
  }

  @Test
  public void cyclicDependencyWithSetBinding() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  static class A {",
            "    @Inject A(Set<C> cSet) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(A aParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(B bParam) {}",
            "  }",
            "",
            "  @Component(modules = CModule.class)",
            "  interface CComponent {",
            "    C getC();",
            "  }",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides @IntoSet",
            "    static C c(C c) {",
            "      return c;",
            "    }",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.CModule.c(c)",
                "    java.util.Set<test.Outer.C> is injected at",
                "        test.Outer.A.<init>(cSet)",
                "    test.Outer.A is injected at",
                "        test.Outer.B.<init>(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C.<init>(bParam)",
                "    test.Outer.C is provided at",
                "        test.Outer.CComponent.getC()"))
        .inFile(component)
        .onLineContaining("interface CComponent");
  }

  @Test
  public void falsePositiveCyclicDependencyIndirectionDetected() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class Outer {",
            "  static class A {",
            "    @Inject A(C cParam) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(A aParam) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject C(B bParam) {}",
            "  }",
            "",
            "  static class D {",
            "    @Inject D(Provider<C> cParam) {}",
            "  }",
            "",
            "  @Component()",
            "  interface DComponent {",
            "    D getD();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.Outer.C is injected at",
                "        test.Outer.A.<init>(cParam)",
                "    test.Outer.A is injected at",
                "        test.Outer.B.<init>(aParam)",
                "    test.Outer.B is injected at",
                "        test.Outer.C.<init>(bParam)",
                "    javax.inject.Provider<test.Outer.C> is injected at",
                "        test.Outer.D.<init>(cParam)",
                "    test.Outer.D is provided at",
                "        test.Outer.DComponent.getD()"))
        .inFile(component)
        .onLineContaining("interface DComponent");
  }

  @Test
  public void cyclicDependencyInSubcomponents() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child.Builder child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = CycleModule.class)",
            "interface Child {",
            "  Grandchild.Builder grandchild();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "}");
    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Grandchild {",
            "  String entry();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Grandchild build();",
            "  }",
            "}");
    JavaFileObject cycleModule =
        JavaFileObjects.forSourceLines(
            "test.CycleModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class CycleModule {",
            "  @Provides static Object object(String string) {",
            "    return string;",
            "  }",
            "",
            "  @Provides static String string(Object object) {",
            "    return object.toString();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(parent, child, grandchild, cycleModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    java.lang.String is injected at",
                "        test.CycleModule.object(string)",
                "    java.lang.Object is injected at",
                "        test.CycleModule.string(object)",
                "    java.lang.String is provided at",
                "        test.Grandchild.entry()"))
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  public void cyclicDependencyInSubcomponentsWithChildren() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "test.Parent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface Parent {",
            "  Child.Builder child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = CycleModule.class)",
            "interface Child {",
            "  String entry();",
            "",
            "  Grandchild grandchild();",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Child build();",
            "  }",
            "}");
    // Grandchild has no entry point that depends on the cycle. http://b/111317986
    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "test.Grandchild",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Grandchild {",
            "",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Grandchild build();",
            "  }",
            "}");
    JavaFileObject cycleModule =
        JavaFileObjects.forSourceLines(
            "test.CycleModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class CycleModule {",
            "  @Provides static Object object(String string) {",
            "    return string;",
            "  }",
            "",
            "  @Provides static String string(Object object) {",
            "    return object.toString();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(parent, child, grandchild, cycleModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    java.lang.String is injected at",
                "        test.CycleModule.object(string)",
                "    java.lang.Object is injected at",
                "        test.CycleModule.string(object)",
                "    java.lang.String is provided at",
                "        test.Child.entry() [test.Parent → test.Child]"))
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  public void circularBindsMethods() {
    JavaFileObject qualifier =
        JavaFileObjects.forSourceLines(
            "test.SomeQualifier",
            "package test;",
            "",
            "import javax.inject.Qualifier;",
            "",
            "@Qualifier @interface SomeQualifier {}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Binds abstract Object bindUnqualified(@SomeQualifier Object qualified);",
            "  @Binds @SomeQualifier abstract Object bindQualified(Object unqualified);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Object unqualified();",
            "}");

    Compilation compilation = daggerCompiler().compile(qualifier, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    java.lang.Object is injected at",
                "        test.TestModule.bindQualified(unqualified)",
                "    @test.SomeQualifier java.lang.Object is injected at",
                "        test.TestModule.bindUnqualified(qualified)",
                "    java.lang.Object is provided at",
                "        test.TestComponent.unqualified()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void selfReferentialBinds() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Binds abstract Object bindToSelf(Object sameKey);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Object selfReferential();",
            "}");

    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    java.lang.Object is injected at",
                "        test.TestModule.bindToSelf(sameKey)",
                "    java.lang.Object is provided at",
                "        test.TestComponent.selfReferential()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void cycleFromMembersInjectionMethod_WithSameKeyAsMembersInjectionMethod() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class A {",
            "  @Inject A() {}",
            "  @Inject B b;",
            "}");
    JavaFileObject b =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "class B {",
            "  @Inject B() {}",
            "  @Inject A a;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.CycleComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface CycleComponent {",
            "  void inject(A a);",
            "}");

    Compilation compilation = daggerCompiler().compile(a, b, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "Found a dependency cycle:",
                "    test.B is injected at",
                "        test.A.b",
                "    test.A is injected at",
                "        test.B.a",
                "    test.B is injected at",
                "        test.A.b",
                "    test.A is injected at",
                "        test.CycleComponent.inject(test.A)"))
        .inFile(component)
        .onLineContaining("interface CycleComponent");
  }

  @Test
  public void longCycleMaskedByShortBrokenCycles() {
    JavaFileObject cycles =
        JavaFileObjects.forSourceLines(
            "test.Cycles",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "import dagger.Component;",
            "",
            "final class Cycles {",
            "  static class A {",
            "    @Inject A(Provider<A> aProvider, B b) {}",
            "  }",
            "",
            "  static class B {",
            "    @Inject B(Provider<B> bProvider, A a) {}",
            "  }",
            "",
            "  @Component",
            "  interface C {",
            "    A a();",
            "  }",
            "}");
    Compilation compilation = daggerCompiler().compile(cycles);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("Found a dependency cycle:")
        .inFile(cycles)
        .onLineContaining("interface C");
  }

  @Test
  public void missingBindingWithSameKeyAsMembersInjectionMethod() {
    JavaFileObject self =
        JavaFileObjects.forSourceLines(
            "test.Self",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "class Self {",
            "  @Inject Provider<Self> selfProvider;",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.SelfComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface SelfComponent {",
            "  void inject(Self target);",
            "}");

    Compilation compilation = daggerCompiler().compile(self, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("test.Self cannot be provided without an @Inject constructor")
        .inFile(component)
        .onLineContaining("interface SelfComponent");
  }

  @Test
  public void genericInjectClassWithWildcardDependencies() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface TestComponent {",
            "  Foo<? extends Number> foo();",
            "}");
    JavaFileObject foo =
        JavaFileObjects.forSourceLines(
            "test.Foo",
            "package test;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Foo<T> {",
            "  @Inject Foo(T t) {}",
            "}");
    Compilation compilation = daggerCompiler().compile(component, foo);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "test.Foo<? extends java.lang.Number> cannot be provided "
                + "without an @Provides-annotated method");
  }

  @Test public void duplicateExplicitBindings_ProvidesAndComponentProvision() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "final class Outer {",
        "  interface A {}",
        "",
        "  interface B {}",
        "",
        "  @Module",
        "  static class AModule {",
        "    @Provides String provideString() { return \"\"; }",
        "    @Provides A provideA(String s) { return new A() {}; }",
        "  }",
        "",
        "  @Component(modules = AModule.class)",
        "  interface Parent {",
        "    A getA();",
        "  }",
        "",
        "  @Module",
        "  static class BModule {",
        "    @Provides B provideB(A a) { return new B() {}; }",
        "  }",
        "",
        "  @Component(dependencies = Parent.class, modules = { BModule.class, AModule.class})",
        "  interface Child {",
        "    B getB();",
        "  }",
        "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Outer.A is bound multiple times:",
                "    @Provides test.Outer.A test.Outer.AModule.provideA(String)",
                "    test.Outer.A test.Outer.Parent.getA()"))
        .inFile(component)
        .onLineContaining("interface Child");
  }

  @Test public void duplicateExplicitBindings_TwoProvidesMethods() {
    JavaFileObject component = JavaFileObjects.forSourceLines("test.Outer",
        "package test;",
        "",
        "import dagger.Component;",
        "import dagger.Module;",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "final class Outer {",
        "  interface A {}",
        "",
        "  @Module",
        "  static class Module1 {",
        "    @Provides A provideA1() { return new A() {}; }",
        "  }",
        "",
        "  @Module",
        "  static class Module2 {",
        "    @Provides String provideString() { return \"\"; }",
        "    @Provides A provideA2(String s) { return new A() {}; }",
        "  }",
        "",
        "  @Component(modules = { Module1.class, Module2.class})",
        "  interface TestComponent {",
        "    A getA();",
        "  }",
        "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Outer.A is bound multiple times:",
                "    @Provides test.Outer.A test.Outer.Module1.provideA1()",
                "    @Provides test.Outer.A test.Outer.Module2.provideA2(String)"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void duplicateExplicitBindings_ProvidesVsBinds() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  static final class B implements A {",
            "    @Inject B() {}",
            "  }",
            "",
            "  @Module",
            "  static class Module1 {",
            "    @Provides A provideA1() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static abstract class Module2 {",
            "    @Binds abstract A bindA2(B b);",
            "  }",
            "",
            "  @Component(modules = { Module1.class, Module2.class})",
            "  interface TestComponent {",
            "    A getA();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Outer.A is bound multiple times:",
                "    @Provides test.Outer.A test.Outer.Module1.provideA1()",
                "    @Binds test.Outer.A test.Outer.Module2.bindA2(test.Outer.B)"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void duplicateExplicitBindings_multibindingsAndExplicitSets() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.HashSet;",
            "import java.util.Set;",
            "import javax.inject.Qualifier;",
            "",
            "final class Outer {",
            "  @Qualifier @interface SomeQualifier {}",
            "",
            "  @Module",
            "  abstract static class TestModule1 {",
            "    @Provides @IntoSet static String stringSetElement() { return \"\"; }",
            "",
            "    @Binds",
            "    @IntoSet abstract String bindStringSetElement(@SomeQualifier String value);",
            "",
            "    @Provides @SomeQualifier",
            "    static String provideSomeQualifiedString() { return \"\"; }",
            "  }",
            "",
            "  @Module",
            "  static class TestModule2 {",
            "    @Provides Set<String> stringSet() { return new HashSet<String>(); }",
            "  }",
            "",
            "  @Component(modules = { TestModule1.class, TestModule2.class })",
            "  interface TestComponent {",
            "    Set<String> getStringSet();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Set<java.lang.String> has incompatible bindings or declarations:",
                "    Set bindings and declarations:",
                "        @Binds @dagger.multibindings.IntoSet String "
                    + "test.Outer.TestModule1.bindStringSetElement(@test.Outer.SomeQualifier "
                    + "String)",
                "        @Provides @dagger.multibindings.IntoSet String "
                    + "test.Outer.TestModule1.stringSetElement()",
                "    Unique bindings and declarations:",
                "        @Provides Set<String> test.Outer.TestModule2.stringSet()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void duplicateExplicitBindings_multibindingsAndExplicitMaps() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.StringKey;",
            "import java.util.HashMap;",
            "import java.util.Map;",
            "import javax.inject.Qualifier;",
            "",
            "final class Outer {",
            "  @Qualifier @interface SomeQualifier {}",
            "",
            "  @Module",
            "  abstract static class TestModule1 {",
            "    @Provides @IntoMap",
            "    @StringKey(\"foo\")",
            "    static String stringMapEntry() { return \"\"; }",
            "",
            "    @Binds @IntoMap @StringKey(\"bar\")",
            "    abstract String bindStringMapEntry(@SomeQualifier String value);",
            "",
            "    @Provides @SomeQualifier",
            "    static String provideSomeQualifiedString() { return \"\"; }",
            "  }",
            "",
            "  @Module",
            "  static class TestModule2 {",
            "    @Provides Map<String, String> stringMap() {",
            "      return new HashMap<String, String>();",
            "    }",
            "  }",
            "",
            "  @Component(modules = { TestModule1.class, TestModule2.class })",
            "  interface TestComponent {",
            "    Map<String, String> getStringMap();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Map<java.lang.String,java.lang.String> has incompatible bindings "
                    + "or declarations:",
                "    Map bindings and declarations:",
                "        @Binds @dagger.multibindings.IntoMap "
                    + "@dagger.multibindings.StringKey(\"bar\") String"
                    + " test.Outer.TestModule1.bindStringMapEntry(@test.Outer.SomeQualifier "
                    + "String)",
                "        @Provides @dagger.multibindings.IntoMap "
                    + "@dagger.multibindings.StringKey(\"foo\") String"
                    + " test.Outer.TestModule1.stringMapEntry()",
                "    Unique bindings and declarations:",
                "        @Provides Map<String,String> test.Outer.TestModule2.stringMap()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void duplicateExplicitBindings_UniqueBindingAndMultibindingDeclaration_Set() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.HashSet;",
            "import java.util.Set;",
            "",
            "final class Outer {",
            "  @Module",
            "  abstract static class TestModule1 {",
            "    @Multibinds abstract Set<String> stringSet();",
            "  }",
            "",
            "  @Module",
            "  static class TestModule2 {",
            "    @Provides Set<String> stringSet() { return new HashSet<String>(); }",
            "  }",
            "",
            "  @Component(modules = { TestModule1.class, TestModule2.class })",
            "  interface TestComponent {",
            "    Set<String> getStringSet();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Set<java.lang.String> has incompatible bindings or declarations:",
                "    Set bindings and declarations:",
                "        @dagger.multibindings.Multibinds Set<String> "
                    + "test.Outer.TestModule1.stringSet()",
                "    Unique bindings and declarations:",
                "        @Provides Set<String> test.Outer.TestModule2.stringSet()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void duplicateExplicitBindings_UniqueBindingAndMultibindingDeclaration_Map() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.Multibinds;",
            "import java.util.HashMap;",
            "import java.util.Map;",
            "",
            "final class Outer {",
            "  @Module",
            "  abstract static class TestModule1 {",
            "    @Multibinds abstract Map<String, String> stringMap();",
            "  }",
            "",
            "  @Module",
            "  static class TestModule2 {",
            "    @Provides Map<String, String> stringMap() {",
            "      return new HashMap<String, String>();",
            "    }",
            "  }",
            "",
            "  @Component(modules = { TestModule1.class, TestModule2.class })",
            "  interface TestComponent {",
            "    Map<String, String> getStringMap();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Map<java.lang.String,java.lang.String> has incompatible bindings "
                    + "or declarations:",
                "    Map bindings and declarations:",
                "        @dagger.multibindings.Multibinds Map<String,String> "
                    + "test.Outer.TestModule1.stringMap()",
                "    Unique bindings and declarations:",
                "        @Provides Map<String,String> test.Outer.TestModule2.stringMap()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test public void duplicateBindings_TruncateAfterLimit() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.Outer",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "final class Outer {",
            "  interface A {}",
            "",
            "  @Module",
            "  static class Module01 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module02 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module03 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module04 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module05 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module06 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module07 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module08 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module09 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module10 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module11 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Module",
            "  static class Module12 {",
            "    @Provides A provideA() { return new A() {}; }",
            "  }",
            "",
            "  @Component(modules = {",
            "    Module01.class,",
            "    Module02.class,",
            "    Module03.class,",
            "    Module04.class,",
            "    Module05.class,",
            "    Module06.class,",
            "    Module07.class,",
            "    Module08.class,",
            "    Module09.class,",
            "    Module10.class,",
            "    Module11.class,",
            "    Module12.class",
            "  })",
            "  interface TestComponent {",
            "    A getA();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.Outer.A is bound multiple times:",
                "    @Provides test.Outer.A test.Outer.Module01.provideA()",
                "    @Provides test.Outer.A test.Outer.Module02.provideA()",
                "    @Provides test.Outer.A test.Outer.Module03.provideA()",
                "    @Provides test.Outer.A test.Outer.Module04.provideA()",
                "    @Provides test.Outer.A test.Outer.Module05.provideA()",
                "    @Provides test.Outer.A test.Outer.Module06.provideA()",
                "    @Provides test.Outer.A test.Outer.Module07.provideA()",
                "    @Provides test.Outer.A test.Outer.Module08.provideA()",
                "    @Provides test.Outer.A test.Outer.Module09.provideA()",
                "    @Provides test.Outer.A test.Outer.Module10.provideA()",
                "    and 2 others"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test public void longChainOfDependencies() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestClass",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Lazy;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "import javax.inject.Named;",
            "import javax.inject.Provider;",
            "",
            "final class TestClass {",
            "  interface A {}",
            "",
            "  static class B {",
            "    @Inject B(A a) {}",
            "  }",
            "",
            "  static class C {",
            "    @Inject B b;",
            "    @Inject C(X x) {}",
            "  }",
            "",
            "  interface D { }",
            "",
            "  static class DImpl implements D {",
            "    @Inject DImpl(C c, B b) {}",
            "  }",
            "",
            "  static class X {",
            "    @Inject X() {}",
            "  }",
            "",
            "  @Module",
            "  static class DModule {",
            "    @Provides @Named(\"slim shady\") D d(X x1, DImpl impl, X x2) { return impl; }",
            "  }",
            "",
            "  @Component(modules = { DModule.class })",
            "  interface AComponent {",
            "    @Named(\"slim shady\") D getFoo();",
            "    C injectC(C c);",
            "    Provider<C> cProvider();",
            "    Lazy<C> lazyC();",
            "    Provider<Lazy<C>> lazyCProvider();",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.TestClass.A cannot be provided without an @Provides-annotated method.",
                "    test.TestClass.A is injected at",
                "        test.TestClass.B.<init>(a)",
                "    test.TestClass.B is injected at",
                "        test.TestClass.C.b",
                "    test.TestClass.C is injected at",
                "        test.TestClass.AComponent.injectC(test.TestClass.C)",
                "The following other entry points also depend on it:",
                "    test.TestClass.AComponent.getFoo()",
                "    test.TestClass.AComponent.cProvider()",
                "    test.TestClass.AComponent.lazyC()",
                "    test.TestClass.AComponent.lazyCProvider()"))
        .inFile(component)
        .onLineContaining("interface AComponent");
  }

  @Test
  public void bindsMethodAppearsInTrace() {
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "TestComponent",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestInterface testInterface();",
            "}");
    JavaFileObject interfaceFile =
        JavaFileObjects.forSourceLines("TestInterface", "interface TestInterface {}");
    JavaFileObject implementationFile =
        JavaFileObjects.forSourceLines(
            "TestImplementation",
            "import javax.inject.Inject;",
            "",
            "final class TestImplementation implements TestInterface {",
            "  @Inject TestImplementation(String missingBinding) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "TestModule",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds abstract TestInterface bindTestInterface(TestImplementation implementation);",
            "}");

    Compilation compilation =
        daggerCompiler().compile(component, module, interfaceFile, implementationFile);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.String cannot be provided without an @Inject constructor or an "
                    + "@Provides-annotated method.",
                "    java.lang.String is injected at",
                "        TestImplementation.<init>(missingBinding)",
                "    TestImplementation is injected at",
                "        TestModule.bindTestInterface(implementation)",
                "    TestInterface is provided at",
                "        TestComponent.testInterface()"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void bindsMissingRightHandSide() {
    JavaFileObject duplicates =
        JavaFileObjects.forSourceLines(
            "test.Duplicates",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.multibindings.IntKey;",
            "import dagger.multibindings.IntoSet;",
            "import dagger.multibindings.IntoMap;",
            "import dagger.multibindings.LongKey;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "interface Duplicates {",
            "",
            "  interface BoundTwice {}",
            "",
            "  class BoundImpl implements BoundTwice {",
            "    @Inject BoundImpl() {}",
            "  }",
            "",
            "  class NotBound implements BoundTwice {}",
            "",
            "  @Module",
            "  abstract class DuplicatesModule {",
            "    @Binds abstract BoundTwice bindWithResolvedKey(BoundImpl impl);",
            "    @Binds abstract BoundTwice bindWithUnresolvedKey(NotBound notBound);",
            "",
            "    @Binds abstract Object bindObject(NotBound notBound);",
            "",
            "    @Binds @IntoSet abstract BoundTwice bindWithUnresolvedKey_set(NotBound notBound);",
            "",
            "    @Binds @IntoMap @IntKey(1)",
            "    abstract BoundTwice bindWithUnresolvedKey_intMap(NotBound notBound);",
            "",
            "    @Provides @IntoMap @LongKey(2L)",
            "    static BoundTwice provideWithUnresolvedKey_longMap(BoundImpl impl) {",
            "      return impl;",
            "    }",
            "    @Binds @IntoMap @LongKey(2L)",
            "    abstract BoundTwice bindWithUnresolvedKey_longMap(NotBound notBound);",
            "  }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Component;",
            "import java.util.Set;",
            "import java.util.Map;",
            "import test.Duplicates.BoundTwice;",
            "",
            "@Component(modules = Duplicates.DuplicatesModule.class)",
            "interface C {",
            "  BoundTwice boundTwice();",
            "  Object object();",
            "  Set<BoundTwice> set();",
            "  Map<Integer, BoundTwice> intMap();",
            "  Map<Long, BoundTwice> longMap();",
            "}");

    Compilation compilation = daggerCompiler().compile(duplicates, component);
    assertThat(compilation).failed();
    // Some javacs report only the first error for each source line.
    // Assert that one of the expected errors is reported.
    assertThat(compilation)
        .hadErrorContainingMatch(
            "\\Qtest.Duplicates.NotBound cannot be provided\\E|"
                + message(
                    "\\Qtest.Duplicates.BoundTwice is bound multiple times:",
                    "    @Binds test.Duplicates.BoundTwice "
                        + "test.Duplicates.DuplicatesModule"
                        + ".bindWithResolvedKey(test.Duplicates.BoundImpl)",
                    "    @Binds test.Duplicates.BoundTwice "
                        + "test.Duplicates.DuplicatesModule"
                        + ".bindWithUnresolvedKey(test.Duplicates.NotBound)\\E")
                + "|\\Qsame map key is bound more than once")
        .inFile(component)
        .onLineContaining("interface C");
  }

  @Test public void resolvedParametersInDependencyTrace() {
    JavaFileObject generic = JavaFileObjects.forSourceLines("test.Generic",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class Generic<T> {",
        "  @Inject Generic(T t) {}",
        "}");
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import java.util.List;",
        "",
        "final class TestClass {",
        "  @Inject TestClass(List list) {}",
        "}");
    JavaFileObject usesTest = JavaFileObjects.forSourceLines("test.UsesTest",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class UsesTest {",
        "  @Inject UsesTest(Generic<TestClass> genericTestClass) {}",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  UsesTest usesTest();",
        "}");

    Compilation compilation = daggerCompiler().compile(generic, testClass, usesTest, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.List cannot be provided without an @Provides-annotated method.",
                "    java.util.List is injected at",
                "        test.TestClass.<init>(list)",
                "    test.TestClass is injected at",
                "        test.Generic.<init>(t)",
                "    test.Generic<test.TestClass> is injected at",
                "        test.UsesTest.<init>(genericTestClass)",
                "    test.UsesTest is provided at",
                "        test.TestComponent.usesTest()"));
  }

  @Test public void resolvedVariablesInDependencyTrace() {
    JavaFileObject generic = JavaFileObjects.forSourceLines("test.Generic",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import javax.inject.Provider;",
        "",
        "final class Generic<T> {",
        "  @Inject T t;",
        "  @Inject Generic() {}",
        "}");
    JavaFileObject testClass = JavaFileObjects.forSourceLines("test.TestClass",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "import java.util.List;",
        "",
        "final class TestClass {",
        "  @Inject TestClass(List list) {}",
        "}");
    JavaFileObject usesTest = JavaFileObjects.forSourceLines("test.UsesTest",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class UsesTest {",
        "  @Inject UsesTest(Generic<TestClass> genericTestClass) {}",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component",
        "interface TestComponent {",
        "  UsesTest usesTest();",
        "}");

    Compilation compilation = daggerCompiler().compile(generic, testClass, usesTest, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.List cannot be provided without an @Provides-annotated method.",
                "    java.util.List is injected at",
                "        test.TestClass.<init>(list)",
                "    test.TestClass is injected at",
                "        test.Generic.t",
                "    test.Generic<test.TestClass> is injected at",
                "        test.UsesTest.<init>(genericTestClass)",
                "    test.UsesTest is provided at",
                "        test.TestComponent.usesTest()"));
  }

  @Test public void nullCheckForConstructorParameters() {
    JavaFileObject a = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A(String string) {}",
        "}");
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class TestModule {",
        "  @Nullable @Provides String provideString() { return null; }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    Compilation compilation2 =
        javac()
            .withOptions("-Adagger.nullableValidation=WARNING")
            .withProcessors(new ComponentProcessor())
            .compile(NULLABLE, a, module, component);
    assertThat(compilation2).succeeded();
  }

  @Test public void nullCheckForMembersInjectParam() {
    JavaFileObject a = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject A() {}",
        "  @Inject void register(String string) {}",
        "}");
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class TestModule {",
        "  @Nullable @Provides String provideString() { return null; }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    Compilation compilation2 =
        javac()
            .withOptions("-Adagger.nullableValidation=WARNING")
            .withProcessors(new ComponentProcessor())
            .compile(NULLABLE, a, module, component);
    assertThat(compilation2).succeeded();
  }

  @Test public void nullCheckForVariable() {
    JavaFileObject a = JavaFileObjects.forSourceLines("test.A",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class A {",
        "  @Inject String string;",
        "  @Inject A() {}",
        "}");
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class TestModule {",
        "  @Nullable @Provides String provideString() { return null; }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  A a();",
        "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    Compilation compilation2 =
        javac()
            .withOptions("-Adagger.nullableValidation=WARNING")
            .withProcessors(new ComponentProcessor())
            .compile(NULLABLE, a, module, component);
    assertThat(compilation2).succeeded();
  }

  @Test public void nullCheckForComponentReturn() {
    JavaFileObject module = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "import javax.inject.Inject;",
        "",
        "@dagger.Module",
        "final class TestModule {",
        "  @Nullable @Provides String provideString() { return null; }",
        "}");
    JavaFileObject component = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(modules = TestModule.class)",
        "interface TestComponent {",
        "  String string();",
        "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));

    // but if we disable the validation, then it compiles fine.
    Compilation compilation2 =
        javac()
            .withOptions("-Adagger.nullableValidation=WARNING")
            .withProcessors(new ComponentProcessor())
            .compile(NULLABLE, module, component);
    assertThat(compilation2).succeeded();
  }

  @Test
  public void nullCheckForOptionalInstance() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(Optional<String> optional) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            nullableToNonNullable(
                "java.lang.String",
                "@test.Nullable @Provides String test.TestModule.provideString()"));
  }

  @Test
  public void nullCheckForOptionalProvider() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class A {",
            "  @Inject A(Optional<Provider<String>> optional) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).succeeded();
  }

  @Test
  public void nullCheckForOptionalLazy() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "",
            "final class A {",
            "  @Inject A(Optional<Lazy<String>> optional) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).succeeded();
  }

  @Test
  public void nullCheckForOptionalProviderOfLazy() {
    JavaFileObject a =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import com.google.common.base.Optional;",
            "import dagger.Lazy;",
            "import javax.inject.Inject;",
            "import javax.inject.Provider;",
            "",
            "final class A {",
            "  @Inject A(Optional<Provider<Lazy<String>>> optional) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.BindsOptionalOf;",
            "import dagger.Provides;",
            "import javax.inject.Inject;",
            "",
            "@dagger.Module",
            "abstract class TestModule {",
            "  @Nullable @Provides static String provideString() { return null; }",
            "  @BindsOptionalOf abstract String optionalString();",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  A a();",
            "}");
    Compilation compilation = daggerCompiler().compile(NULLABLE, a, module, component);
    assertThat(compilation).succeeded();
  }

  @Test public void componentDependencyMustNotCycle_Direct() {
    JavaFileObject shortLifetime = JavaFileObjects.forSourceLines("test.ComponentShort",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ComponentShort.class)",
        "interface ComponentShort {",
        "}");

    Compilation compilation = daggerCompiler().compile(shortLifetime);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ComponentShort contains a cycle in its component dependencies:",
                "    test.ComponentShort"));
  }

  @Test public void componentDependencyMustNotCycle_Indirect() {
    JavaFileObject longLifetime = JavaFileObjects.forSourceLines("test.ComponentLong",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ComponentMedium.class)",
        "interface ComponentLong {",
        "}");
    JavaFileObject mediumLifetime = JavaFileObjects.forSourceLines("test.ComponentMedium",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ComponentLong.class)",
        "interface ComponentMedium {",
        "}");
    JavaFileObject shortLifetime = JavaFileObjects.forSourceLines("test.ComponentShort",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "@Component(dependencies = ComponentMedium.class)",
        "interface ComponentShort {",
        "}");

    Compilation compilation = daggerCompiler().compile(longLifetime, mediumLifetime, shortLifetime);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ComponentLong contains a cycle in its component dependencies:",
                "    test.ComponentLong",
                "    test.ComponentMedium",
                "    test.ComponentLong"))
        .inFile(longLifetime);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ComponentMedium contains a cycle in its component dependencies:",
                "    test.ComponentMedium",
                "    test.ComponentLong",
                "    test.ComponentMedium"))
        .inFile(mediumLifetime);
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "test.ComponentShort contains a cycle in its component dependencies:",
                "    test.ComponentMedium",
                "    test.ComponentLong",
                "    test.ComponentMedium",
                "    test.ComponentShort"))
        .inFile(shortLifetime);
  }

  @Test
  public void childBindingConflictsWithParent() {
    JavaFileObject aComponent =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = A.AModule.class)",
            "interface A {",
            "  Object conflict();",
            "",
            "  B b();",
            "",
            "  @Module",
            "  static class AModule {",
            "    @Provides static Object abConflict() {",
            "      return \"a\";",
            "    }",
            "  }",
            "}");
    JavaFileObject bComponent =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = B.BModule.class)",
            "interface B {",
            "  Object conflict();",
            "",
            "  @Module",
            "  static class BModule {",
            "    @Provides static Object abConflict() {",
            "      return \"b\";",
            "    }",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(aComponent, bComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.Object is bound multiple times:",
                "    @Provides Object test.A.AModule.abConflict()",
                "    @Provides Object test.B.BModule.abConflict()"))
        .inFile(aComponent)
        .onLineContaining("interface A {");
  }

  @Test
  public void grandchildBindingConflictsWithGrandparent() {
    JavaFileObject aComponent =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Component(modules = A.AModule.class)",
            "interface A {",
            "  Object conflict();",
            "",
            "  B b();",
            "",
            "  @Module",
            "  static class AModule {",
            "    @Provides static Object acConflict() {",
            "      return \"a\";",
            "    }",
            "  }",
            "}");
    JavaFileObject bComponent =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface B {",
            "  C c();",
            "}");
    JavaFileObject cComponent =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = C.CModule.class)",
            "interface C {",
            "  Object conflict();",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides static Object acConflict() {",
            "      return \"c\";",
            "    }",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(aComponent, bComponent, cComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.Object is bound multiple times:",
                "    @Provides Object test.A.AModule.acConflict()",
                "    @Provides Object test.C.CModule.acConflict()"))
        .inFile(aComponent)
        .onLineContaining("interface A {");
  }

  @Test
  public void grandchildBindingConflictsWithChild() {
    JavaFileObject aComponent =
        JavaFileObjects.forSourceLines(
            "test.A",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component",
            "interface A {",
            "  B b();",
            "}");
    JavaFileObject bComponent =
        JavaFileObjects.forSourceLines(
            "test.B",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = B.BModule.class)",
            "interface B {",
            "  Object conflict();",
            "",
            "  C c();",
            "",
            "  @Module",
            "  static class BModule {",
            "    @Provides static Object bcConflict() {",
            "      return \"b\";",
            "    }",
            "  }",
            "}");
    JavaFileObject cComponent =
        JavaFileObjects.forSourceLines(
            "test.C",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = C.CModule.class)",
            "interface C {",
            "  Object conflict();",
            "",
            "  @Module",
            "  static class CModule {",
            "    @Provides static Object bcConflict() {",
            "      return \"c\";",
            "    }",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(aComponent, bComponent, cComponent);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.Object is bound multiple times:",
                "    @Provides Object test.B.BModule.bcConflict()",
                "    @Provides Object test.C.CModule.bcConflict()"))
        .inFile(aComponent)
        .onLineContaining("interface A {");
  }

  @Test
  public void grandchildBindingConflictsWithParentWithNullableViolationAsWarning() {
    JavaFileObject parentConflictsWithChild =
        JavaFileObjects.forSourceLines(
            "test.ParentConflictsWithChild",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.annotation.Nullable;",
            "",
            "@Component(modules = ParentConflictsWithChild.ParentModule.class)",
            "interface ParentConflictsWithChild {",
            "  Child child();",
            "",
            "  @Module",
            "  static class ParentModule {",
            "    @Provides @Nullable static Object nullableParentChildConflict() {",
            "      return \"parent\";",
            "    }",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "test.Child",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = Child.ChildModule.class)",
            "interface Child {",
            "  Object parentChildConflictThatViolatesNullability();",
            "",
            "  @Module",
            "  static class ChildModule {",
            "    @Provides static Object nonNullableParentChildConflict() {",
            "      return \"child\";",
            "    }",
            "  }",
            "}");

    Compilation compilation =
        javac()
            .withOptions("-Adagger.nullableValidation=WARNING")
            .withProcessors(new ComponentProcessor())
            .compile(parentConflictsWithChild, child);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.lang.Object is bound multiple times:",
                "    @Provides Object test.Child.ChildModule.nonNullableParentChildConflict()",
                "    @Provides @javax.annotation.Nullable Object"
                    + " test.ParentConflictsWithChild.ParentModule.nullableParentChildConflict()"))
        .inFile(parentConflictsWithChild)
        .onLine(9);
  }

  @Test
  public void bindingUsedOnlyInSubcomponentDependsOnBindingOnlyInSubcomponent() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "ParentModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides static Object needsString(String string) {",
            "    return \"needs string: \" + string;",
            "  }",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  String string();",
            "  Object needsString();",
            "}");
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "ChildModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides static String string() {",
            "    return \"child string\";",
            "  }",
            "}");

    Compilation compilation = daggerCompiler().compile(parent, parentModule, child, childModule);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContainingMatch(
            "(?s)\\Qjava.lang.String cannot be provided\\E.*\\QChild.needsString()\\E")
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  public void multibindingContributionBetweenAncestorComponentAndEntrypointComponent() {
    JavaFileObject parent =
        JavaFileObjects.forSourceLines(
            "Parent",
            "import dagger.Component;",
            "",
            "@Component(modules = ParentModule.class)",
            "interface Parent {",
            "  Child child();",
            "}");
    JavaFileObject child =
        JavaFileObjects.forSourceLines(
            "Child",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = ChildModule.class)",
            "interface Child {",
            "  Grandchild grandchild();",
            "}");
    JavaFileObject grandchild =
        JavaFileObjects.forSourceLines(
            "Grandchild",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent",
            "interface Grandchild {",
            "  Object object();",
            "}");

    JavaFileObject parentModule =
        JavaFileObjects.forSourceLines(
            "ParentModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "import java.util.Set;",
            "",
            "@Module",
            "class ParentModule {",
            "  @Provides static Object dependsOnSet(Set<String> strings) {",
            "    return \"needs strings: \" + strings;",
            "  }",
            "",
            "  @Provides @IntoSet static String contributesToSet() {",
            "    return \"parent string\";",
            "  }",
            "",
            "  @Provides int missingDependency(double dub) {",
            "    return 4;",
            "  }",
            "}");
    JavaFileObject childModule =
        JavaFileObjects.forSourceLines(
            "ChildModule",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.multibindings.IntoSet;",
            "",
            "@Module",
            "class ChildModule {",
            "  @Provides @IntoSet static String contributesToSet(int i) {",
            "    return \"\" + i;",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler().compile(parent, parentModule, child, childModule, grandchild);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContainingMatch(
            "(?s)\\Qjava.lang.Double cannot be provided\\E.*"
                + "\\QGrandchild.object() [Parent → Child → Grandchild]\\E$")
        .inFile(parent)
        .onLineContaining("interface Parent");
  }

  @Test
  public void missingReleasableReferenceManager() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@CanReleaseReferences",
            "@BadMetadata",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject otherScope =
        JavaFileObjects.forSourceLines(
            "test.OtherScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface OtherScope {}");
    JavaFileObject yetAnotherScope =
        JavaFileObjects.forSourceLines(
            "test.YetAnotherScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface YetAnotherScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");
    JavaFileObject badMetadata =
        JavaFileObjects.forSourceLines(
            "test.BadMetadata", // force one-string-per-line format
            "package test;",
            "",
            "@interface BadMetadata {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponents",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "",
            "interface TestComponents {",
            "  @TestScope",
            "  @YetAnotherScope",
            "  @Component",
            "  interface WrongScopeComponent {",
            "    @ForReleasableReferences(OtherScope.class)",
            "    ReleasableReferenceManager otherManager();",
            "  }",
            "",
            "  @TestScope",
            "  @YetAnotherScope",
            "  @Component",
            "  interface WrongMetadataComponent {",
            "    @ForReleasableReferences(TestScope.class)",
            "    TypedReleasableReferenceManager<TestMetadata> wrongMetadata();",
            "  }",
            "",
            "  @TestScope",
            "  @YetAnotherScope",
            "  @Component",
            "  interface BadMetadataComponent {",
            "    @ForReleasableReferences(TestScope.class)",
            "    TypedReleasableReferenceManager<BadMetadata> badManager();",
            "  }",
            "}");
    Compilation compilation =
        daggerCompiler()
            .compile(testScope, otherScope, yetAnotherScope, testMetadata, badMetadata, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            "There is no binding for "
                + "@dagger.releasablereferences.ForReleasableReferences(test.OtherScope.class) "
                + "dagger.releasablereferences.ReleasableReferenceManager "
                + "because no component in test.TestComponents.WrongScopeComponent's "
                + "component hierarchy is annotated with @test.OtherScope. "
                + "The available reference-releasing scopes are "
                + "[@test.TestScope, @test.YetAnotherScope].")
        .inFile(component)
        .onLineContaining("interface WrongScopeComponent");
    assertThat(compilation)
        .hadErrorContaining(
            "There is no binding for "
                + "@dagger.releasablereferences.ForReleasableReferences(test.TestScope.class) "
                + "dagger.releasablereferences.TypedReleasableReferenceManager<test.TestMetadata> "
                + "because test.TestScope is not annotated with @test.TestMetadata")
        .inFile(component)
        .onLineContaining("interface WrongMetadataComponent");
    assertThat(compilation)
        .hadErrorContaining(
            "There is no binding for "
                + "@dagger.releasablereferences.ForReleasableReferences(test.TestScope.class) "
                + "dagger.releasablereferences.TypedReleasableReferenceManager<test.BadMetadata> "
                + "because test.BadMetadata is not annotated with "
                + "@dagger.releasablereferences.CanReleaseReferences")
        .inFile(component)
        .onLineContaining("interface BadMetadataComponent");
  }

  @Test
  public void releasableReferenceManagerConflict_ReleasableReferenceManager() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@TestMetadata",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");

    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @ForReleasableReferences(TestScope.class)",
            "  static ReleasableReferenceManager rrm() {",
            "    return null;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "",
            "@TestScope",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  @ForReleasableReferences(TestScope.class)",
            "  ReleasableReferenceManager testManager();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(testScope, testMetadata, testModule, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                message(
                    "@%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.ReleasableReferenceManager is bound multiple times:",
                    "    @Provides @%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.ReleasableReferenceManager test.TestModule.rrm()",
                    "    binding for "
                        + "@%1$s.ForReleasableReferences(value = test.TestScope.class) "
                        + "%1$s.ReleasableReferenceManager from the scope declaration"),
                "dagger.releasablereferences"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void releasableReferenceManagerConflict_TypedReleasableReferenceManager() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@TestMetadata",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");

    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides @ForReleasableReferences(TestScope.class)",
            "  static TypedReleasableReferenceManager<TestMetadata> typedRrm() {",
            "    return null;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "",
            "@TestScope",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  @ForReleasableReferences(TestScope.class)",
            "  TypedReleasableReferenceManager<TestMetadata> typedManager();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(testScope, testMetadata, testModule, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                message(
                    "@%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.TypedReleasableReferenceManager<test.TestMetadata> "
                        + "is bound multiple times:",
                    "    @Provides @%1$s.ForReleasableReferences(test.TestScope.class) "
                        + "%1$s.TypedReleasableReferenceManager<test.TestMetadata> "
                        + "test.TestModule.typedRrm()",
                    "    binding for "
                        + "@%1$s.ForReleasableReferences(value = test.TestScope.class) "
                        + "%1$s.TypedReleasableReferenceManager<test.TestMetadata> "
                        + "from the scope declaration"),
                "dagger.releasablereferences"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void releasableReferenceManagerConflict_SetOfReleasableReferenceManager() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@TestMetadata",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");

    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides",
            "  static Set<ReleasableReferenceManager> rrmSet() {",
            "    return null;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.ReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@TestScope",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Set<ReleasableReferenceManager> managers();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(testScope, testMetadata, testModule, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            message(
                "java.util.Set<dagger.releasablereferences.ReleasableReferenceManager> "
                    + "is bound multiple times:",
                "    @Provides "
                    + "Set<dagger.releasablereferences.ReleasableReferenceManager> "
                    + "test.TestModule.rrmSet()",
                "    Dagger-generated binding for "
                    + "Set<dagger.releasablereferences.ReleasableReferenceManager>"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void releasableReferenceManagerConflict_SetOfTypedReleasableReferenceManagers() {
    JavaFileObject testScope =
        JavaFileObjects.forSourceLines(
            "test.TestScope",
            "package test;",
            "",
            "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "import java.lang.annotation.Retention;",
            "import javax.inject.Scope;",
            "",
            "@TestMetadata",
            "@CanReleaseReferences",
            "@Scope",
            "@Retention(RUNTIME)",
            "@interface TestScope {}");
    JavaFileObject testMetadata =
        JavaFileObjects.forSourceLines(
            "test.TestMetadata",
            "package test;",
            "",
            "import dagger.releasablereferences.CanReleaseReferences;",
            "",
            "@CanReleaseReferences",
            "@interface TestMetadata {}");

    JavaFileObject testModule =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides",
            "  static Set<TypedReleasableReferenceManager<TestMetadata>> typedRrmSet() {",
            "    return null;",
            "  }",
            "}");

    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import dagger.releasablereferences.ForReleasableReferences;",
            "import dagger.releasablereferences.TypedReleasableReferenceManager;",
            "import java.util.Set;",
            "",
            "@TestScope",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Set<TypedReleasableReferenceManager<TestMetadata>> typedManagers();",
            "}");

    Compilation compilation =
        daggerCompiler().compile(testScope, testMetadata, testModule, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining(
            String.format(
                message(
                    "java.util.Set<%1$s.TypedReleasableReferenceManager<test.TestMetadata>> "
                        + "is bound multiple times:",
                    "    @Provides "
                        + "Set<%1$s.TypedReleasableReferenceManager<test.TestMetadata>> "
                        + "test.TestModule.typedRrmSet()",
                    "    Dagger-generated binding for "
                        + "Set<%1$s.TypedReleasableReferenceManager<test.TestMetadata>>"),
                "dagger.releasablereferences"))
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void abstractModuleWithInstanceMethod() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class TestModule {",
            "  @Provides int i() { return 1; }",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  int i();",
            "}");
    Compilation compilation = daggerCompiler().compile(module, component);
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("TestModule is abstract and has instance @Provides methods")
        .inFile(component)
        .onLineContaining("interface TestComponent");
  }

  @Test
  public void abstractModuleWithInstanceMethod_subclassedIsAllowed() {
    JavaFileObject abstractModule =
        JavaFileObjects.forSourceLines(
            "test.AbstractModule",
            "package test;",
            "",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "abstract class AbstractModule {",
            "  @Provides int i() { return 1; }",
            "}");
    JavaFileObject subclassedModule =
        JavaFileObjects.forSourceLines(
            "test.SubclassedModule",
            "package test;",
            "",
            "import dagger.Module;",
            "",
            "@Module",
            "class SubclassedModule extends AbstractModule {}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = SubclassedModule.class)",
            "interface TestComponent {",
            "  int i();",
            "}");
    Compilation compilation = daggerCompiler().compile(abstractModule, subclassedModule, component);
    assertThat(compilation).succeeded();
  }

}
