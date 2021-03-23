package com.github.javaparser.symbolsolver;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class Issue2477Test extends AbstractSymbolResolutionTest {

    @Test
    public void test() {
        
        TypeSolver typeSolver = new ReflectionTypeSolver();
        ParserConfiguration config = new ParserConfiguration();
        config.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        StaticJavaParser.setConfiguration(config);

        String s = 
                "class A {\n" + 
                "    void method() {\n" +
                "        A a = this;\n";
        for (int i = 0; i < 10_000; i++)
        {
            s += "A _" + i + "  = this;\n";
        }
        s +=
                "        {\n" + 
                "            a.method();\n" + 
                "        }\n" + 
                "    }\n" + 
                "}";
        CompilationUnit cu = StaticJavaParser.parse(s);
        MethodCallExpr mce = cu.findFirst(MethodCallExpr.class).get();
        assertEquals("A.method", mce.resolve().getQualifiedName());
    }

}
