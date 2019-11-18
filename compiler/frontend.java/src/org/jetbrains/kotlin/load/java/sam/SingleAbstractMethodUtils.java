/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.load.java.sam;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.*;
import org.jetbrains.kotlin.descriptors.annotations.Annotations;
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl;
import org.jetbrains.kotlin.descriptors.impl.TypeParameterDescriptorImpl;
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl;
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor;
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor;
import org.jetbrains.kotlin.load.java.descriptors.JavaMethodDescriptor;
import org.jetbrains.kotlin.load.java.lazy.descriptors.LazyJavaClassDescriptor;
import org.jetbrains.kotlin.load.kotlin.sam.SamConversionResolverImplKt;
import org.jetbrains.kotlin.name.Name;
import org.jetbrains.kotlin.resolve.SamConversionResolver;
import org.jetbrains.kotlin.resolve.jvm.JavaResolverUtils;
import org.jetbrains.kotlin.types.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.jetbrains.kotlin.types.Variance.IN_VARIANCE;

public class SingleAbstractMethodUtils {
    private SingleAbstractMethodUtils() {
    }

    @Nullable
    public static KotlinType getFunctionTypeForSamType(@NotNull KotlinType samType, @NotNull SamConversionResolver samResolver) {
        UnwrappedType unwrappedType = samType.unwrap();
        if (unwrappedType instanceof FlexibleType) {
            SimpleType lower = getFunctionTypeForSamType(((FlexibleType) unwrappedType).getLowerBound(), samResolver);
            SimpleType upper = getFunctionTypeForSamType(((FlexibleType) unwrappedType).getUpperBound(), samResolver);
            assert (lower == null) == (upper == null) : "Illegal flexible type: " + unwrappedType;

            if (upper == null) return null;
            return KotlinTypeFactory.flexibleType(lower, upper);
        }
        else {
            return getFunctionTypeForSamType((SimpleType) unwrappedType, samResolver);
        }
    }

    @Nullable
    private static SimpleType getFunctionTypeForSamType(@NotNull SimpleType samType, @NotNull SamConversionResolver samResolver) {
        // e.g. samType == Comparator<String>?

        ClassifierDescriptor classifier = samType.getConstructor().getDeclarationDescriptor();
        if (classifier instanceof ClassDescriptor) {
            ClassDescriptor descriptor = (ClassDescriptor) classifier;
            if (!(descriptor instanceof JavaClassDescriptor) && !descriptor.isFun()) return null;

            // Function2<T, T, Int>
            SimpleType functionTypeDefault = samResolver.resolveFunctionTypeIfSamInterface(descriptor);

            if (functionTypeDefault != null) {
                SimpleType noProjectionsSamType = SingleAbstractMethodUtilsKt.nonProjectionParametrization(samType);
                if (noProjectionsSamType == null) return null;

                // Function2<String, String, Int>?
                KotlinType type = TypeSubstitutor.create(noProjectionsSamType).substitute(functionTypeDefault, IN_VARIANCE);
                assert type != null : "Substitution based on type with no projections '" + noProjectionsSamType +
                                      "' should not end with conflict";

                SimpleType simpleType = TypeSubstitutionKt.asSimpleType(type);

                return simpleType.makeNullableAsSpecified(samType.isMarkedNullable());
            }
        }
        return null;
    }

    @NotNull
    public static SamConstructorDescriptor createSamConstructorFunction(
            @NotNull DeclarationDescriptor owner,
            @NotNull ClassDescriptor samInterface,
            @NotNull SamConversionResolver samResolver
    ) {
        assert SamConversionResolverImplKt.getSingleAbstractMethodOrNull(samInterface) != null : samInterface;

        SamConstructorDescriptorImpl result = new SamConstructorDescriptorImpl(owner, samInterface);

        List<TypeParameterDescriptor> samTypeParameters = samInterface.getTypeConstructor().getParameters();
        SimpleType unsubstitutedSamType = samInterface.getDefaultType();
        initializeSamConstructorDescriptor(samInterface, result, samTypeParameters, unsubstitutedSamType, samResolver);

        return result;
    }

