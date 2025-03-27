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
import java.util.ArrayList;
import java.util.List;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.Context;
import org.javacc.parser.JavaCCGlobals;
import org.javacc.parser.MetaParseException;
import org.javacc.parser.Options;
import org.javacc.parser.RStringLiteral;
import org.javacc.parser.RegExprSpec;
import org.javacc.parser.RegularExpression;
import org.javacc.parser.TokenProduction;
import org.javacc.parser.TokenizerData;

/** Generates the Constants file. */
class OtherFilesGenCPP {

  static void printTokenImages(final CppCodeBuilder ccb, final Context context) {
    ccb.println("/** Literal token values. */");
    ccb.println();
    int cnt = 0;
    ccb.println("static const JJChar tokenImage_" + cnt + "[] = ");
    ccb.print("  ");
    OtherFilesGenCPP.printCharArray(ccb, "<EOF>");
    ccb.println(";");

    for (final TokenProduction tp : context.globals().rexprlist) {
      for (final RegExprSpec res : tp.respecs) {
        final RegularExpression re = res.rexp;
        ccb.println("static const JJChar tokenImage_" + ++cnt + "[] = ");
        // prefer labels to literals
        if (!re.label.equals("")) {
          ccb.println("  // <" + re.label + ">");
          ccb.print("  ");
          OtherFilesGenCPP.printCharArray(ccb, "<" + re.label + ">");
        } else if (re instanceof RStringLiteral) {
          final String image = ((RStringLiteral) re).image;
          // need to escape chars
          // ccb.println("  // " + image);
          ccb.print("  ");
          OtherFilesGenCPP.printCharArray(ccb, image);
        } else {
          if (re.tpContext.kind == TokenProduction.TOKEN) {
            context
                .errors()
                .warning(
                    re,
                    "Consider giving this non-string token a label for better error reporting.");
          }
          ccb.println("  // " + "<token of kind " + re.ordinal + ">");
          ccb.print("  ");
          OtherFilesGenCPP.printCharArray(ccb, "<token of kind " + re.ordinal + ">");
        }
        ccb.println(";");
      }
    }
    ccb.println();
    ccb.println("static const JJChar* const tokenImages[] = {");
    for (int i = 0; i <= cnt; i++) {
      ccb.println("  tokenImage_" + i + ", ");
    }
    ccb.println("};");
    ccb.println();
  }

  static void printTokenLabels(final CppCodeBuilder ccb, final Context ctx) {
    ccb.println("/** Literal token labels. */");
    ccb.println();
    int cnt = 0;
    ccb.println("static const JJChar tokenLabel_" + cnt + "[] = ");
    ccb.print("  ");
    OtherFilesGenCPP.printCharArray(ccb, "<EOF>");
    ccb.println(";");

    for (final TokenProduction tp : ctx.globals().rexprlist) {
      for (final RegExprSpec res : tp.respecs) {
        final RegularExpression re = res.rexp;
        ccb.println("static const JJChar tokenLabel_" + ++cnt + "[] = ");
        if (re instanceof RStringLiteral) {
          final String label = ((RStringLiteral) re).label;
          ccb.println("  // <" + label + ">");
          ccb.print("  ");
          OtherFilesGenCPP.printCharArray(ccb, "<" + label + ">");
        } else if (!re.label.equals("")) {
          ccb.println("  // <" + re.label + ">");
          ccb.print("  ");
          OtherFilesGenCPP.printCharArray(ccb, "<" + re.label + ">");
        } else {
          if (re.tpContext.kind == TokenProduction.TOKEN) {
            ctx.errors()
                .warning(
                    re,
                    "Consider giving this non-string token a label for better error reporting.");
          }
          ccb.println("  // " + "<token of kind " + re.ordinal + ">");
          ccb.print("  ");
          OtherFilesGenCPP.printCharArray(ccb, "<token of kind " + re.ordinal + ">");
        }
        ccb.println(";");
      }
    }
    ccb.println();
    ccb.println("static const JJChar* const tokenLabels[] = {");
    for (int i = 0; i <= cnt; i++) {
      ccb.println("  tokenLabel_" + i + ", ");
    }
    ccb.println("};");
    ccb.println();
  }

  static void start(final Context context, final TokenizerData tokenizerData)
      throws MetaParseException {
    if (context.errors().get_error_count() != 0) {
      throw new MetaParseException();
    }

    final List<String> toolnames = new ArrayList<>(context.globals().toolNames);
    toolnames.add(JavaCCGlobals.toolName);

    try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, CodeGeneratorSettings.create())) {
      ccb.setFile(
          new File(Options.getOutputDirectory(), context.globals().cu_name + "Constants.h"));
      ccb.addTools(toolnames.toArray(new String[toolnames.size()]));

      ccb.println();
      ccb.println("/**");
      ccb.println(" * Token literal values and constants.");
      ccb.println(" * Generated by org.javacc.cpp.OtherFilesGenCPP#start()");
      ccb.println(" */");

      final String guard = "JAVACC_" + context.globals().cu_name.toUpperCase() + "CONSTANTS_H";
      ccb.println("#ifndef " + guard);
      ccb.println("#define " + guard);
      ccb.println();
      ccb.println("#include \"JavaCC.h\"");
      ccb.println();
      if (Options.hasNamespace()) {
        ccb.println("namespace " + Options.stringValue("NAMESPACE_OPEN"));
      }

      final String constPrefix = "const";
      ccb.println("/** Token kind 0. */");
      ccb.println(constPrefix + "  int _EOF = 0;");
      for (final RegularExpression re : context.globals().ordered_named_tokens) {
        ccb.println("/** Labeled token " + re.ordinal + " kind. */");
        ccb.println(constPrefix + "  int " + re.label + " = " + re.ordinal + ";");
      }
      ccb.println();

      if (!Options.getUserTokenManager() && Options.getBuildTokenManager()) {
        for (int i = 0; i < tokenizerData.lexStateNames.length; i++) {
          ccb.println("/** Lexical state " + i + ". */");
          ccb.println(constPrefix + "  int " + tokenizerData.lexStateNames[i] + " = " + i + ";");
        }
        ccb.println();
      }
      printTokenImages(ccb, context);
      printTokenLabels(ccb, context);

      if (Options.stringValue(Options.UO__NAMESPACE).length() > 0) {
        ccb.println(Options.stringValue("NAMESPACE_CLOSE"));
      }
      ccb.println("#endif");
    } catch (final java.io.IOException e) {
      context
          .errors()
          .semantic_error(
              "Could not open file " + context.globals().cu_name + "Constants.h for writing.");
      throw new Error();
    }
  }

  // Used by the CPP code generator
  private static void printCharArray(final CppCodeBuilder ccb, final String s) {
    ccb.print("{");
    for (int i = 0; i < s.length(); i++) {
      ccb.print("0x" + Integer.toHexString(s.charAt(i)) + ", ");
    }
    ccb.print("0}");
  }
}
