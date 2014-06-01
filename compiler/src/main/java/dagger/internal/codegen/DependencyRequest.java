/*
 * Copyright (C) 2014 Google, Inc.
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import dagger.Lazy;
import dagger.MembersInjector;
import dagger.Provides;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;


/**
 * Represents a request for a key at an injection point. Parameters to {@link Inject} constructors
 * or {@link Provides} methods are examples of key requests.
 *
 * @author Gregory Kick
 * @since 2.0
 */
// TODO(gak): Set bindings and the permutations thereof need to be addressed
@AutoValue
abstract class DependencyRequest {
  enum Kind {
    /** A default request for an instance.  E.g.: {@code Blah} */
    INSTANCE,
    /** A request for a {@link Provider}.  E.g.: {@code Provider<Blah>} */
    PROVIDER,
    /** A request for a {@link Lazy}.  E.g.: {@code Lazy<Blah>} */
    LAZY,
    /** A request for a {@link MembersInjector}.  E.g.: {@code MembersInjector<Blah>} */
    MEMBERS_INJECTOR,
  }

  abstract Kind kind();
  abstract Key key();
  abstract Element requestElement();

  static final class Factory {
    private final Elements elements;
    private final Types types;

    Factory(Elements elements, Types types) {
      this.elements = elements;
      this.types = types;
    }

    ImmutableList<DependencyRequest> forRequiredVariables(
        List<? extends VariableElement> variables) {
      return FluentIterable.from(variables)
          .transform(new Function<VariableElement, DependencyRequest>() {
            @Override public DependencyRequest apply(VariableElement input) {
              return forRequiredVariable(input);
            }
          })
          .toList();
    }

    DependencyRequest forRequiredVariable(VariableElement variableElement) {
      checkNotNull(variableElement);
      TypeMirror type = variableElement.asType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(variableElement);
      return newDependencyRequest(variableElement, type, qualifier);
    }

    DependencyRequest forComponentProvisionMethod(ExecutableElement provisionMethod) {
      checkNotNull(provisionMethod);
      TypeMirror type = provisionMethod.getReturnType();
      Optional<AnnotationMirror> qualifier = InjectionAnnotations.getQualifier(provisionMethod);
      return newDependencyRequest(provisionMethod, type, qualifier);
    }

    DependencyRequest forComponentMembersInjectionMethod(ExecutableElement membersInjectionMethod) {
      checkNotNull(membersInjectionMethod);
      Optional<AnnotationMirror> qualifier =
          InjectionAnnotations.getQualifier(membersInjectionMethod);
      checkArgument(!qualifier.isPresent());
      return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
          Key.create(qualifier,
              Iterables.getOnlyElement(membersInjectionMethod.getParameters()).asType()),
          membersInjectionMethod);
    }

    private DependencyRequest newDependencyRequest(Element requestElement, TypeMirror type,
        Optional<AnnotationMirror> qualifier) {
      if (elements.getTypeElement(Provider.class.getCanonicalName())
          .equals(types.asElement(type))) {
        DeclaredType providerType = (DeclaredType) type;
        return new AutoValue_DependencyRequest(Kind.PROVIDER,
            Key.create(qualifier, Iterables.getOnlyElement(providerType.getTypeArguments())),
            requestElement);
      } else if (elements.getTypeElement(Lazy.class.getCanonicalName())
          .equals(types.asElement(type))) {
        DeclaredType lazyType = (DeclaredType) type;
        return new AutoValue_DependencyRequest(Kind.LAZY,
            Key.create(qualifier, Iterables.getOnlyElement(lazyType.getTypeArguments())),
            requestElement);
      } else if (elements.getTypeElement(MembersInjector.class.getCanonicalName())
          .equals(types.asElement(type))) {
        checkArgument(!qualifier.isPresent());
        DeclaredType membersInjectorType = (DeclaredType) type;
        return new AutoValue_DependencyRequest(Kind.MEMBERS_INJECTOR,
            Key.create(qualifier, Iterables.getOnlyElement(membersInjectorType.getTypeArguments())),
            requestElement);
      } else {
        return new AutoValue_DependencyRequest(Kind.INSTANCE, Key.create(qualifier, type),
            requestElement);
      }
    }
  }
}
