package com.strumenta.kolasu.javalib;

import com.strumenta.kolasu.model.*;
import io.lionweb.language.Concept;
import io.lionweb.model.Node;
import kotlin.reflect.KCallable;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeProjection;
import kotlin.reflect.full.KAnnotatedElements;
import org.jetbrains.annotations.NotNull;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;

import static com.strumenta.kolasu.model.ModelKt.getRESERVED_FEATURE_NAMES;
import static kotlin.jvm.JvmClassMappingKt.getKotlinClass;
import static kotlin.reflect.full.KClassifiers.createType;

/**
 * A subclass of {@link Node} that uses Java's reflection to compute its feature set.
 * Kotlin's reflection does not work well with Java classes following the JavaBeans naming convention.
 */
public class JavaNode extends BaseASTNode {
    @Internal
    public @NotNull List<PropertyDescription> getDerivedProperties() {
        return getProperties().stream().filter(PropertyDescription::getDerived).collect(Collectors.toList());
    }

    @Override
    @Internal
    public @NotNull List<PropertyDescription> getOriginalProperties() {
        return getProperties().stream().filter(p -> !p.getDerived()).collect(Collectors.toList());
    }

    @Override
    @Internal
    public @NotNull List<PropertyDescription> getProperties() {
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(getClass());
            return Arrays.stream(beanInfo.getPropertyDescriptors())
                    .filter(this::isFeature)
                    .map(this::getPropertyDescription)
                    .collect(Collectors.toList());
        } catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    protected boolean isFeature(PropertyDescriptor p) {
        if (getRESERVED_FEATURE_NAMES().contains(p.getName())) {
            return false;
        }
        Method getClassMethod;
        try {
             getClassMethod = Object.class.getDeclaredMethod("getClass");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("This is a bug", e);
        }
        if (p.getReadMethod() == null || p.getReadMethod().equals(getClassMethod)) {
            return false;
        }
        return !hasAnnotation(p, Internal.class) && !hasAnnotation(p, Link.class);
    }

    public static boolean hasAnnotation(PropertyDescriptor p, Class<? extends Annotation> annotation) {
        KCallable<?> kCallable = getKotlinClass(BaseASTNode.class).getMembers().stream()
                .filter(m -> m.getName().equals(p.getName())).findFirst().orElse(null);
        return p.getReadMethod().isAnnotationPresent(annotation) ||
                (p.getWriteMethod() != null && p.getWriteMethod().isAnnotationPresent(annotation)) ||
                (kCallable != null && !KAnnotatedElements.findAnnotations(kCallable, getKotlinClass(annotation)).isEmpty());
    }

    @NotNull
    protected PropertyDescription getPropertyDescription(PropertyDescriptor p) {
        String name = p.getName();
        Class<?> type = p.getPropertyType();
        boolean provideNodes = isANode(type);
        Multiplicity multiplicity = Multiplicity.OPTIONAL;
        if (Collection.class.isAssignableFrom(type)) {
            multiplicity = Multiplicity.MANY;
        } else if (p.getReadMethod().isAnnotationPresent(Mandatory.class) || p.getPropertyType().isPrimitive()) {
            multiplicity = Multiplicity.SINGULAR;
        }
        Object value;
        try {
            value = p.getReadMethod().invoke(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        PropertyType propertyType = provideNodes ? PropertyType.CONTAINMENT : PropertyType.ATTRIBUTE;
        Class<?> actualType = type;
        if (ReferenceByName.class.isAssignableFrom(type)) {
            propertyType = PropertyType.REFERENCE;
            Type returnType = p.getReadMethod().getGenericReturnType();
            if (returnType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) returnType).getActualTypeArguments();
                if (typeArgs.length == 1 && typeArgs[0] instanceof Class) {
                    actualType = (Class<?>) typeArgs[0];
                }
            }
        }
        boolean derived = hasAnnotation(p, Derived.class);
        boolean nullable = multiplicity == Multiplicity.OPTIONAL;
        return new PropertyDescription(
                name, provideNodes, multiplicity, value, propertyType, derived, kotlinType(actualType, nullable));
    }

    @NotNull
    public static KType kotlinType(Class<?> type) {
        return kotlinType(type, false);
    }

    @NotNull
    public static KType kotlinType(Class<?> type, boolean nullable) {
        List<KTypeProjection> arguments = new LinkedList<>();
        if (type.isArray() && !type.getComponentType().isPrimitive()) {
            arguments.add(KTypeProjection.covariant(kotlinType(type.getComponentType(), false)));
        } else {
            for (TypeVariable<? extends Class<?>> p : type.getTypeParameters()) {
                if (p.getBounds().length == 1 && p.getBounds()[0] instanceof Class<?>) {
                    arguments.add(KTypeProjection.covariant(kotlinType((Class<?>) p.getBounds()[0], false)));
                } else if (p.getBounds().length == 1 && p.getBounds()[0] instanceof ParameterizedType) {
                    arguments.add(KTypeProjection.covariant(kotlinType((ParameterizedType) p.getBounds()[0], false)));
                } else {
                    arguments.add(KTypeProjection.star);
                }
            }
        }
        return createType(
                getKotlinClass(type), arguments,
                nullable, Collections.emptyList());
    }

    @NotNull
    public static KType kotlinType(ParameterizedType type) {
        return kotlinType(type, false);
    }

    @NotNull
    public static KType kotlinType(ParameterizedType type, boolean nullable) {
        List<KTypeProjection> arguments = new LinkedList<>();
        for (Type p : type.getActualTypeArguments()) {
            if (p instanceof Class<?>) {
                arguments.add(KTypeProjection.covariant(kotlinType((Class<?>) p, false)));
            } else if (p instanceof ParameterizedType) {
                arguments.add(KTypeProjection.covariant(kotlinType((ParameterizedType) p, false)));
            } else {
                arguments.add(KTypeProjection.star);
            }
        }
        return createType(
                getKotlinClass((Class<?>) type.getRawType()), arguments,
                nullable, Collections.emptyList());
    }

    public static boolean isANode(Class<?> type) {
        return Reflection.isANode(getKotlinClass(type));
    }


}
