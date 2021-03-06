/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.checks;

import org.sonar.check.Rule;
import org.sonar.java.JavaVersionAwareVisitor;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.JavaVersion;
import org.sonar.plugins.java.api.tree.Arguments;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.ExpressionStatementTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.LambdaExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.ReturnStatementTree;
import org.sonar.plugins.java.api.tree.Tree;

import javax.annotation.Nullable;

import java.util.List;
import java.util.stream.IntStream;

@Rule(key = "S1612")
public class ReplaceLambdaByMethodRefCheck extends BaseTreeVisitor implements JavaFileScanner, JavaVersionAwareVisitor {

  private JavaFileScannerContext context;

  @Override
  public boolean isCompatibleWithJavaVersion(JavaVersion version) {
    return version.isJava8Compatible();
  }

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.context = context;
    if (context.getSemanticModel() != null) {
      scan(context.getTree());
    }
  }

  @Override
  public void visitLambdaExpression(LambdaExpressionTree tree) {
    if (isSingleMethodInvocationUsingLambdaParamAsArg(tree) || isBodyBlockInvokingMethod(tree)) {
      context.reportIssue(this, tree.arrowToken(), "Replace this lambda with a method reference." + context.getJavaVersion().java8CompatibilityMessage());
    }
    super.visitLambdaExpression(tree);
  }

  private static boolean isSingleMethodInvocationUsingLambdaParamAsArg(LambdaExpressionTree lambdaTree) {
    return isMethodInvocation(lambdaTree.body(), lambdaTree);
  }

  private static boolean isBodyBlockInvokingMethod(LambdaExpressionTree lambdaTree) {
    Tree lambdaBody = lambdaTree.body();
    if (isBlockWithOneStatement(lambdaBody)) {
      Tree statement = ((BlockTree) lambdaBody).body().get(0);
      return isExpressionStatementInvokingMethod(statement, lambdaTree) || isReturnStatementInvokingMethod(statement, lambdaTree);
    }
    return false;
  }

  private static boolean isBlockWithOneStatement(Tree tree) {
    return tree.is(Tree.Kind.BLOCK) && ((BlockTree) tree).body().size() == 1;
  }

  private static boolean isExpressionStatementInvokingMethod(Tree statement, LambdaExpressionTree lambdaTree) {
    return statement.is(Tree.Kind.EXPRESSION_STATEMENT) && isMethodInvocation(((ExpressionStatementTree) statement).expression(), lambdaTree);
  }

  private static boolean isReturnStatementInvokingMethod(Tree statement, LambdaExpressionTree lambdaTree) {
    return statement.is(Tree.Kind.RETURN_STATEMENT) && isMethodInvocation(((ReturnStatementTree) statement).expression(), lambdaTree);
  }

  private static boolean isMethodInvocation(@Nullable Tree tree, LambdaExpressionTree lambdaTree) {
    if (tree != null && tree.is(Tree.Kind.METHOD_INVOCATION, Tree.Kind.NEW_CLASS)) {
      Arguments arguments;
      if(tree.is(Tree.Kind.NEW_CLASS)) {
        if(((NewClassTree) tree).classBody() != null) {
          return false;
        }
        arguments = ((NewClassTree) tree).arguments();
      } else {
        arguments = ((MethodInvocationTree) tree).arguments();
      }
      return arguments.size() == lambdaTree.parameters().size() &&
        IntStream.range(0, arguments.size()).allMatch(i -> {
          List<IdentifierTree> usages = lambdaTree.parameters().get(i).symbol().usages();
          return usages.size() == 1 && usages.get(0).equals(arguments.get(i));
        });
    }
    return false;
  }

}
