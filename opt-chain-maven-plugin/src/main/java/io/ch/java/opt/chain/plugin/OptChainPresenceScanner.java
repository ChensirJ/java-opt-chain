package io.ch.java.opt.chain.plugin;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class OptChainPresenceScanner extends ClassVisitor {

    private boolean hasOptChain;

    OptChainPresenceScanner() {
        super(Opcodes.ASM9);
    }

    boolean hasOptChain() {
        return hasOptChain;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                     String signature, String[] exceptions) {
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                if (OptChainWeaver.OPT_CHAIN_DESC.equals(desc)) {
                    hasOptChain = true;
                }
                return null;
            }
        };
    }
}