    private static void initializeSamConstructorDescriptor(
            @NotNull ClassDescriptor samInterface,
            @NotNull SimpleFunctionDescriptorImpl samConstructor,
            @NotNull List<TypeParameterDescriptor> samTypeParameters,
            @NotNull KotlinType unsubstitutedSamType,
            @NotNull SamConversionResolver samResolver
    ) {
        TypeParameters typeParameters = recreateAndInitializeTypeParameters(samTypeParameters, samConstructor);

        KotlinType parameterTypeUnsubstituted = getFunctionTypeForSamType(unsubstitutedSamType, samResolver);
        assert parameterTypeUnsubstituted != null : "couldn't get function type for SAM type " + unsubstitutedSamType;
        KotlinType parameterType = typeParameters.substitutor.substitute(parameterTypeUnsubstituted, Variance.IN_VARIANCE);
        assert parameterType != null : "couldn't substitute type: " + parameterTypeUnsubstituted +
                                       ", substitutor = " + typeParameters.substitutor;
        ValueParameterDescriptor parameter = new ValueParameterDescriptorImpl(
                samConstructor, null, 0, Annotations.Companion.getEMPTY(), Name.identifier("function"), parameterType,
                /* declaresDefaultValue = */ false,
                /* isCrossinline = */ false,
                /* isNoinline = */ false,
                null, SourceElement.NO_SOURCE);

        KotlinType returnType = typeParameters.substitutor.substitute(unsubstitutedSamType, Variance.OUT_VARIANCE);
        assert returnType != null : "couldn't substitute type: " + unsubstitutedSamType +
                                    ", substitutor = " + typeParameters.substitutor;

        samConstructor.initialize(
                null,
                null,
                typeParameters.descriptors,
                Collections.singletonList(parameter),
                returnType,
                Modality.FINAL,
                samInterface.getVisibility()
        );
    }

    public static SamConstructorDescriptor createTypeAliasSamConstructorFunction(
            @NotNull TypeAliasDescriptor typeAliasDescriptor,
            @NotNull SamConstructorDescriptor underlyingSamConstructor,
            @NotNull SamConversionResolver samResolver
    ) {
        SamTypeAliasConstructorDescriptorImpl result = new SamTypeAliasConstructorDescriptorImpl(typeAliasDescriptor, underlyingSamConstructor);

        ClassDescriptor samInterface = underlyingSamConstructor.getBaseDescriptorForSynthetic();
        List<TypeParameterDescriptor> samTypeParameters = typeAliasDescriptor.getTypeConstructor().getParameters();
        SimpleType unsubstitutedSamType = typeAliasDescriptor.getExpandedType();
        initializeSamConstructorDescriptor(samInterface, result, samTypeParameters, unsubstitutedSamType, samResolver);

        return result;
    }

    public static boolean isSamClassDescriptor(@NotNull ClassDescriptor descriptor) {
        if (descriptor.isFun()) return true;
        if (descriptor instanceof LazyJavaClassDescriptor && descriptor.getDefaultFunctionTypeForSamInterface() != null) return true;

        return false;
    }

    public static boolean isSamType(@NotNull KotlinType type) {
        ClassifierDescriptor descriptor = type.getConstructor().getDeclarationDescriptor();
        if (descriptor instanceof ClassDescriptor && ((ClassDescriptor) descriptor).isFun()) return true;

        return getFunctionTypeForSamType(type, JavaBasedSamConversionResolver.INSTANCE) != null;
    }

