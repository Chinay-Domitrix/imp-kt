package org.imp.jvm.compiler;

import org.imp.jvm.codegen.BytecodeGenerator;
import org.imp.jvm.domain.ImpFile;

import org.imp.jvm.parser.Parser;
import org.objectweb.asm.Opcodes;

import java.io.*;

public class Compiler {

    public static void main(String[] args) throws IOException {
        new Compiler().compile(args[0]);
    }

    public void compile(String filename) throws IOException {
        System.out.println("yeee");
        System.out.println(Opcodes.DUP_X1);

        File source = new File(filename);
        ImpFile impFile = Parser.getImpFile(source);


        saveByteCodeToClassFile(impFile);

    }

    public void saveByteCodeToClassFile(ImpFile impFile) throws IOException {
        BytecodeGenerator bytecodeGenerator = new BytecodeGenerator();
        byte[] bytes = bytecodeGenerator.generate(impFile);
        String className = impFile.getClassName();
        String fileName = ".compile/" + className + ".txt";
        OutputStream output = new FileOutputStream(fileName);
        output.write(bytes);
        output.flush();
        output.close();
    }
}
