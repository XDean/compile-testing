/*
 * Copyright (C) 2018 XDean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package xdean.test.compile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.tools.JavaFileObject;

import org.junit.Test;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

/**
 * Runner for compilation test. It can process {@code @Compile},
 * {@code @Compiled} and normal junit test method.
 *
 * @see Compile
 * @see Compiled
 * @author Dean Xu (XDean@github.com)
 */
public class CompileTestRunner extends BlockJUnit4ClassRunner {
  public CompileTestRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected void collectInitializationErrors(List<Throwable> errors) {
    super.collectInitializationErrors(errors);
    if (!CompileTestCase.class.isAssignableFrom(getTestClass().getJavaClass())) {
      errors.add(new Exception("CompileTestRunner must run with CompileTestCase"));
    }
  }

  @Override
  protected void validateTestMethods(List<Throwable> errors) {
    validatePublicVoidNoArgMethods(Test.class, false, errors);
    validateCompileTestMethods(errors);
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    CompileTestCase ct = (CompileTestCase) test;
    if (method.getMethod().isAnnotationPresent(Compile.class)) {
      ct.setMethod(method);
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          // Compile compile = AnnotationUtils.getAnnotation(method.getMethod(),
          // Compile.class);
          Compile compile = method.getMethod().getAnnotation(Compile.class);
          Class<?> clz = getTestClass().getJavaClass();
          Compiler.javac()
              .withProcessors(ct)
              .compile(Arrays.stream(compile.sources())
                  .map(s -> clz.getResource(s))
                  .map(u -> JavaFileObjects.forResource(u))
                  .toArray(JavaFileObject[]::new));
          if (ct.getError() != null) {
            throw ct.getError();
          }
        }
      };
    } else if (method.getMethod().isAnnotationPresent(Compiled.class)) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          // Compiled compiled =
          // AnnotationUtils.getAnnotation(method.getMethod(), Compiled.class);
          Compiled compiled = method.getMethod().getAnnotation(Compiled.class);
          Class<?> clz = getTestClass().getJavaClass();
          Compilation compilation = Compiler.javac()
              .withProcessors(Arrays.stream(compiled.processors())
                  .map(c -> {
                    try {
                      return c.newInstance();
                    } catch (Exception e) {
                      throw new IllegalStateException("Annotation Processor must has no-arg public constructor");
                    }
                  })
                  .toArray(Processor[]::new))
              .withOptions(Arrays.stream(compiled.options()).toArray(Object[]::new))
              .compile(Arrays.stream(compiled.sources())
                  .map(s -> clz.getResource(s))
                  .map(u -> JavaFileObjects.forResource(u))
                  .toArray(JavaFileObject[]::new));
          method.invokeExplosively(ct, compilation);
        }
      };
    } else {
      return super.methodInvoker(method, test);
    }
  }

  @Override
  protected List<FrameworkMethod> computeTestMethods() {
    List<FrameworkMethod> list = new ArrayList<>();
    list.addAll(getTestClass().getAnnotatedMethods(Test.class));
    list.addAll(getTestClass().getAnnotatedMethods(Compile.class));
    list.addAll(getTestClass().getAnnotatedMethods(Compiled.class));
    return list;
  }

  protected void validateCompileTestMethods(List<Throwable> errors) {
    Set<FrameworkMethod> methods = new HashSet<>();
    methods.addAll(getTestClass().getAnnotatedMethods(Compile.class));
    methods.addAll(getTestClass().getAnnotatedMethods(Compiled.class));
    for (FrameworkMethod method : methods) {
      method.validatePublicVoid(false, errors);
      boolean compile = method.getMethod().isAnnotationPresent(Compile.class);
      boolean compiled = method.getMethod().isAnnotationPresent(Compiled.class);
      if (compile && compiled) {
        errors.add(new Exception("Method " + method.getName() + " can't annotated both @Compile and @Compiled"));
      } else if (compile) {
        int count = method.getMethod().getParameterCount();
        if (count != 1 || !method.getMethod().getParameterTypes()[0].isAssignableFrom(RoundEnvironment.class)) {
          errors.add(new Exception(
              "Method " + method.getName() + " must have only one param with type RoundEnvironment"));
        }
      } else if (compiled) {
        int count = method.getMethod().getParameterCount();
        if (count != 1 || !method.getMethod().getParameterTypes()[0].isAssignableFrom(Compilation.class)) {
          errors.add(new Exception(
              "Method " + method.getName() + " must have only one param with type Compilation"));
        }
      }
    }
  }
}
