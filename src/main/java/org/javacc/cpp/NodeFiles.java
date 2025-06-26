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
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.javacc.Version;
import org.javacc.jjtree.ASTNodeDescriptor;
import org.javacc.jjtree.JJTreeContext;
import org.javacc.jjtree.JJTreeGlobals;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.Options;

final class NodeFiles {

  private NodeFiles() {}

  private static List<String> headersForJJTreeH = new ArrayList<>();

  /** ID of the latest version (of JJTree) in which one of the Node classes was modified. */
  private static final String nodeVersion = Version.version;

  private static Set<String> nodesToBuild = new HashSet<>();

  static void generateNodeType(final String nodeType) {
    if (!nodeType.equals("Tree") && !nodeType.equals("Node")) {
      nodesToBuild.add(nodeType);
    }
  }

  private static String nodeIncludeFile(final File outputDirectory) {
    return new File(outputDirectory, "Tree.h").getAbsolutePath();
  }

  private static String simpleNodeCodeFile(final File outputDirectory) {
    return new File(outputDirectory, "Node.cc").getAbsolutePath();
  }

  private static String jjtreeIncludeFile(final File outputDirectory) {
    return new File(outputDirectory, JJTreeGlobals.parserName + "Tree.h").getAbsolutePath();
  }

  private static String visitorIncludeFile(final File outputDirectory) {
    final String name = visitorClass();
    return new File(outputDirectory, name + ".h").getAbsolutePath();
  }

  static void generateOutputFiles(final JJTreeContext context) throws IOException {
    generateNodeHeader(context);
    generateSimpleNode(context);
    generateOneTree(context, false);
    generateMultiTree(context);
    generateTreeConstants(context);
    generateVisitors(context);
  }

