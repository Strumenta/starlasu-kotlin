package com.strumenta.starlasu.javalib;

import com.strumenta.starlasu.model.*;
import kotlin.Pair;
import io.lionweb.model.Node;
import kotlin.reflect.KCallable;
import kotlin.reflect.KClass;
import kotlin.reflect.KType;
import kotlin.reflect.KTypeProjection;
import kotlin.reflect.full.KAnnotatedElements;
import org.jetbrains.annotations.NotNull;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.stream.Collectors;

import static com.strumenta.starlasu.model.ModelKt.getRESERVED_FEATURE_NAMES;
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
        if (p.getReadMethod() == null || p.getReadMethod().getDeclaringClass() == Object.class) {
            return false;
        }
        return !hasAnnotation(p, Internal.class);
    }

    public static boolean hasAnnotation(PropertyDescriptor p, Class<? extends Annotation> annotation) {
        // Fast case: Java property
        if (p.getReadMethod().isAnnotationPresent(annotation) ||
                (p.getWriteMethod() != null && p.getWriteMethod().isAnnotationPresent(annotation))) {
            return true;
        } else if (p.getReadMethod().getDeclaringClass() == Node.class) {
            // Slow case: Kotlin property
            for (KCallable<?> member : getKotlinClass(Node.class).getMembers()) {
                if (member.getName().equals(p.getName())) {
                    return !KAnnotatedElements.findAnnotations(member, getKotlinClass(annotation)).isEmpty();
                }
            }
            return false;
        } else {
            return false;
        }
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
                name, multiplicity,
                () -> {
                    try {
                        return p.getReadMethod().invoke(this);
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                propertyType, derived, kotlinType(actualType, nullable));
    }

    @NotNull
    public static KType kotlinType(Class<?> type) {
        return kotlinType(type, false);
    }

    private static final Map<Class, Pair<KType, KType>> simpleKotlinTypeCache =
            Collections.synchronizedMap(new WeakHashMap<>());

    @NotNull
    public static KType kotlinType(Class<?> type, boolean nullable) {
        Pair<KType, KType> kTypes = simpleKotlinTypeCache.computeIfAbsent(type, t -> {
            List<KTypeProjection> arguments = new LinkedList<>();
            if (t.isArray() && !t.getComponentType().isPrimitive()) {
                arguments.add(KTypeProjection.covariant(kotlinType(t.getComponentType(), false)));
            } else {
                for (TypeVariable<? extends Class<?>> p : t.getTypeParameters()) {
                    if (p.getBounds().length == 1 && p.getBounds()[0] instanceof Class<?>) {
                        arguments.add(KTypeProjection.covariant(kotlinType((Class<?>) p.getBounds()[0], false)));
                    } else if (p.getBounds().length == 1 && p.getBounds()[0] instanceof ParameterizedType) {
                        arguments.add(KTypeProjection.covariant(kotlinType((ParameterizedType) p.getBounds()[0], false)));
                    } else {
                        arguments.add(KTypeProjection.star);
                    }
                }
            }
            KClass<?> kotlinClass = getKotlinClass(t);
            return new Pair<>(
                    createType(kotlinClass, arguments, false, Collections.emptyList()),
                    createType(kotlinClass, arguments, true, Collections.emptyList())
            );
        });
        return nullable ? kTypes.getSecond() : kTypes.getFirst();
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

    private static final Map<Class, Boolean> isANodeCache = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean isANode(Class<?> type) {
        return isANodeCache.computeIfAbsent(type, c -> Reflection.isANode(getKotlinClass(c)));
    }


}
