package io.ch.java.opt.chain.plugin;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Entry point: returns woven bytecode, or {@code null} if the class had no {@code @OptChain}
 * methods (caller should leave the file untouched).
 */
public final class OptChainWeaver {

    public static final String OPT_CHAIN_DESC = "Lio/ch/java/opt/chain/OptChain;";

    private OptChainWeaver() {
    }

    public static byte[] weave(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        // First pass: detect whether any method carries @OptChain
        OptChainPresenceScanner scanner = new OptChainPresenceScanner();
        reader.accept(scanner, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!scanner.hasOptChain()) {
            return null;
        }

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        ClassVisitor visitor = new OptChainClassVisitor(Opcodes.ASM9, writer);
        // EXPAND_FRAMES not required when we recompute frames
        reader.accept(visitor, ClassReader.SKIP_FRAMES);
        return writer.toByteArray();
    }
}
