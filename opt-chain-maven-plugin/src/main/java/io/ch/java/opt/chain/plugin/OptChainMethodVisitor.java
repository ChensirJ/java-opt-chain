package io.ch.java.opt.chain.plugin;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * Conservative / practical optional-chain rewriter.
 * <p>
 * For each {@code GETFIELD}, {@code INVOKEVIRTUAL}, and {@code INVOKEINTERFACE}
 * inside an {@code @OptChain} method:
 * <ol>
 *   <li>Spill any call arguments to fresh locals (invokes only).</li>
 *   <li>{@code dup} the receiver and branch on null.</li>
 *   <li>Non-null path: restore args and perform the original access/call.</li>
 *   <li>Null path: pop receiver and push a typed default
 *       ({@code null} / {@code 0} / {@code false} / {@code 0L} / …).</li>
 * </ol>
 * Relies on the outer {@code ClassWriter.COMPUTE_FRAMES | COMPUTE_MAXS}.
 * </p>
 *
 * <h2>Known limitations</h2>
 * <ul>
 *   <li>All instance field/method ops in the method are wrapped — not a precise
 *       chain data-flow analysis.</li>
 *   <li>{@code INVOKESTATIC}, {@code INVOKESPECIAL} (incl. {@code <init>}), and
 *       {@code INVOKEDYNAMIC} are not rewritten.</li>
 *   <li>Argument expressions still evaluate before the null check (Java evaluation
 *       order); only the call itself is skipped when the receiver is null.</li>
 * </ul>
 */
final class OptChainMethodVisitor extends GeneratorAdapter {

    OptChainMethodVisitor(int api, MethodVisitor mv, int access, String name, String descriptor) {
        super(api, mv, access, name, descriptor);
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        if (opcode == Opcodes.GETFIELD) {
            rewriteGetField(owner, name, descriptor);
        } else {
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor,
                                boolean isInterface) {
        if ((opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)
                && !"<init>".equals(name)) {
            rewriteInvoke(opcode, owner, name, descriptor, isInterface);
        } else {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }
    }

    private void rewriteGetField(String owner, String name, String descriptor) {
        Type fieldType = Type.getType(descriptor);
        Label nullPath = newLabel();
        Label end = newLabel();

        // stack: ..., receiver
        dup();
        ifNull(nullPath);
        super.visitFieldInsn(Opcodes.GETFIELD, owner, name, descriptor);
        goTo(end);

        mark(nullPath);
        pop();
        pushDefault(fieldType);

        mark(end);
    }

    private void rewriteInvoke(int opcode, String owner, String name, String descriptor,
                               boolean isInterface) {
        Type[] argTypes = Type.getArgumentTypes(descriptor);
        Type returnType = Type.getReturnType(descriptor);

        int[] argLocals = new int[argTypes.length];
        for (int i = argTypes.length - 1; i >= 0; i--) {
            argLocals[i] = newLocal(argTypes[i]);
            storeLocal(argLocals[i], argTypes[i]);
        }

        Label nullPath = newLabel();
        Label end = newLabel();

        dup();
        ifNull(nullPath);

        for (int i = 0; i < argTypes.length; i++) {
            loadLocal(argLocals[i], argTypes[i]);
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        goTo(end);

        mark(nullPath);
        pop();
        if (returnType.getSort() != Type.VOID) {
            pushDefault(returnType);
        }

        mark(end);
    }

    private void pushDefault(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                visitInsn(Opcodes.ICONST_0);
                break;
            case Type.LONG:
                visitInsn(Opcodes.LCONST_0);
                break;
            case Type.FLOAT:
                visitInsn(Opcodes.FCONST_0);
                break;
            case Type.DOUBLE:
                visitInsn(Opcodes.DCONST_0);
                break;
            case Type.ARRAY:
            case Type.OBJECT:
            default:
                visitInsn(Opcodes.ACONST_NULL);
                break;
        }
    }
}