  private static void generateNodeHeader(final JJTreeContext context) {
    final CodeGeneratorSettings optionMap = CodeGeneratorSettings.of(Options.getOptions());
    optionMap.set("PARSER_NAME", JJTreeGlobals.parserName);
    optionMap.set("VISITOR_RETURN_TYPE", getVisitorReturnType());
    optionMap.set("VISITOR_DATA_TYPE", getVisitorDataType());
    optionMap.set(
        "VISITOR_RETURN_TYPE_VOID", Boolean.valueOf(getVisitorReturnType().equals("void")));

    try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, optionMap)) {
      ccb.setFile(new File(nodeIncludeFile(context.treeOptions().getJJTreeOutputDirectory())));
      ccb.setVersion(nodeVersion).addTools(JJTreeGlobals.toolName);
      ccb.addOption(
          "MULTI",
          "NODE_USES_PARSER",
          "VISITOR",
          "TRACK_TOKENS",
          "NODE_PREFIX",
          "NODE_EXTENDS",
          "NODE_FACTORY",
          "SUPPORT_CLASS_VISIBILITY_PUBLIC");

      ccb.printTemplate("/templates/cpp/Tree.h.template");
    } catch (final IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generateSimpleNode(final JJTreeContext context) {
    final CodeGeneratorSettings optionMap = CodeGeneratorSettings.of(Options.getOptions());
    optionMap.set(Options.NUO__PARSER_NAME, JJTreeGlobals.parserName);
    optionMap.set("VISITOR_RETURN_TYPE", getVisitorReturnType());
    optionMap.set("VISITOR_DATA_TYPE", getVisitorDataType());
    optionMap.set(
        "VISITOR_RETURN_TYPE_VOID", Boolean.valueOf(getVisitorReturnType().equals("void")));

    try (CppCodeBuilder ccb = CppCodeBuilder.of(context, optionMap)) {
      ccb.setFile(new File(simpleNodeCodeFile(context.treeOptions().getJJTreeOutputDirectory())));
      ccb.setVersion(nodeVersion).addTools(JJTreeGlobals.toolName);
      ccb.addOption(
          "MULTI",
          "NODE_USES_PARSER",
          "VISITOR",
          "TRACK_TOKENS",
          "NODE_PREFIX",
          "NODE_EXTENDS",
          "NODE_FACTORY",
          Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);

      ccb.printTemplate("/templates/cpp/Node.cc.template");
      ccb.switchToIncludeFile();
      ccb.printTemplate("/templates/cpp/Node.h.template");
    } catch (final IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generateOneTree(
      final JJTreeContext context, final boolean generateOneTreeImpl) {
    final CodeGeneratorSettings optionMap = CodeGeneratorSettings.of(Options.getOptions());
    optionMap.set("PARSER_NAME", JJTreeGlobals.parserName);
    optionMap.set("VISITOR_RETURN_TYPE", getVisitorReturnType());
    optionMap.set("VISITOR_DATA_TYPE", getVisitorDataType());
    optionMap.set(
        "VISITOR_RETURN_TYPE_VOID", Boolean.valueOf(getVisitorReturnType().equals("void")));

    try (CppCodeBuilder ccb =
        generateOneTreeImpl
            ? CppCodeBuilder.of(context, optionMap)
            : CppCodeBuilder.ofHeader(context, optionMap)) {
      ccb.setFile(new File(jjtreeIncludeFile(context.treeOptions().getJJTreeOutputDirectory())));
      ccb.setVersion(nodeVersion).addTools(JJTreeGlobals.toolName);
      ccb.addOption(
          "MULTI",
          "NODE_USES_PARSER",
          "VISITOR",
          "TRACK_TOKENS",
          "NODE_PREFIX",
          "NODE_EXTENDS",
          "NODE_FACTORY",
          "SUPPORT_CLASS_VISIBILITY_PUBLIC");

      ccb.switchToIncludeFile();
      final String guard = "JAVACC_" + "ONE_TREE_H";
      ccb.println("#ifndef " + guard);
      ccb.println("#define " + guard);
      ccb.println();
      ccb.println("#include \"Node.h\"");
      for (final String s : nodesToBuild) {
        ccb.println("#include \"" + s + ".h\"");
        if (generateOneTreeImpl) {
          ccb.switchToMainFile();
          ccb.printTemplate(
              "/templates/cpp/MultiNode.cc.template",
              CodeGeneratorSettings.create().set("NODE_TYPE", s));
          ccb.switchToIncludeFile();
        }
      }
      ccb.println("#endif");
    } catch (final IOException e) {
      throw new Error(e.toString());
    }
  }

  private static void generateMultiTree(final JJTreeContext context) {
    //    System.out.println(" * nodeDirectory : " + context.treeOptions().getASTNodeDirectory());
    //    //    System.out.println(" * nodePackage : " + context.treeOptions().getNodePackage());
    //    System.out.println(
    //        " * jjtreeOutputDirectory : " + context.treeOptions().getJJTreeOutputDirectory());
    //    System.out.println(" * nodesToBuild : " + nodesToBuild);
    for (final String node : nodesToBuild) {
      final File file =
          new File(
              //              new File(
              context.treeOptions().getNodeDirectory(),
              //                  context.treeOptions().getNodePackage()),
              node + ".cc");
      if (file.exists()) {
        continue;
      }

      final CodeGeneratorSettings optionMap = CodeGeneratorSettings.of(Options.getOptions());
      optionMap.set(Options.NUO__PARSER_NAME, JJTreeGlobals.parserName);
      optionMap.set("VISITOR_RETURN_TYPE", getVisitorReturnType());
      optionMap.set("VISITOR_DATA_TYPE", getVisitorDataType());
      optionMap.set(
          "VISITOR_RETURN_TYPE_VOID", Boolean.valueOf(getVisitorReturnType().equals("void")));
      optionMap.set("NODE_TYPE", node);

      try (CppCodeBuilder ccb = CppCodeBuilder.of(context, optionMap)) {
        ccb.setFile(
            new File(
                //                new File(
                context.treeOptions().getJJTreeOutputDirectory(),
                //                    context.treeOptions().getNodePackage()),
                node + ".cc"));
        ccb.setVersion(nodeVersion).addTools(JJTreeGlobals.toolName);
        ccb.addOption(
            "MULTI",
            "NODE_USES_PARSER",
            "VISITOR",
            "TRACK_TOKENS",
            "NODE_PREFIX",
            "NODE_EXTENDS",
            "NODE_FACTORY",
            Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);

        ccb.printTemplate("/templates/cpp/MultiNode.cc.template");
        ccb.switchToIncludeFile();
        ccb.printTemplate("/templates/cpp/MultiNode.h.template");
      } catch (final IOException e) {
        throw new Error(e.toString());
      }
    }
  }

  private static String nodeConstants() {
    return JJTreeGlobals.parserName + "TreeConstants";
  }

  private static void generateTreeConstants(final JJTreeContext context) {
    final List<String> nodeIds = ASTNodeDescriptor.getNodeIds();
    final List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

    final File file =
        new File(context.treeOptions().getJJTreeOutputDirectory(), nodeConstants() + ".h");
    headersForJJTreeH.add(file.getName());

    try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, CodeGeneratorSettings.create())) {
      ccb.setFile(file);

      final String guard = "JAVACC_" + nodeConstants().toUpperCase() + "_H";
      ccb.println("#ifndef " + guard);
      ccb.println("#define " + guard);
      ccb.println();
      ccb.println("#include \"JavaCC.h\"");

      if (Options.hasNamespace()) {
        ccb.println("namespace " + Options.stringValue("NAMESPACE_OPEN"));
      }

      ccb.println("enum {");
      for (int i = 0; i < nodeIds.size(); ++i) {
        final String n = nodeIds.get(i);
        ccb.println("  " + n + " = " + i + ",");
      }

      ccb.println("};");
      ccb.println();

      for (int i = 0; i < nodeNames.size(); ++i) {
        ccb.println("  static JJChar jjtNodeName_arr_", i, "[] = ");
        ccb.printCharArray(nodeNames.get(i));
        ccb.println(";");
      }
      ccb.println("  static JJString jjtNodeName[] = {");
      for (int i = 0; i < nodeNames.size(); i++) {
        ccb.println("jjtNodeName_arr_", i, ", ");
      }
      ccb.println("  };");

      if (Options.hasNamespace()) {
        ccb.println(Options.stringValue("NAMESPACE_CLOSE"));
      }
      ccb.println("#endif");
    } catch (final IOException e) {
      throw new Error(e);
    }
  }

  private static String visitorClass() {
    return JJTreeGlobals.parserName + "Visitor";
  }

  private static String getVisitMethodName(final String className) {
    final StringBuffer sb = new StringBuffer("visit");
    if (Options.booleanValue("VISITOR_METHOD_NAME_INCLUDES_TYPE_NAME")) {
      sb.append(Character.toUpperCase(className.charAt(0)));
      for (int i = 1; i < className.length(); i++) {
        sb.append(className.charAt(i));
      }
    }

    return sb.toString();
  }

  private static String getVisitorDataType() {
    final String ret = Options.stringValue("VISITOR_DATA_TYPE");
    return (ret == null || ret.equals("") || ret.equals("Object")) ? "void*" : ret;
  }

  private static boolean getVisitorDataTypeIsPointer() {
    return Options.booleanValue("VISITOR_DATA_TYPE_IS_POINTER");
  }

  private static String getVisitorReturnType() {
    final String ret = Options.stringValue("VISITOR_RETURN_TYPE");
    return (ret == null) || ret.equals("") || ret.equals("Object") ? "void" : ret;
  }

  private static void generateVisitors(final JJTreeContext context) {
    if (!context.treeOptions().getVisitor()) {
      return;
    }

    try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, CodeGeneratorSettings.create())) {
      ccb.setFile(new File(visitorIncludeFile(context.treeOptions().getJJTreeOutputDirectory())));

      final String guard = "JAVACC_" + JJTreeGlobals.parserName.toUpperCase() + "_VISITOR_H";
      ccb.println("#ifndef " + guard);
      ccb.println("#define " + guard);
      ccb.println();
      ccb.println("#include \"JavaCC.h\"");
      ccb.println("#include \"" + JJTreeGlobals.parserName + "Tree.h" + "\"");

      if (Options.hasNamespace()) {
        ccb.println("namespace " + Options.stringValue("NAMESPACE_OPEN"));
      }

      generateVisitorInterface(ccb, context);
      generateDefaultVisitor(ccb, context);

      if (Options.hasNamespace()) {
        ccb.println(Options.stringValue("NAMESPACE_CLOSE"));
      }
      ccb.println("#endif");
    } catch (final IOException e) {
      throw new Error(e);
    }
  }

  private static void generateVisitorInterface(
      final CppCodeBuilder ccb, final JJTreeContext context) {
    final String name = visitorClass();
    final List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

    ccb.println("class " + name);
    ccb.println("{");

    String argumentType = getVisitorDataType();
    final String returnType = getVisitorReturnType();
    if (!context.treeOptions().getVisitorDataType().equals("")) {
      argumentType = context.treeOptions().getVisitorDataType();
    }
    ccb.println("public:");

    ccb.println(
        "  virtual " + returnType + " visit(const Node *node, " + argumentType + " data) = 0;");
    if (context.treeOptions().getMulti()) {
      for (final String n : nodeNames) {
        if (n.equals("void")) {
          continue;
        }
        final String nodeType = context.treeOptions().getNodePrefix() + n;
        ccb.println(
            "  virtual "
                + returnType
                + " "
                + getVisitMethodName(nodeType)
                + "(const "
                + nodeType
                + " *node, "
                + argumentType
                + " data) = 0;");
      }
    }

    ccb.println("  virtual ~" + name + "() { }");
    ccb.println("};");
  }

  private static String defaultVisitorClass() {
    return JJTreeGlobals.parserName + "DefaultVisitor";
  }

  private static void generateDefaultVisitor(
      final CppCodeBuilder ccb, final JJTreeContext context) {
    final String className = defaultVisitorClass();
    final List<String> nodeNames = ASTNodeDescriptor.getNodeNames();

    ccb.println("class " + className + " : public " + visitorClass() + " {");

    final String argumentType = getVisitorDataType();
    final String ret = getVisitorReturnType();

    ccb.println("public:");
    ccb.println(
        "  virtual " + ret + " defaultVisit(const Node *node, " + argumentType + " data) = 0;");

    ccb.println("  virtual " + ret + " visit(const Node *node, " + argumentType + " data) {");
    ccb.println(
        "    " + (ret.trim().equals("void") ? "" : "return ") + "defaultVisit(node, data);");
    ccb.println("  }");

    if (context.treeOptions().getMulti()) {
      for (final String n : nodeNames) {
        if (n.equals("void")) {
          continue;
        }
        final String nodeType = context.treeOptions().getNodePrefix() + n;
        ccb.println(
            "  virtual "
                + ret
                + " "
                + getVisitMethodName(nodeType)
                + "(const "
                + nodeType
                + " *node, "
                + argumentType
                + " data) {");
        ccb.println(
            "    " + (ret.trim().equals("void") ? "" : "return ") + "defaultVisit(node, data);");
        ccb.println("  }");
      }
    }
    ccb.println("  ~" + className + "() { }");
    ccb.println("};");
  }
}
