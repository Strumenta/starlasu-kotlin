package com.strumenta.starlasu.javalib;

import com.google.common.reflect.TypeToken;
import com.strumenta.starlasu.model.*;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class CompilationUnit extends BaseASTNode {

    public static class A extends BaseASTNode {
        private List<B> bs = new LinkedList<>();

        public List<B> getBs() {
            return bs;
        }

        @Override
        @NotNull
        public List<PropertyDescription> getOriginalProperties() {
            Type type = new TypeToken<List<B>>() {}.getType();
            return Arrays.asList(new PropertyDescription(
                    "bs", true, Multiplicity.MANY, getBs(), PropertyType.CONTAINMENT, false,
                    JavaNode.kotlinType((ParameterizedType) type, false)));
        }

    }

    public static class B extends BaseASTNode {

    }

    private List<A> as = new LinkedList<>();

    public List<A> getAs() {
        return as;
    }

    @NotNull
    @Override
    @Internal
    public List<PropertyDescription> getProperties() {
        Type type = new TypeToken<List<A>>() {}.getType();
        return Arrays.asList(new PropertyDescription(
                "as", Multiplicity.MANY, getAs(), PropertyType.CONTAINMENT, false,
                JavaNode.kotlinType((ParameterizedType) type, false)));
    }

    @Override
    @NotNull
    public List<PropertyDescription> getOriginalProperties() {
        return getProperties();
    }
}
