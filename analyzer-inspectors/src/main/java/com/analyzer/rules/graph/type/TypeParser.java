package com.analyzer.rules.graph.type;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Unified type parser that extracts complete type information from bytecode
 * descriptors and signatures.
 * Handles arrays, generics, wildcards, and type bounds.
 */
public class TypeParser {

    private static final Logger logger = LoggerFactory.getLogger(TypeParser.class);

    /**
     * Parses a field or method type, preferring signature if available.
     * 
     * @param descriptor the type descriptor (never null)
     * @param signature  the generic signature (may be null)
     * @return complete type information
     */
    public static TypeInfo parseType(String descriptor, String signature) {
        if (signature != null && !signature.isEmpty()) {
            try {
                return parseSignature(signature);
            } catch (Exception e) {
                logger.trace("Could not parse signature: {}, falling back to descriptor", signature, e);
            }
        }

        // Fallback to descriptor parsing
        return parseDescriptor(descriptor);
    }

    /**
     * Parses a method signature to extract parameter and return types.
     * 
     * @param descriptor the method descriptor
     * @param signature  the generic signature (may be null)
     * @return list of all types involved (parameters + return type)
     */
    public static List<TypeInfo> parseMethodSignature(String descriptor, String signature) {
        List<TypeInfo> types = new ArrayList<>();

        if (signature != null && !signature.isEmpty()) {
            try {
                SignatureReader reader = new SignatureReader(signature);
                MethodSignatureExtractor extractor = new MethodSignatureExtractor();
                reader.accept(extractor);
                types.addAll(extractor.getTypes());
                return types;
            } catch (Exception e) {
                logger.trace("Could not parse method signature: {}, falling back to descriptor", signature, e);
            }
        }

        // Fallback to descriptor parsing
        Type methodType = Type.getMethodType(descriptor);

        // Return type
        if (methodType.getReturnType().getSort() != Type.VOID) {
            types.add(parseDescriptor(methodType.getReturnType().getDescriptor()));
        }

        // Parameter types
        for (Type paramType : methodType.getArgumentTypes()) {
            types.add(parseDescriptor(paramType.getDescriptor()));
        }

        return types;
    }

    /**
     * Parses a type descriptor (without generics).
     */
    private static TypeInfo parseDescriptor(String descriptor) {
        Type type = Type.getType(descriptor);

        // Handle arrays
        if (type.getSort() == Type.ARRAY) {
            TypeInfo componentType = parseDescriptor(type.getElementType().getDescriptor());
            return TypeInfo.builder()
                    .arrayComponentType(componentType)
                    .build();
        }

        // Handle primitives
        if (type.getSort() < Type.ARRAY) {
            return TypeInfo.builder()
                    .className(type.getClassName())
                    .kind(TypeInfo.TypeKind.PRIMITIVE)
                    .build();
        }

        // Handle objects/classes
        return TypeInfo.builder()
                .className(type.getClassName())
                .kind(TypeInfo.TypeKind.CLASS)
                .build();
    }

    /**
     * Parses a generic signature (with type parameters).
     */
    private static TypeInfo parseSignature(String signature) {
        SignatureReader reader = new SignatureReader(signature);
        TypeSignatureExtractor extractor = new TypeSignatureExtractor();
        reader.acceptType(extractor);
        return extractor.getResult();
    }

    /**
     * ASM SignatureVisitor that extracts complete type information from signatures.
     */
    private static class TypeSignatureExtractor extends SignatureVisitor {
        private final Stack<TypeInfo.Builder> builderStack = new Stack<>();
        private TypeInfo.Builder currentBuilder;

        public TypeSignatureExtractor() {
            super(Opcodes.ASM9);
            currentBuilder = TypeInfo.builder();
            builderStack.push(currentBuilder);
        }

        public TypeInfo getResult() {
            return builderStack.isEmpty() ? currentBuilder.build() : builderStack.peek().build();
        }

        @Override
        public void visitClassType(String name) {
            String className = Type.getObjectType(name).getClassName();
            currentBuilder.className(className).kind(TypeInfo.TypeKind.CLASS);
        }

        @Override
        public void visitInnerClassType(String name) {
            // Handle inner classes
            String currentClass = currentBuilder.build().getClassName();
            if (currentClass != null) {
                currentBuilder.className(currentClass + "$" + name);
            }
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            TypeInfo.Builder argBuilder = TypeInfo.builder();

            if (wildcard == SignatureVisitor.EXTENDS) {
                argBuilder.kind(TypeInfo.TypeKind.WILDCARD);
                TypeSignatureExtractor wildcardVisitor = new TypeSignatureExtractor();
                builderStack.push(argBuilder);
                return new WildcardBoundVisitor(argBuilder, TypeInfo.WildcardKind.EXTENDS, wildcardVisitor);
            } else if (wildcard == SignatureVisitor.SUPER) {
                argBuilder.kind(TypeInfo.TypeKind.WILDCARD);
                TypeSignatureExtractor wildcardVisitor = new TypeSignatureExtractor();
                builderStack.push(argBuilder);
                return new WildcardBoundVisitor(argBuilder, TypeInfo.WildcardKind.SUPER, wildcardVisitor);
            } else if (wildcard == SignatureVisitor.INSTANCEOF) {
                // Concrete type argument
                TypeSignatureExtractor argVisitor = new TypeSignatureExtractor();
                argVisitor.currentBuilder = argBuilder;
                builderStack.push(argBuilder);
                return argVisitor;
            } else {
                // Unbounded wildcard '?'
                argBuilder.kind(TypeInfo.TypeKind.WILDCARD).className("?");
                currentBuilder.addTypeArgument(argBuilder.build());
                return this;
            }
        }

