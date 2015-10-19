/*
 * Copyright 2015 Red Hat, Inc, and individual contributors.
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

package org.wildfly.swarm.tools;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackageDetector {
    public static Set<String> detectPackages(final ZipFile file) throws IOException {
        final PackageCollector visitor = new PackageCollector();

        return detectPackages(file, visitor);
    }

    protected static Set<String> detectPackages(final ZipFile file,
                                                final PackageCollector visitor) throws IOException {
        final Enumeration<? extends ZipEntry> entries = file.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();

            // TODO: accept wars, ears?
            if (name.endsWith(".jar")) {
                final File jarFile = File.createTempFile("swarmPackageDetector", ".jar");
                jarFile.deleteOnExit();

                try (InputStream in = file.getInputStream(entry);
                     FileOutputStream out = new FileOutputStream(jarFile)) {
                    IOUtils.copy(in, out);
                }

                detectPackages(new ZipFile(jarFile), visitor);
            } else if (name.endsWith(".class")) {
                try (InputStream in = file.getInputStream(entry)) {
                    new ClassReader(in).accept(visitor, 0);
                }
            }
        }

        return visitor.packages();
    }

    static class PackageCollector extends ClassVisitor {

        public Set<String> packages() {
            return packages;
        }

        public PackageCollector() {
            super(Opcodes.ASM5);
        }

        @Override
        public void visit(final int __,
                          final int ___,
                          final String name,
                          final String signature,
                          final String superName,
                          final String[] interfaces) {
            addPackage(name);

            if (signature == null) {
                if (superName != null) {
                    addInternalType(superName);
                }
                addInternalTypes(interfaces);
            } else {
                addSignature(signature);
            }
        }


        @Override
        public AnnotationVisitor visitAnnotation(final String desc,
                                                 final boolean __) {
            addType(desc);

            return new AnnotationVisitor(Opcodes.ASM5) {
                @Override
                public void visit(final String __,
                                  final Object value) {
                    if (value instanceof Type) {
                        addType((Type)value);
                    }
                }

                @Override
                public void visitEnum(final String __,
                                      final String desc,
                                      final String ___) {
                    addType(desc);
                }

                @Override
                public AnnotationVisitor visitAnnotation(final String __,
                                                         final String desc) {
                    addType(desc);

                    return this;
                }

                @Override
                public AnnotationVisitor visitArray(final String __) {
                    return this;
                }
            };
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(final int __,
                                                     final TypePath ___,
                                                     final String desc,
                                                     final boolean ____) {
            addType(desc);

            return ANNOTATION_VISITOR;
        }

        @Override
        public FieldVisitor visitField(final int __,
                                       final String ___,
                                       final String desc,
                                       final String signature,
                                       final Object value) {
            if (value instanceof Type) {
                addType((Type)value);
            }

            if (signature != null) {
                addTypeSignature(signature);
            } else {
                addType(desc);
            }

            return new FieldVisitor(Opcodes.ASM5) {
                @Override
                public AnnotationVisitor visitAnnotation(String desc,
                                                         boolean __) {
                    addType(desc);

                    return ANNOTATION_VISITOR;
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(final int __,
                                                             final TypePath ___,
                                                             final String desc,
                                                             final boolean ____) {
                    addType(desc);

                    return ANNOTATION_VISITOR;
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(final int __,
                                         final String ___,
                                         final String desc,
                                         final String signature,
                                         final String[] exceptions) {
            if (signature != null) {
                addSignature(signature);
            } else {
                addMethodTypes(desc);
            }
            addInternalTypes(exceptions);

            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return ANNOTATION_VISITOR;
                }

                @Override
                public AnnotationVisitor visitAnnotation(final String desc,
                                                         final boolean __) {
                    addType(desc);

                    return ANNOTATION_VISITOR;
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(final int __,
                                                             final TypePath ___,
                                                             final String desc,
                                                             final boolean ____) {
                    addType(desc);

                    return ANNOTATION_VISITOR;
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(final int __,
                                                                  final String desc,
                                                                  final boolean ___) {
                    addType(desc);

                    return ANNOTATION_VISITOR;
                }

                @Override
                public void visitTypeInsn(final int __,
                                          final String type) {
                    addType(Type.getObjectType(type));
                }

                @Override
                public void visitFieldInsn(final int __,
                                           final String owner,
                                           final String ___,
                                           final String desc) {
                    addInternalType(owner);
                    addType(desc);
                }

                @Override
                public void visitMethodInsn(final int __,
                                            final String owner,
                                            final String ___,
                                            final String desc,
                                            final boolean ____) {
                    addInternalType(owner);
                    addMethodTypes(desc);
                }

                @Override
                public void visitInvokeDynamicInsn(final String __,
                                                   final String desc,
                                                   final Handle bsm,
                                                   final Object... bsmArgs) {
                    addMethodTypes(desc);
                    addConstant(bsm);
                    for (Object each : bsmArgs) {
                        addConstant(each);
                    }
                }

                @Override
                public void visitLdcInsn(final Object cst) {
                    addConstant(cst);
                }

                @Override
                public void visitMultiANewArrayInsn(final String desc,
                                                    final int __) {
                    addType(desc);
                }

                @Override
                public AnnotationVisitor visitInsnAnnotation(final int __,
                                                             final TypePath ___,
                                                             final String desc,
                                                             final boolean ____) {
                    addType(desc);

                    return ANNOTATION_VISITOR;
                }

                @Override
                public void visitLocalVariable(final String __,
                                               final String ___,
                                               final String signature,
                                               final Label ____,
                                               final Label _____,
                                               final int ______) {
                    addTypeSignature(signature);
                }

                @Override
                public AnnotationVisitor visitLocalVariableAnnotation(final int __,
                                                                      final TypePath ___,
                                                                      Label[] ____,
                                                                      Label[] _____,
                                                                      int[] ______,
                                                                      String desc,
                                                                      boolean _______) {
                    addType(desc);

                    return ANNOTATION_VISITOR;
                }

                @Override
                public void visitTryCatchBlock(final Label __,
                                               final Label ____,
                                               final Label _____,
                                               final String type) {
                    if (type != null) {
                        addInternalType(type);
                    }
                }

                @Override
                public AnnotationVisitor visitTryCatchAnnotation(final int __,
                                                                 final TypePath ___,
                                                                 final String desc,
                                                                 final boolean ____) {
                    addType(desc);

                    return ANNOTATION_VISITOR;
                }
            };
        }



        private String addPackage(String name) {
            if (name != null) {
                final int pos = name.lastIndexOf('/');
                if (pos > -1) {
                    name = name.substring(0, pos);
                }
                packages.add(name.replace('/', '.'));
            }

            return name;
        }

        private void addType(final Type type) {
            switch (type.getSort()) {
                case Type.ARRAY:
                    addType(type.getElementType());
                    break;
                case Type.OBJECT:
                    addPackage(type.getInternalName());
                    break;
                case Type.METHOD:
                    addMethodTypes(type.getDescriptor());
                    break;
            }
        }

        private void addType(final String desc) {
            addType(Type.getType(desc));
        }

        private void addInternalType(final String name) {
            addType(Type.getObjectType(name));
        }

        private void addInternalTypes(final String[] names) {
            if (names != null) {
                for (String each : names) {
                    if (each != null) {
                        addInternalType(each);
                    }
                }
            }
        }

        private void addMethodTypes(final String desc) {
            addType(Type.getReturnType(desc));
            for (Type each : Type.getArgumentTypes(desc)) {
                addType(each);
            }
        }

        private void addSignature(final String signature) {
            if (signature != null) {
                new SignatureReader(signature)
                        .accept(SIGNATURE_VISITOR);
            }
        }

        void addTypeSignature(final String signature) {
            if (signature != null) {
                new SignatureReader(signature)
                        .acceptType(SIGNATURE_VISITOR);
            }
        }

        void addConstant(final Object constant) {
            if (constant instanceof Type) {
                addType((Type)constant);
            } else if (constant instanceof Handle) {
                Handle handle = (Handle)constant;
                addInternalType(handle.getOwner());
                addMethodTypes(handle.getDesc());
            }
        }

        private final Set<String> packages = new HashSet<>();

        private final AnnotationVisitor ANNOTATION_VISITOR =
                new AnnotationVisitor(Opcodes.ASM5) {
                    @Override
                    public void visit(final String __,
                                      final Object value) {
                        if (value instanceof Type) {
                            addType((Type)value);
                        }
                    }

                    @Override
                    public void visitEnum(final String __,
                                          final String desc,
                                          final String ___) {
                        addType(desc);
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(final String __,
                                                             final String desc) {
                        addType(desc);

                        return this;
                    }

                    @Override
                    public AnnotationVisitor visitArray(final String __) {
                        return this;
                    }
                };

        private final SignatureVisitor SIGNATURE_VISITOR =
                new SignatureVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitClassType(final String name) {
                        outerName = name;
                        addInternalType(name);
                    }

                    @Override
                    public void visitInnerClassType(final String name) {
                        outerName += "$" + name;
                        addInternalType(outerName);
                    }

                    private String outerName;
                };

    }
}
