package com.analyzer.rules.graph.type;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents complete type information including generics, arrays, and
 * wildcards.
 * This class captures the full structure of a type declaration from bytecode.
 */
public class TypeInfo {

    private final String className;
    private final TypeKind kind;
    private final List<TypeInfo> typeArguments;
    private final TypeInfo arrayComponentType;
    private final TypeInfo wildcardBound;
    private final WildcardKind wildcardKind;

    private TypeInfo(Builder builder) {
        this.className = builder.className;
        this.kind = builder.kind;
        this.typeArguments = Collections.unmodifiableList(new ArrayList<>(builder.typeArguments));
        this.arrayComponentType = builder.arrayComponentType;
        this.wildcardBound = builder.wildcardBound;
        this.wildcardKind = builder.wildcardKind;
    }

    public String getClassName() {
        return className;
    }

    public TypeKind getKind() {
        return kind;
    }

    public List<TypeInfo> getTypeArguments() {
        return typeArguments;
    }

    public TypeInfo getArrayComponentType() {
        return arrayComponentType;
    }

    public TypeInfo getWildcardBound() {
        return wildcardBound;
    }

    public WildcardKind getWildcardKind() {
        return wildcardKind;
    }

    /**
     * Returns all class names referenced in this type (including nested generics).
     */
    public List<String> getAllReferencedClasses() {
        List<String> classes = new ArrayList<>();
        collectReferencedClasses(this, classes);
        return classes;
    }

    private void collectReferencedClasses(TypeInfo type, List<String> classes) {
        if (type == null) {
            return;
        }

        if (type.className != null && type.kind != TypeKind.PRIMITIVE) {
            classes.add(type.className);
        }

        for (TypeInfo typeArg : type.typeArguments) {
            collectReferencedClasses(typeArg, classes);
        }

        if (type.arrayComponentType != null) {
            collectReferencedClasses(type.arrayComponentType, classes);
        }

        if (type.wildcardBound != null) {
            collectReferencedClasses(type.wildcardBound, classes);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String className;
        private TypeKind kind = TypeKind.CLASS;
        private List<TypeInfo> typeArguments = new ArrayList<>();
        private TypeInfo arrayComponentType;
        private TypeInfo wildcardBound;
        private WildcardKind wildcardKind;

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder kind(TypeKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder addTypeArgument(TypeInfo typeArg) {
            this.typeArguments.add(typeArg);
            return this;
        }

        public Builder typeArguments(List<TypeInfo> typeArguments) {
            this.typeArguments = new ArrayList<>(typeArguments);
            return this;
        }

        public Builder arrayComponentType(TypeInfo componentType) {
            this.arrayComponentType = componentType;
            this.kind = TypeKind.ARRAY;
            return this;
        }

        public Builder wildcardBound(TypeInfo bound, WildcardKind wildcardKind) {
            this.wildcardBound = bound;
            this.wildcardKind = wildcardKind;
            this.kind = TypeKind.WILDCARD;
            return this;
        }

        public TypeInfo build() {
            return new TypeInfo(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TypeInfo typeInfo = (TypeInfo) o;
        return Objects.equals(className, typeInfo.className) &&
                kind == typeInfo.kind &&
                Objects.equals(typeArguments, typeInfo.typeArguments) &&
                Objects.equals(arrayComponentType, typeInfo.arrayComponentType) &&
                Objects.equals(wildcardBound, typeInfo.wildcardBound) &&
                wildcardKind == typeInfo.wildcardKind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, kind, typeArguments, arrayComponentType, wildcardBound, wildcardKind);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        switch (kind) {
            case ARRAY:
                sb.append(arrayComponentType).append("[]");
                break;
            case WILDCARD:
                sb.append("?");
                if (wildcardBound != null) {
                    sb.append(" ").append(wildcardKind == WildcardKind.EXTENDS ? "extends" : "super")
                            .append(" ").append(wildcardBound);
                }
                break;
            case PRIMITIVE:
            case CLASS:
            default:
                sb.append(className);
                if (!typeArguments.isEmpty()) {
                    sb.append("<");
                    for (int i = 0; i < typeArguments.size(); i++) {
                        if (i > 0)
                            sb.append(", ");
                        sb.append(typeArguments.get(i));
                    }
                    sb.append(">");
                }
                break;
        }

        return sb.toString();
    }

    public enum TypeKind {
        CLASS,
        PRIMITIVE,
        ARRAY,
        WILDCARD,
        TYPE_VARIABLE
    }

    public enum WildcardKind {
        EXTENDS,
        SUPER
    }
}
