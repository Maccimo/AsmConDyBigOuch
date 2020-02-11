/*
 *
 * MIT License
 * 
 * Copyright (c) 2020 Maxim Degtyarev
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package com.maccimo.asm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;

/**
 *
 * Use of dynamic constant as a static parameter for another dynamic constant while generating class with OW2 ASM 
 * lead to significant performance degradation.
 *
 */
@Fork(1)
@State(Scope.Benchmark)
@SuppressWarnings("SpellCheckingInspection")
public class ChainedConDyGenerationBenchmark {

    public static final String CLASS_NAME = "TestConDyChain";

    public static final String BOOTSTRAP_METHOD_NAME = "nextValue";

    private static final String BOOTSTRAP_METHOD_DESCRIPTOR = "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;[I)I";

    @Param({"1", "8", "16", "32"})
    public int chainLength;

    @Benchmark
    public byte[] testConDyChainGeneration() {
        List<String> names = generateNames(chainLength);

        return generateClassBytes(names);
    }

    private List<String> generateNames(int chainLength) {
        return IntStream
            .range(0, chainLength)
            .mapToObj(index -> String.format("VALUE_%05d", index))
            .collect(Collectors.toList());
    }

    private byte[] generateClassBytes(List<String> names) {

        int count = names.size();

        ConstantDynamic[] dynamics = new ConstantDynamic[count];

        // First ConDy has no previous value
        Object[] previous = new ConstantDynamic[0];

        for (int i = 0; i < count; i++) {
            ConstantDynamic next = new ConstantDynamic(
                names.get(i),
                "I",
                new Handle(
                    Opcodes.H_INVOKESTATIC,
                    CLASS_NAME,
                    BOOTSTRAP_METHOD_NAME,
                    BOOTSTRAP_METHOD_DESCRIPTOR,
                    false
                ),
                previous
            );
            dynamics[i] = next;
            previous = new ConstantDynamic[] { next };
        }

        ClassWriter classWriter = new ClassWriter(0);

        classWriter.visit(V11, ACC_PUBLIC | ACC_SUPER, CLASS_NAME, null, "java/lang/Object", null);

        classWriter.visitInnerClass("java/lang/invoke/MethodHandles$Lookup", "java/lang/invoke/MethodHandles", "Lookup", ACC_PUBLIC | ACC_FINAL | ACC_STATIC);

        generateConstructor(classWriter);
        generateMain(classWriter, dynamics[dynamics.length - 1]);
        generateBootstrapMethod(classWriter);
        classWriter.visitEnd();

        return classWriter.toByteArray();

    }

    private void generateMain(ClassWriter classWriter, ConstantDynamic dynamic) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_VARARGS, "main", "([Ljava/lang/String;)V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
        methodVisitor.visitLdcInsn(dynamic);
        methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(2, 1);
        methodVisitor.visitEnd();
    }

    private void generateBootstrapMethod(ClassWriter classWriter) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PRIVATE | ACC_STATIC | ACC_VARARGS, BOOTSTRAP_METHOD_NAME, BOOTSTRAP_METHOD_DESCRIPTOR, null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitInsn(ARRAYLENGTH);
        Label label0 = new Label();
        methodVisitor.visitJumpInsn(IFNE, label0);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitLabel(label0);
        methodVisitor.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        methodVisitor.visitVarInsn(ALOAD, 3);
        methodVisitor.visitInsn(ICONST_0);
        methodVisitor.visitInsn(IALOAD);
        methodVisitor.visitInsn(ICONST_1);
        methodVisitor.visitInsn(IADD);
        methodVisitor.visitInsn(IRETURN);
        methodVisitor.visitMaxs(2, 4);
        methodVisitor.visitEnd();
    }

    private void generateConstructor(ClassWriter classWriter) {
        MethodVisitor methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(ALOAD, 0);
        methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
    }

}
