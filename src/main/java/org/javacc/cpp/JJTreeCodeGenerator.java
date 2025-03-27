/*
 * Copyright (c) 2020-2025, Sreeni Viswanadha <sreeni@viswanadha.net>.
 * Copyright (c) 2024-2025, Marc Mazas <mazas.marc@gmail.com>.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the names of the copyright holders nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.javacc.cpp;

import java.io.File;
import org.javacc.Version;
import org.javacc.jjtree.ASTBNFAction;
import org.javacc.jjtree.ASTBNFDeclaration;
import org.javacc.jjtree.ASTBNFNodeScope;
import org.javacc.jjtree.ASTBNFOneOrMore;
import org.javacc.jjtree.ASTBNFSequence;
import org.javacc.jjtree.ASTBNFTryBlock;
import org.javacc.jjtree.ASTBNFZeroOrMore;
import org.javacc.jjtree.ASTBNFZeroOrOne;
import org.javacc.jjtree.ASTCompilationUnit;
import org.javacc.jjtree.ASTExpansionNodeScope;
import org.javacc.jjtree.ASTGrammar;
import org.javacc.jjtree.ASTJavacodeBody;
import org.javacc.jjtree.ASTNodeDescriptor;
import org.javacc.jjtree.DefaultJJTreeVisitor;
import org.javacc.jjtree.IO;
import org.javacc.jjtree.JJTreeContext;
import org.javacc.jjtree.JJTreeGlobals;
import org.javacc.jjtree.JJTreeNode;
import org.javacc.jjtree.JJTreeParserConstants;
import org.javacc.jjtree.Node;
import org.javacc.jjtree.NodeScope;
import org.javacc.jjtree.SimpleNode;
import org.javacc.jjtree.Token;
import org.javacc.jjtree.TokenUtils;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.JavaCCGlobals;
import org.javacc.parser.Options;

class JJTreeCodeGenerator extends DefaultJJTreeVisitor {

  private static final String JJTStateVersion = Version.version;

  private final JJTreeContext context;

  JJTreeCodeGenerator(final JJTreeContext context) {
    this.context = context;
  }

  @Override
  public Object defaultVisit(final SimpleNode node, final Object data) {
    visit((JJTreeNode) node, data);
    return null;
  }

  @Override
  public Object visit(final ASTGrammar node, final Object data) {
    final IO io = (IO) data;
    if (CppCodeGenerator.IS_DEBUG) {
      io.println(
          "/*@bgen(jjtree) "
              + JavaCCGlobals.getIdString("JJTree", new File(io.getOutputFileName()).getName())
              + (Options.getIgnoreActions() ? "" : " */"));
      io.print((Options.getIgnoreActions() ? "" : "/*") + "@egen*/");
    }

    return node.childrenAccept(this, io);
  }

  @Override
  public Object visit(final ASTBNFAction node, final Object data) {
    final IO io = (IO) data;
    /*
     * Assume that this action requires an early node close, and then try to
     * decide whether this assumption is false. Do this by looking outwards
     * through the enclosing expansion units. If we ever find that we are
     * enclosed in a unit which is not the final unit in a sequence we know that
     * an early close is not required.
     */

    final NodeScope ns = NodeScope.getEnclosingNodeScope(node);
    if ((ns != null) && !ns.isVoid()) {
      boolean needClose = true;
      final Node sp = node.getScopingParent(ns);

      JJTreeNode n = node;
      while (true) {
        final Node p = n.jjtGetParent();
        if ((p instanceof ASTBNFSequence) || (p instanceof ASTBNFTryBlock)) {
          if (n.getOrdinal() != (p.jjtGetNumChildren() - 1)) {
            /* We're not the final unit in the sequence. */
            needClose = false;
            break;
          }
        } else if ((p instanceof ASTBNFZeroOrOne)
            || (p instanceof ASTBNFZeroOrMore)
            || (p instanceof ASTBNFOneOrMore)) {
          needClose = false;
          break;
        }
        if (p == sp) {
          /* No more parents to look at. */
          break;
        }
        n = (JJTreeNode) p;
      }
      if (needClose) {
        JJTreeCodeGenerator.openJJTreeComment(io, null);
        io.println();
        insertCloseNodeAction(ns, io, getIndentation(node));
        JJTreeCodeGenerator.closeJJTreeComment(io);
      }
    }

    return visit((JJTreeNode) node, io);
  }

  @Override
  public Object visit(final ASTBNFDeclaration node, final Object data) {
    final IO io = (IO) data;
    if (!node.node_scope.isVoid()) {
      String indent = "";
      if (TokenUtils.hasTokens(node)) {
        for (int i = 1; i < node.getFirstToken().beginColumn; ++i) {
          indent += " ";
        }
      } else {
        indent = "  ";
      }

      JJTreeCodeGenerator.openJJTreeComment(io, node.node_scope.getNodeDescriptorText());
      io.println();
      insertOpenNodeCode(node.node_scope, io, indent);
      JJTreeCodeGenerator.closeJJTreeComment(io);
    }

    return visit((JJTreeNode) node, io);
  }

  @Override
  public Object visit(final ASTBNFNodeScope node, final Object data) {
    final IO io = (IO) data;
    if (node.node_scope.isVoid()) {
      return visit((JJTreeNode) node, io);
    }

    final String indent = getIndentation(node.expansion_unit);

    JJTreeCodeGenerator.openJJTreeComment(io, node.node_scope.getNodeDescriptor().getDescriptor());
    io.println();
    tryExpansionUnit(node.node_scope, io, indent, node.expansion_unit);
    return null;
  }

  @Override
  public Object visit(final ASTCompilationUnit node, final Object data) {
    final IO io = (IO) data;
    Token t = node.getFirstToken();
    while (true) {
      node.print(t, io);
      if (t == node.getLastToken()) {
        break;
      }
      if (t.kind == JJTreeParserConstants._PARSER_BEGIN) {
        // eat PARSER_BEGIN "(" <ID> ")"
        node.print(t.next, io);
        node.print(t.next.next, io);
        node.print(t = t.next.next.next, io);
      }

      t = t.next;
    }
    return null;
  }

  @Override
  public Object visit(final ASTExpansionNodeScope node, final Object data) {
    final IO io = (IO) data;
    final String indent = getIndentation(node.expansion_unit);
    JJTreeCodeGenerator.openJJTreeComment(io, node.node_scope.getNodeDescriptor().getDescriptor());
    io.println();
    insertOpenNodeAction(node.node_scope, io, indent);
    tryExpansionUnit(node.node_scope, io, indent, node.expansion_unit);

    // Print the "whiteOut" equivalent of the Node descriptor to preserve
    // line numbers in the generated file.
    ((ASTNodeDescriptor) node.jjtGetChild(1)).jjtAccept(this, io);
    return null;
  }

  @Override
  public Object visit(final ASTJavacodeBody node, final Object data) {
    final IO io = (IO) data;
    if (node.node_scope.isVoid()) {
      return visit((JJTreeNode) node, io);
    }

    final Token first = node.getFirstToken();

    String indent = "";
    for (int i = 4; i < first.beginColumn; ++i) {
      indent += " ";
    }

    JJTreeCodeGenerator.openJJTreeComment(io, node.node_scope.getNodeDescriptorText());
    io.println();
    insertOpenNodeCode(node.node_scope, io, indent);
    tryTokenSequence(node.node_scope, io, indent, first, node.getLastToken());
    return null;
  }

  /*
   * This method prints the tokens corresponding to this node recursively
   * calling the print methods of its children. Overriding this print method in
   * appropriate nodes gives the output the added stuff not in the input.
   */
  private Object visit(final JJTreeNode node, final Object data) {
    final IO io = (IO) data;
    /*
     * Some productions do not consume any tokens. In that case their first and
     * last tokens are a bit strange.
     */
    if (node.getLastToken().next == node.getFirstToken()) {
      return null;
    }

    final Token t1 = node.getFirstToken();
    Token t = new Token();
    t.next = t1;
    JJTreeNode n;
    for (int ord = 0; ord < node.jjtGetNumChildren(); ord++) {
      n = (JJTreeNode) node.jjtGetChild(ord);
      while (true) {
        t = t.next;
        if (t == n.getFirstToken()) {
          break;
        }
        node.print(t, io);
      }
      n.jjtAccept(this, io);
      t = n.getLastToken();
    }
    while (t != node.getLastToken()) {
      t = t.next;
      node.print(t, io);
    }

    return null;
  }

  private static void openJJTreeComment(final IO io, final String arg) {
    if (CppCodeGenerator.IS_DEBUG) {
      if (arg != null) {
        io.print("/*@bgen(jjtree) " + arg + (Options.getIgnoreActions() ? "" : " */"));
      } else {
        io.print("/*@bgen(jjtree)" + (Options.getIgnoreActions() ? "" : "*/"));
      }
    }
  }

  private static void closeJJTreeComment(final IO io) {
    if (CppCodeGenerator.IS_DEBUG) {
      io.print((Options.getIgnoreActions() ? "" : "/*") + "@egen*/");
    }
  }

  private String getIndentation(final JJTreeNode n) {
    return getIndentation(n, 0);
  }

  private String getIndentation(final JJTreeNode n, final int offset) {
    String s = "";
    for (int i = offset + 1; i < n.getFirstToken().beginColumn; ++i) {
      s += " ";
    }
    return s;
  }

  private void insertOpenNodeCode(final NodeScope ns, final IO io, final String indent) {
    final String type = ns.node_descriptor.getNodeType(context);
    final String nodeClass;
    if ((context.treeOptions().getNodeClass().length() > 0) && !context.treeOptions().getMulti()) {
      nodeClass = context.treeOptions().getNodeClass();
    } else {
      nodeClass = type;
    }

    /*
     * Ensure that there is a template definition file for the node type.
     */
    NodeFiles.generateNodeType(type);

    io.print(indent + nodeClass + " *" + ns.nodeVar + " = ");
    final String p = Options.getStatic() ? "null" : "this";
    final String parserArg = context.treeOptions().getNodeUsesParser() ? (p + ", ") : "";

    if (context.treeOptions().getNodeFactory().equals("*")) {
      // Old-style multiple-implementations.
      io.println(
          "("
              + nodeClass
              + "*)"
              + nodeClass
              + "::jjtCreate("
              + parserArg
              + ns.node_descriptor.getNodeId()
              + ");");
    } else if (context.treeOptions().getNodeFactory().length() > 0) {
      io.println(
          "("
              + nodeClass
              + "*)nodeFactory->jjtCreate("
              + parserArg
              + ns.node_descriptor.getNodeId()
              + ");");
    } else {
      io.println("new " + nodeClass + "(" + parserArg + ns.node_descriptor.getNodeId() + ");");
    }

    if (ns.usesCloseNodeVar()) {
      io.println(indent + "bool " + ns.closedVar + " = true;");
    }
    io.println(indent + ns.node_descriptor.openNode(ns.nodeVar));
    if (context.treeOptions().getNodeScopeHook()) {
      io.println(indent + "jjtreeOpenNodeScope(" + ns.nodeVar + ");");
    }

    if (context.treeOptions().getTrackTokens()) {
      io.println(indent + ns.nodeVar + "->jjtSetFirstToken(getToken(1));");
    }
  }

  private void insertCloseNodeCode(
      final NodeScope ns, final IO io, final String indent, final boolean isFinal) {
    final String closeNode = ns.node_descriptor.closeNode(ns.nodeVar);
    io.println(indent + closeNode);
    if (ns.usesCloseNodeVar() && !isFinal) {
      io.println(indent + ns.closedVar + " = false;");
    }
    if (context.treeOptions().getNodeScopeHook()) {
      io.println(indent + "if (jjtree.nodeCreated()) {");
      io.println(indent + " jjtreeCloseNodeScope(" + ns.nodeVar + ");");
      io.println(indent + "}");
    }

    if (context.treeOptions().getTrackTokens()) {
      io.println(indent + ns.nodeVar + "->jjtSetLastToken(getToken(0));");
    }
  }

  private void insertOpenNodeAction(final NodeScope ns, final IO io, final String indent) {
    io.println(indent + "{");
    insertOpenNodeCode(ns, io, indent + "  ");
    io.println(indent + "}");
  }

  private void insertCloseNodeAction(final NodeScope ns, final IO io, final String indent) {
    io.println(indent + "{");
    insertCloseNodeCode(ns, io, indent + "  ", false);
    io.println(indent + "}");
  }

  private void insertCatchBlocks(final NodeScope ns, final IO io, final String indent) {
    io.println(indent + "} catch (...) {"); // " + ns.exceptionVar + ") {");

    if (ns.usesCloseNodeVar()) {
      io.println(indent + "  if (" + ns.closedVar + ") {");
      io.println(indent + "    jjtree.clearNodeScope(" + ns.nodeVar + ");");
      io.println(indent + "    " + ns.closedVar + " = false;");
      io.println(indent + "  } else {");
      io.println(indent + "    jjtree.popNode();");
      io.println(indent + "  }");
    }
  }

  private void tryTokenSequence(
      final NodeScope ns, final IO io, final String indent, final Token first, final Token last) {
    io.println(indent + "try {");
    JJTreeCodeGenerator.closeJJTreeComment(io);

    /*
     * Print out all the tokens, converting all references to `jjtThis' into the
     * current node variable.
     */
    for (Token t = first; t != last.next; t = t.next) {
      TokenUtils.print(t, io, "jjtThis", ns.nodeVar);
    }

    JJTreeCodeGenerator.openJJTreeComment(io, null);
    io.println();

    insertCatchBlocks(ns, io, indent);

    io.println(indent + "} {");
    if (ns.usesCloseNodeVar()) {
      io.println(indent + "  if (" + ns.closedVar + ") {");
      insertCloseNodeCode(ns, io, indent + "    ", true);
      io.println(indent + "  }");
    }
    io.println(indent + "}");
    JJTreeCodeGenerator.closeJJTreeComment(io);
  }

  private void tryExpansionUnit(
      final NodeScope ns, final IO io, final String indent, final JJTreeNode expansion_unit) {
    io.println(indent + "try {");
    JJTreeCodeGenerator.closeJJTreeComment(io);

    expansion_unit.jjtAccept(this, io);

    JJTreeCodeGenerator.openJJTreeComment(io, null);
    io.println();

    insertCatchBlocks(ns, io, indent);

    io.println(indent + "} {");
    if (ns.usesCloseNodeVar()) {
      io.println(indent + "  if (" + ns.closedVar + ") {");
      insertCloseNodeCode(ns, io, indent + "    ", true);
      io.println(indent + "  }");
    }
    io.println(indent + "}");
    JJTreeCodeGenerator.closeJJTreeComment(io);
  }

  @Override
  public void generateHelperFiles() throws java.io.IOException {
    final CodeGeneratorSettings options = CodeGeneratorSettings.of(Options.getOptions());
    options.set(Options.NUO__PARSER_NAME, JJTreeGlobals.parserName);

    try (CppCodeBuilder builder = CppCodeBuilder.of(context, options)) {
      builder.setFile(
          new File(
              context.treeOptions().getJJTreeOutputDirectory(),
              "JJT" + JJTreeGlobals.parserName + "State.cc"));
      builder.setVersion(JJTreeCodeGenerator.JJTStateVersion).addOption(JavaCCGlobals.toolName);

      builder.printTemplate("/templates/cpp/JJTTreeState.cc.template");
      builder.switchToIncludeFile();
      builder.printTemplate("/templates/cpp/JJTTreeState.h.template");
    }

    NodeFiles.generateOutputFiles(context);
  }
}
