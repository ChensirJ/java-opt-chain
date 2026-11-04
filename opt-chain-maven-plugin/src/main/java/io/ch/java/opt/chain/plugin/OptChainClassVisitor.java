package io.ch.java.opt.chain.plugin;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class OptChainClassVisitor extends ClassVisitor {

    OptChainClassVisitor(int api, ClassVisitor cv) {
        super(api, cv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
        return new OptChainMethodProbe(api, mv, access, name, descriptor);
    }

    /**
     * Looks for {@code @OptChain}; if present, wraps with the rewriting visitor.
     */
    private static final class OptChainMethodProbe extends MethodVisitor {
        private final int access;
        private final String name;
        private final String descriptor;
        private final MethodVisitor downstream;
        private boolean annotated;

        OptChainMethodProbe(int api, MethodVisitor mv, int access, String name, String descriptor) {
            super(api, mv);
            this.downstream = mv;
            this.access = access;
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (OptChainWeaver.OPT_CHAIN_DESC.equals(desc)) {
                annotated = true;
            }
            return super.visitAnnotation(desc, visible);
        }

        @Override
        public void visitCode() {
            if (annotated) {
                // Swap delegate to rewriting visitor for the body
                OptChainMethodVisitor rewriter =
                        new OptChainMethodVisitor(api, downstream, access, name, descriptor);
                mv = rewriter;
            }
            super.visitCode();
        }
    }
}
