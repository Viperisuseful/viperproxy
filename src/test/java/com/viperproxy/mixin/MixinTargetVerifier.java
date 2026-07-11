package com.viperproxy.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class MixinTargetVerifier {
    private static final String MIXIN_ANNOTATION = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INJECT_ANNOTATION = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final List<String> MIXINS = List.of(
        "com/viperproxy/mixin/ClientConnectionMixin",
        "com/viperproxy/mixin/ConnectScreenMixin"
    );

    private MixinTargetVerifier() {
    }

    public static void main(String[] args) throws IOException {
        List<String> failures = new ArrayList<>();
        int selectorCount = 0;

        for (String mixinClass : MIXINS) {
            MixinMetadata metadata = readMixinMetadata(mixinClass);
            if (metadata.targets().isEmpty()) {
                failures.add(mixinClass + " has no @Mixin target");
                continue;
            }

            for (Injection injection : metadata.injections()) {
                for (String selector : injection.selectors()) {
                    selectorCount++;
                    for (String target : metadata.targets()) {
                        verifySelector(mixinClass, injection.handler(), selector, target, failures);
                    }
                }
            }
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                "Mixin target verification failed:" + System.lineSeparator() + String.join(System.lineSeparator(), failures)
            );
        }

        System.out.println("Verified " + selectorCount + " Mixin injection selectors against Minecraft bytecode.");
    }

    private static MixinMetadata readMixinMetadata(String mixinClass) throws IOException {
        List<String> targets = new ArrayList<>();
        List<Injection> injections = new ArrayList<>();

        readClass(mixinClass).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MIXIN_ANNOTATION.equals(descriptor)) {
                    return null;
                }

                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if (!"value".equals(name)) {
                            return null;
                        }

                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(String ignored, Object value) {
                                if (value instanceof Type type) {
                                    targets.add(type.getInternalName());
                                }
                            }
                        };
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                        if (!INJECT_ANNOTATION.equals(annotationDescriptor)) {
                            return null;
                        }

                        List<String> selectors = new ArrayList<>();
                        injections.add(new Injection(name, selectors));
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitArray(String annotationField) {
                                if (!"method".equals(annotationField)) {
                                    return null;
                                }

                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String ignored, Object value) {
                                        if (value instanceof String selector) {
                                            selectors.add(selector);
                                        }
                                    }
                                };
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        return new MixinMetadata(List.copyOf(targets), List.copyOf(injections));
    }

    private static void verifySelector(
        String mixinClass,
        String handler,
        String selector,
        String targetClass,
        List<String> failures
    ) throws IOException {
        Set<MethodKey> methods = new HashSet<>();
        readClass(targetClass).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                methods.add(new MethodKey(name, descriptor));
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        int descriptorStart = selector.indexOf('(');
        String methodName = descriptorStart < 0 ? selector : selector.substring(0, descriptorStart);
        String methodDescriptor = descriptorStart < 0 ? null : selector.substring(descriptorStart);
        boolean found = methods.stream().anyMatch(method ->
            method.name().equals(methodName) && (methodDescriptor == null || method.descriptor().equals(methodDescriptor))
        );

        if (!found) {
            failures.add(
                mixinClass + "#" + handler + " selects missing " + targetClass + "." + selector
            );
        }
    }

    private static ClassReader readClass(String internalName) throws IOException {
        String resource = internalName + ".class";
        ClassLoader classLoader = MixinTargetVerifier.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Class resource not found: " + resource);
            }
            return new ClassReader(input);
        }
    }

    private record Injection(String handler, List<String> selectors) {
    }

    private record MethodKey(String name, String descriptor) {
    }

    private record MixinMetadata(List<String> targets, List<Injection> injections) {
    }
}
