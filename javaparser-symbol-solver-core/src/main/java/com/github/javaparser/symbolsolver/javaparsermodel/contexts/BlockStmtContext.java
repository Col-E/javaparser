/*
 * Copyright (C) 2015-2016 Federico Tomassetti
 * Copyright (C) 2017-2020 The JavaParser Team.
 *
 * This file is part of JavaParser.
 *
 * JavaParser can be used either under the terms of
 * a) the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * b) the terms of the Apache License
 *
 * You should have received a copy of both licenses in LICENCE.LGPL and
 * LICENCE.APACHE. Please refer to those files for details.
 *
 * JavaParser is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 */

package com.github.javaparser.symbolsolver.javaparsermodel.contexts;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.symbolsolver.core.resolution.Context;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFactory;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

public class BlockStmtContext extends AbstractJavaParserContext<BlockStmt> {

    public BlockStmtContext(BlockStmt wrappedNode, TypeSolver typeSolver) {
        super(wrappedNode, typeSolver);
    }

    @Override
    public List<VariableDeclarator> localVariablesExposedToChild(Node child) {
        int position = -1;
        for (int i = 0; i < wrappedNode.getStatements().size(); i++) {
            if (wrappedNode.getStatements().get(i).equals(child)) {
                position = i;
            }
        }
        if (position == -1) {
            throw new RuntimeException();
        }
        List<VariableDeclarator> variableDeclarators = new LinkedList<>();
        for (int i = position - 1; i >= 0; i--) {
            variableDeclarators.addAll(localVariablesDeclaredIn(wrappedNode.getStatement(i)));
        }
        return variableDeclarators;
    }

    private List<VariableDeclarator> localVariablesDeclaredIn(Statement statement) {
        if (statement instanceof ExpressionStmt) {
            ExpressionStmt expressionStmt = (ExpressionStmt) statement;
            if (expressionStmt.getExpression() instanceof VariableDeclarationExpr) {
                VariableDeclarationExpr variableDeclarationExpr = (VariableDeclarationExpr) expressionStmt.getExpression();
                return Collections.unmodifiableList(variableDeclarationExpr.getVariables());
            }
        }
        return Collections.emptyList();
    }

    @Override
    public SymbolReference<? extends ResolvedValueDeclaration> solveSymbol(String name) {
        Optional<Context> optionalParent = getParent();
        if (!optionalParent.isPresent()) {
            return SymbolReference.unsolved(ResolvedValueDeclaration.class);
        }

        Optional<Node> optionalParentNode = wrappedNode.getParentNode();
        if(!optionalParentNode.isPresent()) {
            return SymbolReference.unsolved(ResolvedValueDeclaration.class);
        }

        Node parentOfWrappedNode = optionalParentNode.get();

        // we should look in all the statements preceding, treating them as SymbolDeclarators
        if (parentOfWrappedNode instanceof MethodDeclaration) {
            return solveSymbolInParentContext(name);
        } else if (parentOfWrappedNode instanceof ConstructorDeclaration) {
            return solveSymbolInParentContext(name);
        } else if (parentOfWrappedNode instanceof LambdaExpr) {
            return solveSymbolInParentContext(name);
        } else if (parentOfWrappedNode instanceof NodeWithStatements) {
            NodeWithStatements<?> nodeWithStmt = (NodeWithStatements<?>) parentOfWrappedNode;

            // Assuming the wrapped node exists within the parent's collection of statements...
            int position = nodeWithStmt.getStatements().indexOf(wrappedNode);
            if (position == -1) {
                throw new IllegalStateException("This node is not a statement within the current NodeWithStatements");
            }

            // Start at the current node and work backwards...
            ListIterator<Statement> statementListIterator = nodeWithStmt.getStatements().listIterator(position);
            while(statementListIterator.hasPrevious()) {
                Context prevContext = JavaParserFactory.getContext(statementListIterator.previous(), typeSolver);
                SymbolReference<? extends ResolvedValueDeclaration> symbolReference = prevContext.solveSymbol(name);
                if (symbolReference.isSolved())
                {
                    return symbolReference;
                }
            }
        }

        /*
        if (wrappedNode.getStatements().size() > 0) {
            // tries to resolve a declaration from local variables defined in child statements
            // or from parent node context
            // for example resolve declaration for the MethodCallExpr a.method() in
            // A a = this;
            // {
            //   a.method();
            // }

            List<VariableDeclarator> variableDeclarators = new LinkedList<>();
            // find all variable declarators exposed in child
            wrappedNode.getStatements().forEach(stmt -> variableDeclarators.addAll(localVariablesExposedToChild(stmt)));
            if (!variableDeclarators.isEmpty()) {
                // FIXME: Work backwards from the current statement, to only consider declarations prior to this statement.
                for (VariableDeclarator vd : variableDeclarators) {
                    if (vd.getNameAsString().equals(name)) {
                        return SymbolReference.solved(JavaParserSymbolDeclaration.localVar(vd, typeSolver));
                    }
                }
            }
        }*/

        // Otherwise continue as normal...
        return solveSymbolInParentContext(name);
    }
}
