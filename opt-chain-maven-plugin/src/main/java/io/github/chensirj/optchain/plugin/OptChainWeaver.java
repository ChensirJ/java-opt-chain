package io.github.chensirj.optchain.plugin;

import io.github.chensirj.optchain.OptChain;
import io.github.chensirj.optchain.OptChainOps;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Rewrites {@code @OptChain} methods and any method that contains {@link OptChainOps#opt} markers.
 * <p>
 * Precise mode (markers present): only the read or call that consumes a marked receiver is
 * null-checked, and the marker invocation is removed. Legacy mode (hand-written {@code @OptChain}
 * with {@code precise = false} and no markers): every {@code GETFIELD}, {@code INVOKEVIRTUAL},
 * and {@code INVOKEINTERFACE} is null-checked, matching older releases.
 * </p>
 */
public final class OptChainWeaver {

    public static final String OPT_CHAIN_DESC = "L" + OptChain.class.getName().replace('.', '/') + ";";
    static final String OPT_OWNER = OptChainOps.class.getName().replace('.', '/');
    static final String OPT_DESC = "(Ljava/lang/Object;)Ljava/lang/Object;";
    private static final Type[] NO_ARGS = new Type[0];

    private OptChainWeaver() {
    }

    /**
     * @return woven bytecode, or {@code null} when the class has nothing to rewrite
     */
    public static byte[] weave(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.SKIP_FRAMES);

        boolean changed = false;
        for (MethodNode method : classNode.methods) {
            changed |= rewriteMethod(classNode.name, method);
        }
        if (!changed) {
            return null;
        }

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (RuntimeException ex) {
                    return "java/lang/Object";
                }
            }
        };
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static boolean rewriteMethod(String owner, MethodNode method) {
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return false;
        }
        if (method.instructions == null || method.instructions.size() == 0) {
            return false;
        }
        if (containsOptCall(method)) {
            return rewritePrecise(owner, method);
        }
        if (isLegacyAnnotation(method)) {
            return rewriteLegacy(method);
        }
        return false;
    }

    private static boolean rewritePrecise(String owner, MethodNode method) {
        Set<AbstractInsnNode> marked = findMarkedAccesses(owner, method);
        List<AbstractInsnNode> snapshot = snapshot(method);
        boolean changed = false;
        for (AbstractInsnNode insn : snapshot) {
            if (isOptCall(insn)) {
                method.instructions.remove(insn);
                changed = true;
            } else if (marked.contains(insn)) {
                rewriteAccess(method, insn);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteLegacy(MethodNode method) {
        List<AbstractInsnNode> snapshot = snapshot(method);
        boolean changed = false;
        for (AbstractInsnNode insn : snapshot) {
            if (isLegacyAccess(insn)) {
                rewriteAccess(method, insn);
                changed = true;
            }
        }
        return changed;
    }

    private static List<AbstractInsnNode> snapshot(MethodNode method) {
        List<AbstractInsnNode> snapshot = new ArrayList<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            snapshot.add(insn);
        }
        return snapshot;
    }

    private static Set<AbstractInsnNode> findMarkedAccesses(String owner, MethodNode method) {
        Set<AbstractInsnNode> marked = Collections.newSetFromMap(new IdentityHashMap<>());
        Analyzer<BasicValue> analyzer = new Analyzer<>(new MarkInterpreter(marked));
        try {
            analyzer.analyze(owner, method);
        } catch (AnalyzerException ex) {
            throw new IllegalStateException(
                    "opt-chain could not analyze " + owner + "." + method.name + method.desc, ex);
        }
        return marked;
    }

    private static boolean containsOptCall(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (isOptCall(insn)) {
                return true;
            }
        }
        return false;
    }

    static boolean isOptCall(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode method) || method.getOpcode() != Opcodes.INVOKESTATIC) {
            return false;
        }
        return OPT_OWNER.equals(method.owner) && "opt".equals(method.name) && OPT_DESC.equals(method.desc);
    }

    /**
     * Hand-written {@code @OptChain} (precise flag absent or false). {@code precise = true}
     * without markers weaves nothing: the markers live in another method, such as a lambda.
     */
    private static boolean isLegacyAnnotation(MethodNode method) {
        Boolean precise = preciseFlag(method.invisibleAnnotations);
        if (precise == null) {
            precise = preciseFlag(method.visibleAnnotations);
        }
        return precise != null && !precise;
    }

    private static Boolean preciseFlag(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return null;
        }
        for (AnnotationNode annotation : annotations) {
            if (!OPT_CHAIN_DESC.equals(annotation.desc)) {
                continue;
            }
            if (annotation.values == null) {
                return false;
            }
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if ("precise".equals(annotation.values.get(i))) {
                    Object value = annotation.values.get(i + 1);
                    return Boolean.TRUE.equals(value) || Integer.valueOf(1).equals(value);
                }
            }
            return false;
        }
        return null;
    }

    private static boolean isLegacyAccess(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        if (opcode == Opcodes.GETFIELD) {
            return true;
        }
        if ((opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE)
                && insn instanceof MethodInsnNode method) {
            return !"<init>".equals(method.name);
        }
        return false;
    }

    private static void rewriteAccess(MethodNode method, AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        if (opcode == Opcodes.GETFIELD && insn instanceof FieldInsnNode field) {
            rewriteNullCheck(method, insn, Type.getType(field.desc), () ->
                    new FieldInsnNode(Opcodes.GETFIELD, field.owner, field.name, field.desc), NO_ARGS);
            return;
        }
        if (opcode == Opcodes.ARRAYLENGTH) {
            rewriteNullCheck(method, insn, Type.INT_TYPE, () -> new InsnNode(Opcodes.ARRAYLENGTH), NO_ARGS);
            return;
        }
        if (insn instanceof MethodInsnNode call
                && (opcode == Opcodes.INVOKEVIRTUAL
                || opcode == Opcodes.INVOKEINTERFACE
                || opcode == Opcodes.INVOKESPECIAL)
                && !"<init>".equals(call.name)) {
            rewriteNullCheck(
                    method,
                    insn,
                    Type.getReturnType(call.desc),
                    () -> new MethodInsnNode(opcode, call.owner, call.name, call.desc, call.itf),
                    Type.getArgumentTypes(call.desc));
        }
    }

    @FunctionalInterface
    private interface InsnFactory {
        AbstractInsnNode create();
    }

    private static void rewriteNullCheck(MethodNode method, AbstractInsnNode original, Type result,
                                          InsnFactory access, Type[] argTypes) {
        int cursor = method.maxLocals;
        int[] slots = new int[argTypes.length];
        InsnList rewrite = new InsnList();
        for (int i = argTypes.length - 1; i >= 0; i--) {
            slots[i] = cursor;
            rewrite.add(new VarInsnNode(argTypes[i].getOpcode(Opcodes.ISTORE), slots[i]));
            cursor += argTypes[i].getSize();
        }
        if (cursor > method.maxLocals) {
            method.maxLocals = cursor;
        }

        LabelNode nullPath = new LabelNode();
        LabelNode end = new LabelNode();
        rewrite.add(new InsnNode(Opcodes.DUP));
        rewrite.add(new JumpInsnNode(Opcodes.IFNULL, nullPath));
        for (int i = 0; i < argTypes.length; i++) {
            rewrite.add(new VarInsnNode(argTypes[i].getOpcode(Opcodes.ILOAD), slots[i]));
        }
        rewrite.add(access.create());
        rewrite.add(new JumpInsnNode(Opcodes.GOTO, end));
        rewrite.add(nullPath);
        rewrite.add(new InsnNode(Opcodes.POP));
        if (result.getSort() != Type.VOID) {
            pushDefault(rewrite, result);
        }
        rewrite.add(end);

        method.instructions.insert(original, rewrite);
        method.instructions.remove(original);
    }

    private static void pushDefault(InsnList out, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                out.add(new InsnNode(Opcodes.ICONST_0));
                break;
            case Type.LONG:
                out.add(new InsnNode(Opcodes.LCONST_0));
                break;
            case Type.FLOAT:
                out.add(new InsnNode(Opcodes.FCONST_0));
                break;
            case Type.DOUBLE:
                out.add(new InsnNode(Opcodes.DCONST_0));
                break;
            case Type.VOID:
                break;
            default:
                out.add(new InsnNode(Opcodes.ACONST_NULL));
                break;
        }
    }

    /**
     * Reference values are either unmarked or definitely produced by {@code OptChainOps.opt}.
     * All references share one type, matching {@link BasicInterpreter}'s merge rules, so the
     * analyzer does not need application classes on the plugin classpath.
     */
    private static final class MarkInterpreter extends BasicInterpreter {
        private final Set<AbstractInsnNode> marked;

        MarkInterpreter(Set<AbstractInsnNode> marked) {
            super(Opcodes.ASM9);
            this.marked = marked;
        }

        @Override
        public BasicValue newValue(Type type) {
            if (type == null) {
                return BasicValue.UNINITIALIZED_VALUE;
            }
            return switch (type.getSort()) {
                case Type.VOID -> null;
                case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> BasicValue.INT_VALUE;
                case Type.FLOAT -> BasicValue.FLOAT_VALUE;
                case Type.LONG -> BasicValue.LONG_VALUE;
                case Type.DOUBLE -> BasicValue.DOUBLE_VALUE;
                case Type.ARRAY, Type.OBJECT -> MarkedValue.REF;
                default -> super.newValue(type);
            };
        }

        @Override
        public BasicValue unaryOperation(AbstractInsnNode insn, BasicValue value) throws AnalyzerException {
            int opcode = insn.getOpcode();
            if ((opcode == Opcodes.GETFIELD || opcode == Opcodes.ARRAYLENGTH) && isMarked(value)) {
                marked.add(insn);
            }
            BasicValue result = super.unaryOperation(insn, value);
            if (opcode == Opcodes.CHECKCAST && isMarked(value)) {
                return MarkedValue.MARKED;
            }
            return result;
        }

        @Override
        public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values)
                throws AnalyzerException {
            if (isOptCall(insn)) {
                return MarkedValue.MARKED;
            }
            int opcode = insn.getOpcode();
            if ((opcode == Opcodes.INVOKEVIRTUAL
                    || opcode == Opcodes.INVOKEINTERFACE
                    || opcode == Opcodes.INVOKESPECIAL)
                    && insn instanceof MethodInsnNode method
                    && !"<init>".equals(method.name)
                    && !values.isEmpty()
                    && isMarked(values.get(0))) {
                marked.add(insn);
            }
            return super.naryOperation(insn, values);
        }

        @Override
        public BasicValue merge(BasicValue left, BasicValue right) {
            if (left.equals(right)) {
                return left;
            }
            if (isReference(left) && isReference(right)) {
                return isMarked(left) && isMarked(right) ? MarkedValue.MARKED : MarkedValue.REF;
            }
            return BasicValue.UNINITIALIZED_VALUE;
        }

        private static boolean isMarked(BasicValue value) {
            return value instanceof MarkedValue markedValue && markedValue.marked;
        }

        private static boolean isReference(BasicValue value) {
            if (value instanceof MarkedValue) {
                return true;
            }
            if (value == null || value.getType() == null) {
                return false;
            }
            int sort = value.getType().getSort();
            return sort == Type.OBJECT || sort == Type.ARRAY;
        }
    }

    private static final class MarkedValue extends BasicValue {
        static final MarkedValue REF = new MarkedValue(false);
        static final MarkedValue MARKED = new MarkedValue(true);

        private final boolean marked;

        private MarkedValue(boolean marked) {
            super(Type.getObjectType("java/lang/Object"));
            this.marked = marked;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof MarkedValue markedValue && markedValue.marked == marked;
        }

        @Override
        public int hashCode() {
            return marked ? 17 : 13;
        }
    }
}