    public static boolean isSamAdapterNecessary(@NotNull FunctionDescriptor fun) {
        for (ValueParameterDescriptor param : fun.getValueParameters()) {
            if (isSamType(param.getType())) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public static SamAdapterDescriptor<JavaMethodDescriptor> createSamAdapterFunction(
            @NotNull JavaMethodDescriptor original,
            @NotNull SamConversionResolver samResolver
    ) {
        SamAdapterFunctionDescriptor result = new SamAdapterFunctionDescriptor(original);
        return initSamAdapter(original, result, new FunctionInitializer() {
            @Override
            public void initialize(
                    @NotNull List<TypeParameterDescriptor> typeParameters,
                    @NotNull List<ValueParameterDescriptor> valueParameters,
                    @NotNull KotlinType returnType
            ) {
                result.initialize(
                        null,
                        original.getDispatchReceiverParameter(),
                        typeParameters,
                        valueParameters,
                        returnType,
                        Modality.FINAL,
                        original.getVisibility()
                );
            }
        }, samResolver);
    }

    @NotNull
    public static SamAdapterDescriptor<JavaClassConstructorDescriptor> createSamAdapterConstructor(
            @NotNull JavaClassConstructorDescriptor original,
            @NotNull SamConversionResolver samResolver
    ) {
        SamAdapterClassConstructorDescriptor result = new SamAdapterClassConstructorDescriptor(original);
        return initSamAdapter(original, result, new FunctionInitializer() {
            @Override
            public void initialize(
                    @NotNull List<TypeParameterDescriptor> typeParameters,
                    @NotNull List<ValueParameterDescriptor> valueParameters,
                    @NotNull KotlinType returnType
            ) {
                result.initialize(valueParameters, original.getVisibility());
                result.setReturnType(returnType);
            }
        }, samResolver);
    }

    @NotNull
    private static <F extends FunctionDescriptor> SamAdapterDescriptor<F> initSamAdapter(
            @NotNull F original,
            @NotNull SamAdapterDescriptor<F> adapter,
            @NotNull FunctionInitializer initializer,
            @NotNull SamConversionResolver samResolver
    ) {
        TypeParameters typeParameters = recreateAndInitializeTypeParameters(original.getTypeParameters(), adapter);

        KotlinType returnTypeUnsubstituted = original.getReturnType();
        assert returnTypeUnsubstituted != null : "Creating SAM adapter for not initialized original: " + original;

        TypeSubstitutor substitutor = typeParameters.substitutor;
        KotlinType returnType = substitutor.substitute(returnTypeUnsubstituted, Variance.INVARIANT);
        assert returnType != null : "couldn't substitute type: " + returnTypeUnsubstituted +
                                        ", substitutor = " + substitutor;


        List<ValueParameterDescriptor> valueParameters = createValueParametersForSamAdapter(original, adapter, substitutor, samResolver);

        initializer.initialize(typeParameters.descriptors, valueParameters, returnType);

        return adapter;
    }

    public static List<ValueParameterDescriptor> createValueParametersForSamAdapter(
            @NotNull FunctionDescriptor original,
            @NotNull FunctionDescriptor samAdapter,
            @NotNull TypeSubstitutor substitutor,
            @NotNull SamConversionResolver samResolver
    ) {
        List<ValueParameterDescriptor> originalValueParameters = original.getValueParameters();
        List<ValueParameterDescriptor> valueParameters = new ArrayList<>(originalValueParameters.size());
        for (ValueParameterDescriptor originalParam : originalValueParameters) {
            KotlinType originalType = originalParam.getType();
            KotlinType functionType = getFunctionTypeForSamType(originalType, samResolver);
            KotlinType newTypeUnsubstituted = functionType != null ? functionType : originalType;
            KotlinType newType = substitutor.substitute(newTypeUnsubstituted, Variance.IN_VARIANCE);
            assert newType != null : "couldn't substitute type: " + newTypeUnsubstituted + ", substitutor = " + substitutor;

            ValueParameterDescriptor newParam = new ValueParameterDescriptorImpl(
                    samAdapter, null, originalParam.getIndex(), originalParam.getAnnotations(),
                    originalParam.getName(), newType,
                    /* declaresDefaultValue = */ false,
                    /* isCrossinline = */ false,
                    /* isNoinline = */ false,
                    null, SourceElement.NO_SOURCE
            );
            valueParameters.add(newParam);
        }
        return valueParameters;
    }

    @NotNull
    private static TypeParameters recreateAndInitializeTypeParameters(
            @NotNull List<TypeParameterDescriptor> originalParameters,
            @Nullable DeclarationDescriptor newOwner
    ) {
        if (newOwner instanceof SamAdapterClassConstructorDescriptor) {
            return new TypeParameters(originalParameters, TypeSubstitutor.EMPTY);
        }

        Map<TypeParameterDescriptor, TypeParameterDescriptorImpl> traitToFunTypeParameters =
                JavaResolverUtils.recreateTypeParametersAndReturnMapping(originalParameters, newOwner);
        TypeSubstitutor typeParametersSubstitutor = JavaResolverUtils.createSubstitutorForTypeParameters(traitToFunTypeParameters);
        for (Map.Entry<TypeParameterDescriptor, TypeParameterDescriptorImpl> mapEntry : traitToFunTypeParameters.entrySet()) {
            TypeParameterDescriptor traitTypeParameter = mapEntry.getKey();
            TypeParameterDescriptorImpl funTypeParameter = mapEntry.getValue();

            for (KotlinType upperBound : traitTypeParameter.getUpperBounds()) {
                KotlinType upperBoundSubstituted = typeParametersSubstitutor.substitute(upperBound, Variance.INVARIANT);
                assert upperBoundSubstituted != null : "couldn't substitute type: " + upperBound + ", substitutor = " + typeParametersSubstitutor;
                funTypeParameter.addUpperBound(upperBoundSubstituted);
            }

            funTypeParameter.setInitialized();
        }

        List<TypeParameterDescriptor> typeParameters = new ArrayList<>(traitToFunTypeParameters.values());
        return new TypeParameters(typeParameters, typeParametersSubstitutor);
    }

    private static class TypeParameters {
        public final List<TypeParameterDescriptor> descriptors;
        public final TypeSubstitutor substitutor;

        private TypeParameters(List<TypeParameterDescriptor> descriptors, TypeSubstitutor substitutor) {
            this.descriptors = descriptors;
            this.substitutor = substitutor;
        }
    }

    private static abstract class FunctionInitializer {
        public abstract void initialize(
                @NotNull List<TypeParameterDescriptor> typeParameters,
                @NotNull List<ValueParameterDescriptor> valueParameters,
                @NotNull KotlinType returnType
        );
    }


}