        @Override
        public void visitBaseType(char descriptor) {
            String primitiveType = getPrimitiveTypeName(descriptor);
            currentBuilder.className(primitiveType).kind(TypeInfo.TypeKind.PRIMITIVE);
        }

        @Override
        public SignatureVisitor visitArrayType() {
            TypeInfo.Builder componentBuilder = TypeInfo.builder();
            TypeSignatureExtractor componentVisitor = new TypeSignatureExtractor();
            componentVisitor.currentBuilder = componentBuilder;
            builderStack.push(componentBuilder);
            return new ArrayComponentVisitor(currentBuilder, componentVisitor);
        }

        @Override
        public void visitEnd() {
            if (builderStack.size() > 1) {
                TypeInfo.Builder completed = builderStack.pop();
                TypeInfo.Builder parent = builderStack.peek();
                parent.addTypeArgument(completed.build());
            }
        }

        private String getPrimitiveTypeName(char descriptor) {
            switch (descriptor) {
                case 'B':
                    return "byte";
                case 'C':
                    return "char";
                case 'D':
                    return "double";
                case 'F':
                    return "float";
                case 'I':
                    return "int";
                case 'J':
                    return "long";
                case 'S':
                    return "short";
                case 'Z':
                    return "boolean";
                case 'V':
                    return "void";
                default:
                    return "unknown";
            }
        }
    }

    /**
     * Visitor for wildcard bounds (? extends X, ? super Y).
     */
    private static class WildcardBoundVisitor extends SignatureVisitor {
        private final TypeInfo.Builder wildcardBuilder;
        private final TypeInfo.WildcardKind wildcardKind;
        private final TypeSignatureExtractor boundVisitor;

        public WildcardBoundVisitor(TypeInfo.Builder wildcardBuilder, TypeInfo.WildcardKind wildcardKind,
                TypeSignatureExtractor boundVisitor) {
            super(Opcodes.ASM9);
            this.wildcardBuilder = wildcardBuilder;
            this.wildcardKind = wildcardKind;
            this.boundVisitor = boundVisitor;
        }

        @Override
        public void visitClassType(String name) {
            boundVisitor.visitClassType(name);
        }

        @Override
        public void visitEnd() {
            TypeInfo bound = boundVisitor.getResult();
            wildcardBuilder.wildcardBound(bound, wildcardKind);
        }
    }

    /**
     * Visitor for array component types.
     */
    private static class ArrayComponentVisitor extends SignatureVisitor {
        private final TypeInfo.Builder arrayBuilder;
        private final TypeSignatureExtractor componentVisitor;

        public ArrayComponentVisitor(TypeInfo.Builder arrayBuilder, TypeSignatureExtractor componentVisitor) {
            super(Opcodes.ASM9);
            this.arrayBuilder = arrayBuilder;
            this.componentVisitor = componentVisitor;
        }

        @Override
        public void visitClassType(String name) {
            componentVisitor.visitClassType(name);
        }

        @Override
        public void visitBaseType(char descriptor) {
            componentVisitor.visitBaseType(descriptor);
        }

        @Override
        public void visitEnd() {
            TypeInfo component = componentVisitor.getResult();
            arrayBuilder.arrayComponentType(component);
        }
    }

    /**
     * Extracts all types from a method signature.
     */
    private static class MethodSignatureExtractor extends SignatureVisitor {
        private final List<TypeInfo> types = new ArrayList<>();

        public MethodSignatureExtractor() {
            super(Opcodes.ASM9);
        }

        public List<TypeInfo> getTypes() {
            return types;
        }

        @Override
        public SignatureVisitor visitParameterType() {
            TypeSignatureExtractor extractor = new TypeSignatureExtractor();
            return new TypeCollector(extractor, types);
        }

        @Override
        public SignatureVisitor visitReturnType() {
            TypeSignatureExtractor extractor = new TypeSignatureExtractor();
            return new TypeCollector(extractor, types);
        }
    }

    /**
     * Collects parsed types into a list.
     */
    private static class TypeCollector extends SignatureVisitor {
        private final TypeSignatureExtractor extractor;
        private final List<TypeInfo> types;

        public TypeCollector(TypeSignatureExtractor extractor, List<TypeInfo> types) {
            super(Opcodes.ASM9);
            this.extractor = extractor;
            this.types = types;
        }

        @Override
        public void visitClassType(String name) {
            extractor.visitClassType(name);
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            return extractor.visitTypeArgument(wildcard);
        }

        @Override
        public void visitBaseType(char descriptor) {
            extractor.visitBaseType(descriptor);
        }

        @Override
        public SignatureVisitor visitArrayType() {
            return extractor.visitArrayType();
        }

        @Override
        public void visitEnd() {
            extractor.visitEnd();
            TypeInfo result = extractor.getResult();
            if (result != null && result.getKind() != TypeInfo.TypeKind.PRIMITIVE
                    || !"void".equals(result.getClassName())) {
                types.add(result);
            }
        }
    }
}
