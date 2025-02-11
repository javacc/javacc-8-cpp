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
 *     * Neither the names of of the copyright holders nor the names of its
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.javacc.parser.Action;
import org.javacc.parser.BNFProduction;
import org.javacc.parser.Choice;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.CodeProduction;
import org.javacc.parser.Context;
import org.javacc.parser.CppCodeProduction;
import org.javacc.parser.Expansion;
import org.javacc.parser.JavaCCGlobals;
import org.javacc.parser.JavaCCParserConstants;
import org.javacc.parser.JavaCodeProduction;
import org.javacc.parser.Lookahead;
import org.javacc.parser.NonTerminal;
import org.javacc.parser.NormalProduction;
import org.javacc.parser.OneOrMore;
import org.javacc.parser.Options;
import org.javacc.parser.ParserData;
import org.javacc.parser.RegularExpression;
import org.javacc.parser.Semanticize;
import org.javacc.parser.Sequence;
import org.javacc.parser.Token;
import org.javacc.parser.TryBlock;
import org.javacc.parser.ZeroOrMore;
import org.javacc.parser.ZeroOrOne;
import org.javacc.utils.CodeBuilder;

/** Generate the parser. */
class ParserCodeGenerator implements org.javacc.parser.ParserCodeGenerator {
  /*
   * These lists are used to maintain expansions for which code generation in phase 2 and phase 3
   *  is required.
   * Whenever a call is generated to a phase 2 or phase 3 routine, a corresponding entry is added
   *  here if it has not already been added.
   * The phase 3 routines have been optimized in version 0.7pre2.
   * Essentially only those methods (and only those portions of these methods) are generated
   *  that are required.
   * The lookahead amount is used to determine this.
   * This change requires the use of a hash table because it is now possible for the same phase 3
   *  routine to be requested multiple times with different lookaheads.
   * The hash table provides a easily searchable capability to determine the previous requests.
   * The phase 3 routines nExpressionTreeConstantsow are performed in a two step process:
   *  - the first step gathers the requests (replacing requests with lower lookaheads with those
   *     requiring larger lookaheads),
   *  - the second step then generates these methods.
   */

  private final List<Lookahead> phase2list = new ArrayList<>();
  private final List<Phase3Data> phase3list = new ArrayList<>();
  private boolean jj2LA;
  private final Hashtable<Expansion, Phase3Data> phase3table = new Hashtable<>();

  private final Context context;
  private final Map<Expansion, String> internalNames = new HashMap<>();
  private final Map<Expansion, Integer> internalIndexes = new HashMap<>();

  private CppCodeBuilder ccb;

  ParserCodeGenerator(final Context context) {
    this.context = context;
  }

  @Override
  public void generateCode(final CodeGeneratorSettings settings, final ParserData parserData) {

    final List<String> tn = new ArrayList<>(context.globals().toolNames);
    tn.add(JavaCCGlobals.toolName);

    final File file = new File(Options.getOutputDirectory(), parserData.parserName + ".cc");
    ccb = CppCodeBuilder.of(context, settings).setFile(file);

    if (context.globals().jjtreeGenerated) {
      ccb.switchToStaticsFile();
      ccb.println("#include \"" + context.globals().cu_name + "Tree.h\"\n");
    }

    ccb.switchToIncludeFile();
    final String guard = "JAVACC_" + parserData.parserName.toUpperCase() + "_H";
    ccb.println("#ifndef " + guard);
    ccb.println("#define " + guard);
    ccb.println();

    if (!Options.getLibrary().isEmpty()) {
      ccb.println("#include \"ImportExport.h\"");
    }
    ccb.println("#include \"JavaCC.h\"");
    ccb.println("#include \"CharStream.h\"");
    ccb.println("#include \"Token.h\"");
    ccb.println("#include \"TokenManager.h\"");
    printInclude(Options.getTokenInclude());
    printInclude(Options.getParserInclude());

    if (Options.getTokenConstantsInclude().isEmpty()) {
      ccb.println("#include \"" + context.globals().cu_name + "Constants.h\"");
    } else {
      ccb.println("#include \"" + Options.getTokenConstantsInclude() + "\" // user defined option");
    }
    if (context.globals().jjtreeGenerated) {
      ccb.println("#include \"JJT" + context.globals().cu_name + "State.h\"");
    }
    ccb.println("#include \"DefaultParserErrorHandler.h\"");
    if (context.globals().jjtreeGenerated) {
      ccb.println("#include \"" + context.globals().cu_name + "Tree.h\"");
    }
    ccb.println();

    if (Options.hasNamespace()) {
      ccb.println("namespace " + Options.stringValue("NAMESPACE_OPEN") + " // user defined option");
      ccb.println();
    }

    ccb.println("/* Base code I (.h) */");
    ccb.println();
    ccb.print("struct ");
    if (!Options.getLibrary().isEmpty()) {
      ccb.print(parserData.parserName.toUpperCase() + "_API ");
    }
    ccb.println("JJCalls {");
    ccb.println("  int        arg;");
    ccb.println("  int        gen;");
    ccb.println("  Token*     first;");
    ccb.println("  JJCalls*   next;");
    ccb.println("  JJCalls()  {");
    ccb.println("    arg   = 0;");
    ccb.println("    gen   = -1;");
    ccb.println("    first = nullptr;");
    ccb.println("    next  = nullptr;");
    ccb.println("  }");
    ccb.println("  ~JJCalls() {");
    ccb.println("    if (next) delete next;");
    ccb.println("  }");
    ccb.println("};");
    ccb.println();

    ccb.genClassStart("", context.globals().cu_name, new String[] {}, new String[0]);

    ccb.switchToMainFile();
    ccb.println("/* User code (parser_begin-parser_end section) */");
    if (context.globals().cu_to_insertion_point_2.size() != 0) {
      ccb.printTokenSetup(context.globals().cu_to_insertion_point_2.get(0));
      for (final Token token : context.globals().cu_to_insertion_point_2) {
        ccb.printToken(token);
      }
    }
    if (Options.hasNamespace()) {
      ccb.println("namespace " + Options.stringValue("NAMESPACE_OPEN") + " // user defined option");
    }
    ccb.println();
    ccb.println("/* Generated code for user productions (.cc) */");
    ccb.println();

    ccb.switchToIncludeFile();
    ccb.println();
    ccb.println("/* Generated code for user productions (.h) */");
    ccb.println();

    ccb.switchToMainFile();

    build();

    ccb.switchToMainFile();
    ccb.println("  /* Base code II (.cc) */");
    ccb.println();

    ccb.switchToIncludeFile();
    ccb.println();
    ccb.println("/* Base code II (.h) */");
    ccb.println();
    ccb.println("public: ");
    ccb.println("  void setErrorHandler(ParserErrorHandler* eh) {");
    ccb.println("    if (delete_eh) delete errorHandler;");
    ccb.println("    errorHandler = eh;");
    ccb.println("    delete_eh = false;");
    ccb.println("  }");
    ccb.println();
    ccb.println("  const ParserErrorHandler* getErrorHandler() {");
    ccb.println("    return errorHandler;");
    ccb.println("  }");
    ccb.println();
    ccb.println("  static const JJChar* getTokenImage(int kind) {");
    ccb.println(
        "    return kind >= 0 ? " + getTokenImages() + "[kind] : " + getTokenImages() + "[0];");
    ccb.println("  }");
    ccb.println();
    ccb.println("  static const JJChar* getTokenLabel(int kind) {");
    ccb.println(
        "    return kind >= 0 ? " + getTokenLabels() + "[kind] : " + getTokenLabels() + "[0];");
    ccb.println("  }");
    ccb.println();
    ccb.println("  TokenManager*          token_source = nullptr;");
    ccb.println("  CharStream*            jj_input_stream = nullptr;");
    ccb.println("  Token*                 token = nullptr;  // Current token.");
    ccb.println("  Token*                 jj_nt = nullptr;  // Next token.");
    ccb.println();
    ccb.println("private: ");
    ccb.println("  int                    jj_ntk;");

    ccb.println("  JJCalls                jj_2_rtns[" + (context.globals().jj2index + 1) + "];");
    ccb.println("  bool                   jj_rescan;");
    ccb.println("  int                    jj_gc;");
    ccb.println("  Token*                 jj_scanpos;");
    ccb.println("  Token*                 jj_lastpos;");
    ccb.println("  int                    jj_la;");
    ccb.println("  bool                   jj_lookingAhead;  // Whether we are looking ahead.");
    ccb.println("  bool                   jj_semLA;");

    ccb.println("  int                    jj_gen;");
    ccb.println("  int                    jj_la1[" + (context.globals().maskindex + 1) + "];");
    ccb.println("  ParserErrorHandler*    errorHandler = nullptr;");
    ccb.println();
    ccb.println("protected: ");
    ccb.println("  bool                   delete_eh = false;");
    ccb.println("  bool                   delete_tokens = true;");
    ccb.println("  bool                   hasError;");
    ccb.println();

    final int tokenMaskSize = ((context.globals().tokenCount - 1) / 32) + 1;
    if (Options.getErrorReporting() && (tokenMaskSize > 0)) {
      ccb.switchToStaticsFile();
      ccb.println("#include \"TokenManagerError.h\"");
      ccb.println();
      ccb.println("/* Base code I (.cc, static) */");
      ccb.println();
      for (int i = 0; i < tokenMaskSize; i++) {
        if (context.globals().maskVals.size() > 0) {
          ccb.println("static unsigned int jj_la1_" + i + "[] = {");
          int j = 0;
          for (final int[] tokenMask : context.globals().maskVals) {
            if (j > 0) {
              ccb.print(", ");
            } else {
              j++;
              ccb.print("  ");
            }
            ccb.print("0x" + Integer.toHexString(tokenMask[i]));
          }
          ccb.println();
          ccb.println("};");
        }
      }
      ccb.println();
    }

    if (Options.getDepthLimit() > 0) {
      ccb.println("  private: int jj_depth;");
      ccb.println("  private: bool jj_depth_error;");
      ccb.println("  friend class __jj_depth_inc;");
      ccb.println("  class __jj_depth_inc {public:");
      ccb.println("    " + context.globals().cu_name + "* parent;");
      ccb.println(
          "    __jj_depth_inc("
              + context.globals().cu_name
              + "* p): parent(p) { parent->jj_depth++; };");
      ccb.println("    ~__jj_depth_inc(){ parent->jj_depth--; }");
      ccb.println("  };");
      ccb.println();
    }

    if (!Options.getStackLimit().equals("")) {
      ccb.println("  public: size_t jj_stack_limit;");
      ccb.println("  private: void* jj_stack_base;");
      ccb.println("  private: bool jj_stack_error;");
      ccb.println();
    }

    ccb.switchToIncludeFile(); // TEMP
    ccb.println("  Token*                 head;");
    ccb.println();
    ccb.println("public: ");

    ccb.generateMethodDefHeader(
        "", context.globals().cu_name, context.globals().cu_name + "(TokenManager* tokenManager)");
    ccb.println(" {");
    ccb.println("  head = nullptr;");
    ccb.println("  ReInit(tokenManager);");
    if (Options.getTokenManagerUsesParser()) {
      ccb.println("    tokenManager->setParser(this);");
    }
    ccb.println("}");
    ccb.println();

    ccb.switchToIncludeFile();
    ccb.println("  virtual ~" + context.globals().cu_name + "();");
    ccb.println();
    ccb.switchToMainFile();
    ccb.print(context.globals().cu_name + "::~" + context.globals().cu_name + "()");
    ccb.println(" {");
    ccb.println("  clear();");
    ccb.println("}");
    ccb.println();

    ccb.generateMethodDefHeader(
        "void", context.globals().cu_name, "ReInit(TokenManager* tokenManager)");
    ccb.println("{");
    ccb.println("  clear();");
    ccb.println("  errorHandler = new DefaultParserErrorHandler();");
    ccb.println("  delete_eh = true;");
    ccb.println("  hasError = false;");
    ccb.println("  token_source = tokenManager;");
    ccb.println("  head = token = new " + getTokenType() + ";");
    ccb.println("  jj_lookingAhead = false;");
    ccb.println("  jj_rescan = false;");
    ccb.println("  jj_done = false;");
    ccb.println("  jj_scanpos = jj_lastpos = nullptr;");
    ccb.println("  jj_gc = 0;");
    ccb.println("  jj_kind = -1;");
    ccb.println("  indent = 0;");
    ccb.println("  trace = " + Options.getDebugParser() + ";");
    if (!Options.getStackLimit().equals("")) {
      ccb.println("  jj_stack_limit = " + Options.getStackLimit() + ";");
      ccb.println("  jj_stack_error = jj_stack_check(true);");
    }

    if (Options.getCacheTokens()) {
      ccb.println("  token->next() = jj_nt = token_source->getNextToken();");
    } else {
      ccb.println("  jj_ntk = -1;");
    }
    if (context.globals().jjtreeGenerated) {
      ccb.println("  jjtree.reset();");
    }
    if (Options.getDepthLimit() > 0) {
      ccb.println("  jj_depth = 0;");
      ccb.println("  jj_depth_error = false;");
    }
    if (Options.getErrorReporting()) {
      ccb.println("  jj_gen = 0;");
      if (context.globals().maskindex > 0) {
        ccb.println(
            "  for (int i = 0; i < " + context.globals().maskindex + "; i++) { jj_la1[i] = -1; }");
      }
    }
    ccb.println("}");
    ccb.println();

    ccb.generateMethodDefHeader("void", context.globals().cu_name, "clear()");
    ccb.println("{");
    ccb.println(
        "  // Since token manager was generated from outside, parser should not take care of deleting it");
    ccb.println("  // if (token_source) delete token_source;");
    ccb.println("  if (delete_tokens && head) {");
    ccb.println("    Token* next;");
    ccb.println("    Token* t = head;");
    ccb.println("    while (t) {");
    ccb.println("      next = t->next();");
    ccb.println("      delete t;");
    ccb.println("      t = next;");
    ccb.println("    }");
    ccb.println("  }");
    ccb.println("  if (delete_eh) {");
    ccb.println("    delete errorHandler, errorHandler = nullptr;");
    ccb.println("    delete_eh = false;");
    ccb.println("  }");
    if (Options.getDepthLimit() > 0) {
      ccb.println("  assert(jj_depth==0);");
    }
    ccb.println("}");
    ccb.println();

    if (!Options.getStackLimit().equals("")) {
      ccb.println();
      ccb.switchToIncludeFile();
      ccb.println(" virtual");
      ccb.switchToMainFile();
      ccb.generateMethodDefHeader("bool", context.globals().cu_name, "jj_stack_check(bool init)");
      ccb.println(" {");
      ccb.println("   if (init) {");
      ccb.println("     jj_stack_base = nullptr;");
      ccb.println("     return false;");
      ccb.println("   } else {");
      ccb.println("     volatile int q = 0;");
      ccb.println("     if (!jj_stack_base) {");
      ccb.println("       jj_stack_base = (void*)&q;");
      ccb.println("       return false;");
      ccb.println("     } else {");
      ccb.println("       // Stack can grow in both directions, depending on arch");
      ccb.println("       std::ptrdiff_t used = (char*)jj_stack_base-(char*)&q;");
      ccb.println("       return (std::abs(used) > jj_stack_limit);");
      ccb.println("     }");
      ccb.println("   }");
      ccb.println("}");
    }

    ccb.generateMethodDefHeader("Token*", context.globals().cu_name, "jj_consume_token(int kind)");
    ccb.println(" {");
    if (!Options.getStackLimit().equals("")) {
      ccb.println("  if (kind != -1 && (jj_stack_error || jj_stack_check(false))) {");
      ccb.println("    if (!jj_stack_error) {");
      ccb.println("      errorHandler->otherError(\"Stack overflow while trying to parse\");");
      ccb.println("      jj_stack_error=true;");
      ccb.println("    }");
      ccb.println("    return jj_consume_token(-1);");
      ccb.println("  }");
    }
    if (Options.getCacheTokens()) {
      ccb.println("  Token* oldToken = token;");
      ccb.println("  if ((token = jj_nt)->next() != nullptr) jj_nt = jj_nt->next();");
      ccb.println("  else jj_nt = jj_nt->next() = token_source->getNextToken();");
    } else {
      ccb.println("  Token* oldToken;");
      ccb.println("  if ((oldToken = token)->next() != nullptr) token = token->next();");
      ccb.println("  else token = token->next() = token_source->getNextToken();");
      ccb.println("  jj_ntk = -1;");
    }
    ccb.println("  if (token->kind() == kind) {");
    if (Options.getErrorReporting()) {
      ccb.println("    jj_gen++;");
      if (context.globals().jj2index != 0) {
        ccb.println("    if (++jj_gc > 100) {");
        ccb.println("      jj_gc = 0;");
        ccb.println("      for (int i = 0; i < " + context.globals().jj2index + "; i++) {");
        ccb.println("        JJCalls *c = &jj_2_rtns[i];");
        ccb.println("        while (c != nullptr) {");
        ccb.println("          if (c->gen < jj_gen) c->first = nullptr;");
        ccb.println("          c = c->next;");
        ccb.println("        }");
        ccb.println("      }");
        ccb.println("    }");
      }
    }
    if (Options.getDebugParser()) {
      ccb.println("    trace_token(token, \"\");");
    }
    ccb.println("    return token;");
    ccb.println("  }");
    if (Options.getCacheTokens()) {
      ccb.println("  jj_nt = token;");
    }
    ccb.println("  token = oldToken;");
    if (Options.getErrorReporting()) {
      ccb.println("  jj_kind = kind;");
    }
    // codeGen.genCodeLine(" throw generateParseException();");
    if (!Options.getStackLimit().equals("")) {
      ccb.println("  if (!jj_stack_error) {");
    }
    ccb.println("  const JJString expectedImage = getTokenImage(kind);");
    ccb.println("  const JJString expectedLabel = getTokenLabel(kind);");

    ccb.println("  const Token*   actualToken   = getToken(1);");
    ccb.println("  const JJString actualImage   = getTokenImage(actualToken->kind());");
    ccb.println("  const JJString actualLabel   = getTokenLabel(actualToken->kind());");
    ccb.println(
        "  errorHandler->unexpectedToken(expectedImage, expectedLabel, actualImage, actualLabel, actualToken);");
    if (!Options.getStackLimit().equals("")) {
      ccb.println("  }");
    }
    ccb.println("  hasError = true;");
    ccb.println("  return token;");
    ccb.println("}");
    ccb.println();

    if (context.globals().jj2index != 0) {
      ccb.switchToMainFile();
      ccb.generateMethodDefHeader("bool", context.globals().cu_name, "jj_scan_token(int kind)");
      ccb.println(" {");
      if (!Options.getStackLimit().equals("")) {
        ccb.println("  if (kind != -1 && (jj_stack_error || jj_stack_check(false))) {");
        ccb.println("    if (!jj_stack_error) {");
        ccb.println("      errorHandler->otherError(\"Stack overflow while trying to parse\");");
        ccb.println("      jj_stack_error=true;");
        ccb.println("    }");
        ccb.println("    return jj_consume_token(-1);");
        ccb.println("  }");
      }
      ccb.println("  if (jj_scanpos == jj_lastpos) {");
      ccb.println("    jj_la--;");
      ccb.println("    if (jj_scanpos->next() == nullptr) {");
      ccb.println(
          "      jj_lastpos = jj_scanpos = jj_scanpos->next() = token_source->getNextToken();");
      ccb.println("    } else {");
      ccb.println("      jj_lastpos = jj_scanpos = jj_scanpos->next();");
      ccb.println("    }");
      ccb.println("  } else {");
      ccb.println("    jj_scanpos = jj_scanpos->next();");
      ccb.println("  }");
      if (Options.getErrorReporting()) {
        ccb.println("  if (jj_rescan) {");
        ccb.println("    int i = 0; Token* tok = token;");
        ccb.println("    while (tok != nullptr && tok != jj_scanpos) {");
        ccb.println("      i++;");
        ccb.println("      tok = tok->next();");
        ccb.println("    }");
        ccb.println("    if (tok != nullptr) jj_add_error_token(kind, i);");
        if (Options.getDebugLookahead()) {
          ccb.println("  } else {");
          ccb.println("    trace_scan(jj_scanpos, kind);");
        }
        ccb.println("  }");
      } else if (Options.getDebugLookahead()) {
        ccb.println("  trace_scan(jj_scanpos, kind);");
      }
      ccb.println("  if (jj_scanpos->kind() != kind) return true;");
      // codeGen.genCodeLine(" if (jj_la == 0 && jj_scanpos == jj_lastpos)
      // throw jj_ls;");
      ccb.println("  if (jj_la == 0 && jj_scanpos == jj_lastpos) { return jj_done = true; }");
      ccb.println("  return false;");
      ccb.println("}");
      ccb.println();
    }
    ccb.println();

    ccb.println("/** Get the next Token. */");
    ccb.generateMethodDefHeader("Token*", context.globals().cu_name, "getNextToken()");
    ccb.println(" {");
    if (Options.getCacheTokens()) {
      ccb.println("  if ((token = jj_nt)->next() != nullptr) jj_nt = jj_nt->next();");
      ccb.println("  else jj_nt = jj_nt->next() = token_source->getNextToken();");
    } else {
      ccb.println("  if (token->next() != nullptr) token = token->next();");
      ccb.println("  else token = token->next() = token_source->getNextToken();");
      ccb.println("  jj_ntk = -1;");
    }
    if (Options.getErrorReporting()) {
      ccb.println("  jj_gen++;");
    }
    if (Options.getDebugParser()) {
      ccb.println("  trace_token(token, \" (in getNextToken)\");");
    }
    ccb.println("  return token;");
    ccb.println("}");
    ccb.println();

    ccb.println("/** Get the specific Token. */");
    ccb.generateMethodDefHeader("Token*", context.globals().cu_name, "getToken(int index)");
    ccb.println(" {");
    if (context.globals().lookaheadNeeded) {
      ccb.println("  Token* t = jj_lookingAhead ? jj_scanpos : token;");
    } else {
      ccb.println("  Token* t = token;");
    }
    ccb.println("  for (int i = 0; i < index; i++) {");
    ccb.println("    if (t->next() != nullptr) t = t->next();");
    ccb.println("    else t = t->next() = token_source->getNextToken();");
    ccb.println("  }");
    ccb.println("  return t;");
    ccb.println("}");
    ccb.println();

    if (!Options.getCacheTokens()) {
      ccb.generateMethodDefHeader("int", context.globals().cu_name, "jj_ntk_f()");
      ccb.println(" {");

      ccb.println("  if ((jj_nt=token->next()) == nullptr)");
      ccb.println("    return (jj_ntk = (token->next() = token_source->getNextToken())->kind());");
      ccb.println("  else");
      ccb.println("    return (jj_ntk = jj_nt->kind());");
      ccb.println("}");
      ccb.println();
    }

    ccb.switchToIncludeFile();
    ccb.println("private:");
    ccb.println("  int jj_kind;");
    if (Options.getErrorReporting()) {
      ccb.println("  int** jj_expentries;");
      ccb.println("  int*  jj_expentry;");
      ccb.println();
      if (context.globals().jj2index != 0) {
        ccb.switchToStaticsFile();
        // For now we don't support ERROR_REPORTING in the C++ version.
        // codeGen.genCodeLine(" static int *jj_lasttokens = new
        // int[100];");
        // codeGen.genCodeLine(" static int jj_endpos;");
        ccb.println();

        ccb.generateMethodDefHeader(
            "void", context.globals().cu_name, "jj_add_error_token(int kind, int pos)");
        ccb.println(" {");
        // For now we don't support ERROR_REPORTING in the C++ version.

        // codeGen.genCodeLine(" if (pos >= 100) return;");
        // codeGen.genCodeLine(" if (pos == jj_endpos + 1) {");
        // codeGen.genCodeLine(" jj_lasttokens[jj_endpos++] = kind;");
        // codeGen.genCodeLine(" } else if (jj_endpos != 0) {");
        // codeGen.genCodeLine(" jj_expentry = new int[jj_endpos];");
        // codeGen.genCodeLine(" for (int i = 0; i < jj_endpos; i++) {");
        // codeGen.genCodeLine(" jj_expentry[i] = jj_lasttokens[i];");
        // codeGen.genCodeLine(" }");
        // codeGen.genCodeLine(" jj_entries_loop: for (java.util.Iterator
        // it = jj_expentries.iterator(); it.hasNext();) {");
        // codeGen.genCodeLine(" int[] oldentry = (int[])(it->next());");
        // codeGen.genCodeLine(" if (oldentry.length ==
        // jj_expentry.length) {");
        // codeGen.genCodeLine(" for (int i = 0; i < jj_expentry.length;
        // i++) {");
        // codeGen.genCodeLine(" if (oldentry[i] != jj_expentry[i]) {");
        // codeGen.genCodeLine(" continue jj_entries_loop;");
        // codeGen.genCodeLine(" }");
        // codeGen.genCodeLine(" }");
        // codeGen.genCodeLine(" jj_expentries.add(jj_expentry);");
        // codeGen.genCodeLine(" break jj_entries_loop;");
        // codeGen.genCodeLine(" }");
        // codeGen.genCodeLine(" }");
        // codeGen.genCodeLine(" if (pos != 0) jj_lasttokens[(jj_endpos =
        // pos) - 1] = kind;");
        // codeGen.genCodeLine(" }");
        ccb.println("}");
        ccb.println();
      }

      ccb.switchToIncludeFile();
      ccb.println("protected:");
      ccb.println("  /** Generate ParseException. */");
      ccb.generateMethodDefHeader("virtual void", context.globals().cu_name, "parseError()");
      ccb.println(" {");
      if (Options.getErrorReporting()) {
        ccb.println(
            "  JJERR << JJWIDE(Parse error at : ) << token->beginLine() << JJWIDE(:) << token->beginColumn() << JJWIDE( after token: ) << addUnicodeEscapes(token->image()) << JJWIDE( encountered: ) << addUnicodeEscapes(getToken(1)->image()) << std::endl;");
      }
      ccb.println("}");
      /*
       * generateMethodDefHeader("ParseException", cu_name,
       * "generateParseException()"); codeGen.genCodeLine("   {");
       * //codeGen.genCodeLine("    jj_expentries.clear();");
       * //codeGen.genCodeLine("    bool[] la1tokens = new boolean[" +
       * tokenCount + "];");
       * //codeGen.genCodeLine("    if (jj_kind >= 0) {");
       * //codeGen.genCodeLine("      la1tokens[jj_kind] = true;");
       * //codeGen.genCodeLine("      jj_kind = -1;");
       * //codeGen.genCodeLine("    }");
       * //codeGen.genCodeLine("    for (int i = 0; i < " + maskindex +
       * "; i++) {");
       * //codeGen.genCodeLine("      if (jj_la1[i] == jj_gen) {");
       * //codeGen.genCodeLine("        for (int j = 0; j < 32; j++) {");
       * //for (int i = 0; i < (tokenCount-1)/32 + 1; i++) {
       * //codeGen.genCodeLine("          if ((jj_la1_" + i +
       * "[i] & (1<<j)) != 0) {"); //genCode("            la1tokens["); //if (i
       * != 0) { //genCode((32*i) + "+"); //}
       * //codeGen.genCodeLine("j] = true;");
       * //codeGen.genCodeLine("          }"); //}
       * //codeGen.genCodeLine("        }");
       * //codeGen.genCodeLine("      }");
       * //codeGen.genCodeLine("    }");
       * //codeGen.genCodeLine("    for (int i = 0; i < " + tokenCount +
       * "; i++) {"); //codeGen.genCodeLine("      if (la1tokens[i]) {");
       * //codeGen.genCodeLine("        jj_expentry = new int[1];");
       * //codeGen.genCodeLine("        jj_expentry[0] = i;");
       * //codeGen.genCodeLine("        jj_expentries.add(jj_expentry);");
       * //codeGen.genCodeLine("      }");
       * //codeGen.genCodeLine("    }"); //if (jj2index != 0) {
       * //codeGen.genCodeLine("    jj_endpos = 0;");
       * //codeGen.genCodeLine("    jj_rescan_token();");
       * //codeGen.genCodeLine("    jj_add_error_token(0, 0);"); //}
       * //codeGen.genCodeLine("    int exptokseq[][1] = new int[1];");
       * //codeGen.
       * genCodeLine("    for (int i = 0; i < jj_expentries.size(); i++) {");
       * //if (!Options.getGenerateGenerics()) //codeGen.
       * genCodeLine("      exptokseq[i] = (int[])jj_expentries.get(i);");
       * //else //codeGen.
       * genCodeLine("      exptokseq[i] = jj_expentries.get(i);");
       * //codeGen.genCodeLine("    }");
       * codeGen.genCodeLine("    return new _ParseException();");//token,
       * nullptr, tokenImages);"); codeGen.genCodeLine(" }");
       */
    } else {
      ccb.println("protected:");
      ccb.println("  /** Generate ParseException. */");
      ccb.generateMethodDefHeader("virtual void", context.globals().cu_name, "parseError()");
      ccb.println(" {");
      if (Options.getErrorReporting()) {
        ccb.println(
            "  JJERR << "
                + "JJWIDE(Parse error at : ) << token->beginLine() << JJWIDE(:) "
                + "<< token->beginColumn() << JJWIDE( after token: ) << addUnicodeEscapes(token->image())"
                + " << JJWIDE( encountered: ) << addUnicodeEscapes(getToken(1)->image()) << std::endl;");
      }
      ccb.println("}");
      /*
       * generateMethodDefHeader("ParseException", cu_name,
       * "generateParseException()"); codeGen.genCodeLine("   {");
       * codeGen.genCodeLine("    Token* errortok = token->next();"); if
       * (Options.getKeepLineColumn()) codeGen.
       * genCodeLine("    int line = errortok.beginLine, column = errortok.beginColumn;"
       * ); codeGen.
       * genCodeLine("    JJString mess = (errortok->kind() == 0) ? tokenImages[0] : errortok->image();"
       * ); if (Options.getKeepLineColumn())
       * codeGen.genCodeLine("    return new _ParseException();");// +
       * //"\"Parse error at line \" + line + \", column \" + column + \".  " +
       * //"Encountered: \" + mess);"); else
       * codeGen.genCodeLine("    return new _ParseException();");//
       * \"Parse error at <unknown location>.  " +
       * //"Encountered: \" + mess);"); codeGen.genCodeLine("  }");
       */
    }
    ccb.println();

    ccb.switchToIncludeFile();
    ccb.println("private:");
    ccb.println("  int  indent; // trace indentation");
    ccb.println("  bool trace = " + Options.getDebugParser() + ";");
    ccb.println("  bool trace_la = " + Options.getDebugLookahead() + ";");
    ccb.println();
    ccb.println("public:");
    ccb.generateMethodDefHeader("bool", context.globals().cu_name, "trace_enabled()");
    ccb.println("  {");
    ccb.println("  return trace;");
    ccb.println("}");
    ccb.println();
    ccb.generateMethodDefHeader("bool", context.globals().cu_name, "trace_la_enabled()");
    ccb.println(" {");
    ccb.println("  return trace_la;");
    ccb.println("}");
    ccb.println();
    if (Options.getDebugParser()) {
      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "enable_tracing()");
      ccb.println(" {");
      ccb.println("  trace = true;");
      ccb.println("}");
      ccb.println();

      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "disable_tracing()");
      ccb.println(" {");
      ccb.println("  trace = false;");
      ccb.println("}");
      ccb.println();

      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "trace_call(const char *s)");
      ccb.println(" {");
      ccb.println("  if (trace_enabled()) {");
      ccb.println("    for (int no = 0; no < indent; no++) { JJLOG << JJSPACE; }");
      ccb.println("    JJLOG << \"Call:   \" << s << std::endl;");
      ccb.println("  }");
      ccb.println("  indent += 2;");
      ccb.println("}");
      ccb.println();

      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "trace_return(const char *s)");
      ccb.println(" {");
      ccb.println("  indent -= 2;");
      ccb.println("  if (trace_enabled()) {");
      ccb.println("    for (int no = 0; no < indent; no++) { JJLOG << JJSPACE; }");
      ccb.println("    JJLOG << \"Return: \" << s << std::endl;");
      ccb.println("  }");
      ccb.println("}");
      ccb.println();

      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader(
          "void", context.globals().cu_name, "trace_token(const Token* token, const char* where)");
      ccb.println(" {");
      ccb.println("  if (trace_enabled()) {");
      ccb.println("    for (int no = 0; no < indent; no++) { JJLOG << JJSPACE; }");
      ccb.println("    JJLOG << JJWIDE(Consumed token: <) << token->kind() << JJCOMMA << JJSPACE");
      ccb.println(
          "          << JJDBQUOTE << addUnicodeEscapes("
              + getTokenImages()
              + "[token->kind()]) << JJDBQUOTE;");
      ccb.println(
          "    if (token->kind() != 0 && "
              + getTokenImages()
              + "[token->kind()] != token->image()) {");
      ccb.println(
          "       JJLOG << JJSPACE << JJDBQUOTE << addUnicodeEscapes(token->image()) << JJDBQUOTE;");
      ccb.println("    }");
      ccb.println("    JJLOG << JJWIDE(> at) << JJSPACE << token->beginLine() << JJWIDE(:)");
      ccb.println("          << token->beginColumn() << *where << std::endl;");
      ccb.println("  }");
      ccb.println("}");
      ccb.println();

      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader(
          "void", context.globals().cu_name, "trace_scan(const Token* token, int t2)");
      ccb.println(" {");
      ccb.println("  if (trace_la_enabled()) {");
      ccb.println("    for (int no = 0; no < indent; no++) { JJLOG << JJSPACE; }");
      ccb.println("    JJLOG << JJWIDE(Visited  token: <) << token->kind() << JJCOMMA << JJSPACE");
      ccb.println(
          "          << JJDBQUOTE << addUnicodeEscapes("
              + getTokenImages()
              + "[token->kind()]) << JJDBQUOTE;");
      ccb.println(
          "    if (token->kind() != 0 && "
              + getTokenImages()
              + "[token->kind()] != token->image()) {");
      ccb.println(
          "       JJLOG << JJSPACE << JJDBQUOTE << addUnicodeEscapes(token->image()) << JJDBQUOTE;");
      ccb.println("    }");
      ccb.println("    JJLOG << JJWIDE(> at) << JJSPACE << token->beginLine() << JJWIDE(:)");
      ccb.print("          << token->beginColumn() << JJWIDE(; Expected token: <)");
      ccb.println(" << t2 << JJCOMMA << JJSPACE");
      ccb.println(
          "          << addUnicodeEscapes("
              + getTokenLabels()
              + "[t2]) << JJWIDE(>) << std::endl;");
      ccb.println("  }");
      ccb.println("}");
      ccb.println();

    } else {
      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "enable_tracing()");
      ccb.println(" {");
      ccb.println("}");
      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "disable_tracing()");
      ccb.println(" {");
      ccb.println("}");
      ccb.println();
    }

    if (Options.getDebugLookahead()) {
      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "enable_la_tracing()");
      ccb.println(" {");
      ccb.println("  trace_la = true;");
      ccb.println("}");
      ccb.println();

      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "disable_la_tracing()");
      ccb.println(" {");
      ccb.println("  trace_la = false;");
      ccb.println("}");
      ccb.println();

      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader(
          "void", context.globals().cu_name, "trace_la_call(const char *s)");
      ccb.println(" {");
      ccb.println("  if (trace_la_enabled()) {");
      ccb.println("    for (int no = 0; no < indent; no++) { JJLOG << JJSPACE; }");
      ccb.println("    JJLOG << \"Call:   \" << s << std::endl;");
      ccb.println("  }");
      ccb.println("  indent += 2;");
      ccb.println("}");
      ccb.println();

      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader(
          "void", context.globals().cu_name, "trace_la_return(const char *s)");
      ccb.println(" {");
      ccb.println("  indent -= 2;");
      ccb.println("  if (trace_la_enabled()) {");
      ccb.println("    for (int no = 0; no < indent; no++) { JJLOG << JJSPACE; }");
      ccb.println("    JJLOG << \"Return: \" << s << std::endl;");
      ccb.println("  }");
      ccb.println("}");
      ccb.println();
    } else {
      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "enable_la_tracing()");
      ccb.println(" {");
      ccb.println("}");
      ccb.switchToIncludeFile();
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "disable_la_tracing()");
      ccb.println(" {");
      ccb.println("}");
      ccb.println();
    }

    if ((context.globals().jj2index != 0) && Options.getErrorReporting()) {
      ccb.generateMethodDefHeader("void", context.globals().cu_name, "jj_rescan_token()");
      ccb.println(" {");
      ccb.println("  jj_rescan = true;");
      ccb.println("  for (int i = 0; i < " + context.globals().jj2index + "; i++) {");
      // codeGen.genCodeLine(" try {");
      ccb.println("    JJCalls *p = &jj_2_rtns[i];");
      ccb.println("    do {");
      ccb.println("      if (p->gen > jj_gen) {");
      ccb.println("        jj_la = p->arg;");
      ccb.println("        jj_lastpos = jj_scanpos = p->first;");
      ccb.println("        switch (i) {");
      for (int i = 0; i < context.globals().jj2index; i++) {
        ccb.println("          case " + i + ":");
        ccb.println("            jj_3_" + (i + 1) + "();");
        ccb.println("            break;");
      }
      ccb.println("        }");
      ccb.println("      }");
      ccb.println("      p = p->next;");
      ccb.println("    } while (p != nullptr);");
      // codeGen.genCodeLine(" } catch(LookaheadSuccess ls) { }");
      ccb.println("  }");
      ccb.println("  jj_rescan = false;");
      ccb.println("}");
      ccb.println();

      ccb.generateMethodDefHeader("void", context.globals().cu_name, "jj_save(int index, int xla)");
      ccb.println(" {");
      ccb.println("  JJCalls *p = &jj_2_rtns[index];");
      ccb.println("  while (p->gen > jj_gen) {");
      ccb.println("    if (p->next == nullptr) {");
      ccb.println("      p = p->next = new JJCalls();");
      ccb.println("      break;");
      ccb.println("    }");
      ccb.println("    p = p->next;");
      ccb.println("  }");
      ccb.println("  p->gen = jj_gen + xla - jj_la;");
      ccb.println("  p->first = token;");
      ccb.println("  p->arg = xla;");
      ccb.println("}");
      ccb.println();
    }

    if (context.globals().cu_from_insertion_point_2.size() != 0) {
      Token t = null;
      ccb.printTokenSetup((context.globals().cu_from_insertion_point_2.get(0)));
      for (final Iterator<Token> it = context.globals().cu_from_insertion_point_2.iterator();
          it.hasNext(); ) {
        t = it.next();
        ccb.printToken(t);
      }
      ccb.printTrailingComments(t);
      ccb.println();
    }

    // in the include file close the class signature
    ccb.switchToIncludeFile();

    // copy other stuff
    Token t1 = context.globals().otherLanguageDeclTokenBeg;
    final Token t2 = context.globals().otherLanguageDeclTokenEnd;
    while (t1 != t2) {
      ccb.printToken(t1);
      t1 = t1.next;
    }

    if (context.globals().jjtreeGenerated) {
      ccb.println("  JJT" + context.globals().cu_name + "State jjtree;");
      ccb.println();
    }

    ccb.println("private:");
    ccb.println("  bool jj_done;");

    ccb.println();
    ccb.println("};");
  }

  void printInclude(final String userInclude) {
    if (userInclude != null && userInclude.length() > 0) {
      if (userInclude.charAt(0) == '<') {
        ccb.println("#include " + userInclude + " // user defined option");
      } else {
        ccb.println("#include \"" + userInclude + "\" // user defined option");
      }
    }
  }

  @Override
  public void finish(final CodeGeneratorSettings settings, final ParserData parserData) {
    if (Options.stringValue(Options.UO__NAMESPACE).length() > 0) {
      ccb.println(Options.stringValue("NAMESPACE_CLOSE"));
      ccb.println("#endif");
      ccb.switchToMainFile();
      ccb.println(Options.stringValue("NAMESPACE_CLOSE"));
    } else {
      ccb.println("#endif");
    }

    try {
      ccb.close();
    } catch (final IOException e) {
      throw new Error(e);
    }
  }

  /**
   * Returns true if there is a JAVACODE production that the argument expansion may directly expand
   * to (without consuming tokens or encountering lookahead).
   */
  private boolean javaCodeCheck(final Expansion exp) {
    if (exp instanceof RegularExpression) {
      return false;
    } else if (exp instanceof NonTerminal) {
      final NormalProduction prod = ((NonTerminal) exp).getProd();
      if (prod instanceof CodeProduction) {
        return true;
      } else {
        return javaCodeCheck(prod.getExpansion());
      }
    } else if (exp instanceof Choice) {
      final Choice ch = (Choice) exp;
      for (final Expansion element : ch.getChoices()) {
        if (javaCodeCheck(element)) {
          return true;
        }
      }
      return false;
    } else if (exp instanceof Sequence) {
      final Sequence seq = (Sequence) exp;
      for (int i = 0; i < seq.units.size(); i++) {
        final Expansion[] units = seq.units.toArray(new Expansion[seq.units.size()]);
        if ((units[i] instanceof Lookahead) && ((Lookahead) units[i]).isExplicit()) {
          // An explicit lookahead (rather than one generated implicitly).
          // Assume
          // the user knows what he / she is doing, e.g.
          // "A" ( "B" | LOOKAHEAD("X") jcode() | "C" )* "D"
          return false;
        } else if (javaCodeCheck(units[i])) {
          return true;
        } else if (!Semanticize.emptyExpansionExists(units[i])) {
          return false;
        }
      }
      return false;
    } else if (exp instanceof OneOrMore) {
      final OneOrMore om = (OneOrMore) exp;
      return javaCodeCheck(om.getExpansion());
    } else if (exp instanceof ZeroOrMore) {
      final ZeroOrMore zm = (ZeroOrMore) exp;
      return javaCodeCheck(zm.getExpansion());
    } else if (exp instanceof ZeroOrOne) {
      final ZeroOrOne zo = (ZeroOrOne) exp;
      return javaCodeCheck(zo.getExpansion());
    } else if (exp instanceof TryBlock) {
      final TryBlock tb = (TryBlock) exp;
      return javaCodeCheck(tb.exp);
    } else {
      return false;
    }
  }

  /**
   * An array used to store the first sets generated by the following method. A true entry means
   * that the corresponding token is in the first set.
   */
  private boolean[] firstSet;

  /**
   * Sets up the array "firstSet" above based on the Expansion argument passed to it. Since this is
   * a recursive function, it assumes that "firstSet" has been reset before the first call.
   */
  private void genFirstSet(final Expansion exp) {
    if (exp instanceof RegularExpression) {
      firstSet[((RegularExpression) exp).ordinal] = true;
    } else if (exp instanceof NonTerminal) {
      if (!(((NonTerminal) exp).getProd() instanceof CodeProduction)) {
        genFirstSet(((BNFProduction) ((NonTerminal) exp).getProd()).getExpansion());
      }
    } else if (exp instanceof Choice) {
      final Choice ch = (Choice) exp;
      for (final Expansion element : ch.getChoices()) {
        genFirstSet(element);
      }
    } else if (exp instanceof Sequence) {
      final Sequence seq = (Sequence) exp;
      final Object obj = seq.units.get(0);
      if ((obj instanceof Lookahead) && (((Lookahead) obj).getActionTokens().size() != 0)) {
        jj2LA = true;
      }
      for (int i = 0; i < seq.units.size(); i++) {
        final Expansion unit = seq.units.get(i);
        // Javacode productions can not have FIRST sets. Instead we generate the
        // FIRST set
        // for the preceding LOOKAHEAD (the semantic checks should have made
        // sure that
        // the LOOKAHEAD is suitable).
        if ((unit instanceof NonTerminal)
            && (((NonTerminal) unit).getProd() instanceof CodeProduction)) {
          if ((i > 0) && (seq.units.get(i - 1) instanceof Lookahead)) {
            final Lookahead la = (Lookahead) seq.units.get(i - 1);
            genFirstSet(la.getLaExpansion());
          }
        } else {
          genFirstSet(seq.units.get(i));
        }
        if (!Semanticize.emptyExpansionExists(seq.units.get(i))) {
          break;
        }
      }
    } else if (exp instanceof OneOrMore) {
      final OneOrMore om = (OneOrMore) exp;
      genFirstSet(om.getExpansion());
    } else if (exp instanceof ZeroOrMore) {
      final ZeroOrMore zm = (ZeroOrMore) exp;
      genFirstSet(zm.getExpansion());
    } else if (exp instanceof ZeroOrOne) {
      final ZeroOrOne zo = (ZeroOrOne) exp;
      genFirstSet(zo.getExpansion());
    } else if (exp instanceof TryBlock) {
      final TryBlock tb = (TryBlock) exp;
      genFirstSet(tb.exp);
    }
  }

  /* Constants used in the following method "buildLookaheadChecker". */
  private final int NOOPENSTM = 0;
  private final int OPENIF = 1;
  private final int OPENSWITCH = 2;

  /*
   * The phase 1 routines generates their output into String's and dumps these String's once for
   *  each method.
   * These String's contain the special characters '\u0001' to indicate a positive indent,
   *  and '\u0002' to indicate a negative indent.
   * '\n' is used to indicate a line terminator.
   * The characters '\u0003' and '\u0004' are used to delineate portions of text where '\n's
   *  should not be followed by an indentation.
   */

  /**
   * This method takes two parameters - an array of Lookahead's "<code>conds</code>", and an array
   * of String's "<code>actions</code>".<br>
   * "<code>actions</code>" contains exactly one element more than "<code>conds</code>".<br>
   * "<code>actions</code>" are Java source code, and "<code>conds</code>" translate to conditions
   * <br>
   * - so lets say "<code>f(conds[i])</code>" is <code>true</code> if the lookahead required by "
   * <code>conds[i]
   * </code>" is indeed the case. <br>
   * This method returns a string corresponding to the Java code for: <br>
   * <code>
   * if (f(conds[0]) actions[0]<br>else if (f(conds[1]) actions[1]<br>. . .<br> else actions[action.length-1]
   * </code> <br>
   * A particular action entry ("<code>actions[i]</code>") can be <code>null</code>, in which case,
   * a noop is generated for that action.
   */
  private String buildLookaheadChecker(final Lookahead[] conds, final String[] actions) {

    // The state variables.
    int state = NOOPENSTM;
    int indentAmt = 0;
    final boolean[] casedValues = new boolean[context.globals().tokenCount];
    String retval = "";
    Lookahead la;
    Token t = null;
    final int tokenMaskSize = ((context.globals().tokenCount - 1) / 32) + 1;
    int[] tokenMask = null;

    // Iterate over all the conditions.
    int index = 0;
    while (index < conds.length) {

      la = conds[index];
      jj2LA = false;

      if ((la.getAmount() == 0)
          || Semanticize.emptyExpansionExists(la.getLaExpansion())
          || javaCodeCheck(la.getLaExpansion())) {

        // This handles the following cases:
        // . If syntactic lookahead is not wanted (and hence explicitly specified as 0).
        // . If it is possible for the lookahead expansion to recognize the empty string
        //   - in which case the lookahead trivially passes.
        // . If the lookahead expansion has a JAVACODE production that it directly expands to
        //   - in which case the lookahead trivially passes.
        if (la.getActionTokens().size() == 0) {
          // In addition, if there is no semantic lookahead, then the lookahead trivially succeeds.
          // So break the main loop and treat this case as the default last action.
          break;
        } else {
          // This case is when there is only semantic lookahead
          //  (without any preceding syntactic lookahead).
          // In this case, an "if" statement is generated.
          switch (state) {
            case NOOPENSTM:
              retval += "\n" + "if (";
              indentAmt++;
              break;
            case OPENIF:
              retval += "\u0002\n" + "} else if (";
              break;
            case OPENSWITCH:
              retval += "\n" + "default:" + "\u0001";
              if (Options.getErrorReporting()) {
                retval += "\njj_la1[" + context.globals().maskindex + "] = jj_gen;";
                context.globals().maskindex++;
              }
              context.globals().maskVals.add(tokenMask);
              retval += "\n" + "if (";
              indentAmt++;
          }
          ccb.printTokenSetup(la.getActionTokens().get(0));
          for (final Iterator<Token> it = la.getActionTokens().iterator(); it.hasNext(); ) {
            t = it.next();
            retval += CodeBuilder.toString(t);
          }
          retval += ccb.getTrailingComments(t);
          retval += ") {\u0001" + actions[index];
          state = OPENIF;
        }

      } else if ((la.getAmount() == 1) && (la.getActionTokens().size() == 0)) {
        // Special optimal processing when the lookahead is exactly 1
        //  and there is no semantic lookahead.

        if (firstSet == null) {
          firstSet = new boolean[context.globals().tokenCount];
        }
        for (int i = 0; i < context.globals().tokenCount; i++) {
          firstSet[i] = false;
        }
        // jj2LA is set to false at the beginning of the containing "if" statement.
        // It is checked immediately after the end of the same statement to determine
        //  if lookaheads are to be performed using calls to the jj2 methods.
        genFirstSet(la.getLaExpansion());
        // genFirstSet may find that semantic attributes are appropriate for the next token.
        // In which case, it sets jj2LA to true.
        if (!jj2LA) {

          // This case is if there is no applicable semantic lookahead and the lookahead is one
          //  (excluding the earlier cases such as JAVACODE, etc.).
          switch (state) {
            case OPENIF:
              retval += "\u0002\n" + "} else {\u0001";
              // Control flows through to next case.
            case NOOPENSTM:
              retval += "\n" + "switch (";
              if (Options.getCacheTokens()) {
                retval += "jj_nt->kind()";
                retval += ") {\u0001";
              } else {
                retval += "(jj_ntk==-1)?jj_ntk_f():jj_ntk) {\u0001";
              }
              for (int i = 0; i < context.globals().tokenCount; i++) {
                casedValues[i] = false;
              }
              indentAmt++;
              tokenMask = new int[tokenMaskSize];
              for (int i = 0; i < tokenMaskSize; i++) {
                tokenMask[i] = 0;
              }
              // Don't need to do anything if state is OPENSWITCH.
          }
          for (int i = 0; i < context.globals().tokenCount; i++) {
            if (firstSet[i]) {
              if (!casedValues[i]) {
                casedValues[i] = true;
                retval += "\ncase ";
                final int j1 = i / 32;
                final int j2 = i % 32;
                tokenMask[j1] |= 1 << j2;
                final String s =
                    addTokenNamespace(context.globals().names_of_tokens.get(Integer.valueOf(i)));
                if (s == null) {
                  retval += i;
                } else {
                  retval += s;
                }
                retval += " : \u0001";
              }
            }
          }
          retval += "{";
          retval += actions[index];
          retval += "\nbreak;\u0002\n}";
          state = OPENSWITCH;
        }

      } else {
        // This is the case when lookahead is determined through calls to jj2 methods.
        // The other case is when lookahead is 1, but semantic attributes need to be evaluated.
        // Hence this crazy control structure.

        jj2LA = true;
      }

      if (jj2LA) {
        // In this case lookahead is determined by the jj2 methods.
        switch (state) {
          case NOOPENSTM:
            retval += "\n" + "if (";
            indentAmt++;
            break;
          case OPENIF:
            retval += "\u0002\n" + "} else if (";
            break;
          case OPENSWITCH:
            retval += "\n" + "default:" + "\u0001";
            if (Options.getErrorReporting()) {
              retval += "\njj_la1[" + context.globals().maskindex + "] = jj_gen;";
              context.globals().maskindex++;
            }
            context.globals().maskVals.add(tokenMask);
            retval += "\n" + "if (";
            indentAmt++;
        }
        context.globals().jj2index++;
        // At this point, la.la_expansion.internal_name must be "".
        internalNames.put(la.getLaExpansion(), "_" + context.globals().jj2index);
        internalIndexes.put(la.getLaExpansion(), context.globals().jj2index);
        phase2list.add(la);
        String amount;
        if (la.getAmount() == Integer.MAX_VALUE) {
          amount = "INT_MAX";
        } else {
          amount = Integer.toString(la.getAmount());
        }

        retval += "jj_2" + internalNames.get(la.getLaExpansion()) + "(" + amount + ")";
        if (la.getActionTokens().size() != 0) {
          // In addition, there is also a semantic lookahead.
          // So concatenate the semantic check with the syntactic one.
          retval += " && (";
          ccb.printTokenSetup(la.getActionTokens().get(0));
          for (final Iterator<Token> it = la.getActionTokens().iterator(); it.hasNext(); ) {
            t = it.next();
            retval += CodeBuilder.toString(t);
          }
          retval += ccb.getTrailingComments(t);
          retval += ")";
        }
        retval += ") {\u0001" + actions[index];
        state = OPENIF;
      }

      index++;
    }

    // Generate code for the default case. Note this may not be the last entry of "actions"
    //  if any condition can be statically determined to be always "true".

    switch (state) {
      case NOOPENSTM:
        retval += actions[index];
        break;
      case OPENIF:
        retval += "\u0002\n" + "} else {\u0001" + actions[index];
        break;
      case OPENSWITCH:
        retval += "\n" + "default:" + "\u0001";
        if (Options.getErrorReporting()) {
          retval += "\njj_la1[" + context.globals().maskindex + "] = jj_gen;";
          context.globals().maskVals.add(tokenMask);
          context.globals().maskindex++;
        }
        retval += actions[index];
        retval += "\u0002";
    }
    for (int i = 0; i < indentAmt; i++) {
      retval += "\u0002\n}";
    }

    return retval;
  }

  private int indentamt;

  private void dumpFormattedString(final String str) {
    char ch = ' ';
    char prevChar;
    boolean indentOn = true;
    for (int i = 0; i < str.length(); i++) {
      prevChar = ch;
      ch = str.charAt(i);
      if ((ch == '\n') && (prevChar == '\r')) {
        // do nothing - we've already printed a new line for the '\r'
        // during the previous iteration.
      } else if ((ch == '\n') || (ch == '\r')) {
        if (indentOn) {
          phase1NewLine();
        } else {
          ccb.println();
        }
      } else if (ch == '\u0001') {
        indentamt += 2;
      } else if (ch == '\u0002') {
        indentamt -= 2;
      } else if (ch == '\u0003') {
        indentOn = false;
      } else if (ch == '\u0004') {
        indentOn = true;
      } else {
        ccb.print(ch);
      }
    }
  }

  /** Print method header and return the ERROR_RETURN string. */
  private String generateCPPMethodheader(final BNFProduction p, Token t) {
    final StringBuffer sig = new StringBuffer();
    String ret, params;

    final String method_name = p.getLhs();
    boolean void_ret = false;
    boolean ptr_ret = false;

    ccb.printTokenSetup(t);
    ccb.getLeadingComments(t);
    sig.append(t.image);
    if (t.kind == JavaCCParserConstants.VOID) {
      void_ret = true;
    }
    if (t.kind == JavaCCParserConstants.STAR) {
      ptr_ret = true;
    }

    for (int i = 1; i < p.getReturnTypeTokens().size(); i++) {
      t = p.getReturnTypeTokens().get(i);
      sig.append(CodeBuilder.toString(t));
      if (t.kind == JavaCCParserConstants.VOID) {
        void_ret = true;
      }
      if (t.kind == JavaCCParserConstants.STAR) {
        ptr_ret = true;
      }
    }

    ccb.getTrailingComments(t);
    ret = sig.toString();

    sig.setLength(0);
    sig.append("(");
    if (p.getParameterListTokens().size() != 0) {
      ccb.printTokenSetup(p.getParameterListTokens().get(0));
      for (final Iterator<Token> it = p.getParameterListTokens().iterator(); it.hasNext(); ) {
        t = it.next();
        sig.append(CodeBuilder.toString(t));
      }
      sig.append(ccb.getTrailingComments(t));
    }
    sig.append(")");
    params = sig.toString();

    // For now, just ignore comments
    ccb.generateMethodDefHeader(
        ret, context.globals().cu_name, p.getLhs() + params, sig.toString());

    // Generate a default value for error return.
    String default_return;
    if (ptr_ret) {
      default_return = "NULL";
    } else if (void_ret) {
      default_return = "";
    } else {
      default_return = "0"; // 0 converts to most (all?) basic types.
    }

    final StringBuffer ret_val = new StringBuffer("\n#if !defined ERROR_RET_" + method_name + "\n");
    ret_val.append("#define ERROR_RET_" + method_name + " " + default_return + "\n");
    ret_val.append("#endif\n");
    ret_val.append("#define __ERROR_RET__ ERROR_RET_" + method_name + "\n");

    return ret_val.toString();
  }

  private void buildPhase1Routine(final BNFProduction p) {
    Token t = p.getReturnTypeTokens().get(0);
    boolean voidReturn = false;
    if (t.kind == JavaCCParserConstants.VOID) {
      voidReturn = true;
    }
    String error_ret = null;
    error_ret = generateCPPMethodheader(p, t);

    ccb.print(" {");

    if ((Options.getStopOnFirstError() && (error_ret != null))
        || ((Options.getDepthLimit() > 0) && !voidReturn)) {
      ccb.print(error_ret);
    } else {
      error_ret = null;
    }

    genStackCheck(voidReturn);

    indentamt = 2;
    if (Options.getDebugParser()) {
      ccb.println();
      ccb.println(
          "  JJEnter<std::function<void()>> jjenter([this]() {trace_call  (\""
              + ccb.escapeToUnicode(p.getLhs())
              + "\"); });");
      ccb.println(
          "  JJExit <std::function<void()>> jjexit ([this]() {trace_return(\""
              + ccb.escapeToUnicode(p.getLhs())
              + "\"); });");
      //      codeGen.println("  try {");
      ccb.print("  try {");
      indentamt += 2;
    }

    if (!Options.booleanValue(Options.UO__IGNORE_ACTIONS)
        && (p.getDeclarationTokens().size() != 0)) {
      ccb.printTokenSetup(p.getDeclarationTokens().get(0));
      for (final Iterator<Token> it = p.getDeclarationTokens().iterator(); it.hasNext(); ) {
        t = it.next();
        ccb.printToken(t);
      }
      ccb.printTrailingComments(t);
    }

    final String code = phase1ExpansionGen(p.getExpansion());
    dumpFormattedString(code);
    ccb.println();

    if (p.isJumpPatched() && !voidReturn) {
      ccb.println("  throw \"Missing return statement in function\";");
    }
    if (Options.getDebugParser()) {
      ccb.println("  } catch(...) { }");
      indentamt -= 2;
    }
    if (!voidReturn) {
      ccb.println("assert(false);");
    }

    if (error_ret != null) {
      ccb.println("\n#undef __ERROR_RET__\n");
    }
    ccb.println("}");
    ccb.println();
  }

  private void phase1NewLine() {
    ccb.println();
    for (int i = 0; i < indentamt; i++) {
      ccb.print(' ');
    }
  }

  private int gensymindex = 0;

  private String phase1ExpansionGen(final Expansion e) {
    String retval = "";
    Token t = null;
    Lookahead[] conds;
    String[] actions;
    if (e instanceof RegularExpression) {
      final RegularExpression e_nrw = (RegularExpression) e;
      retval += "\n";
      if (e_nrw.lhsTokens.size() != 0) {
        ccb.printTokenSetup(e_nrw.lhsTokens.get(0));
        for (final Iterator<Token> it = e_nrw.lhsTokens.iterator(); it.hasNext(); ) {
          t = it.next();
          retval += CodeBuilder.toString(t);
        }
        retval += ccb.getTrailingComments(t);
        retval += " = ";
      }
      final String tail = e_nrw.rhsToken == null ? ");" : ")->" + e_nrw.rhsToken.image + ";";
      if (e_nrw.label.equals("")) {
        final Object label = context.globals().names_of_tokens.get(Integer.valueOf(e_nrw.ordinal));
        if (label != null) {
          retval += "jj_consume_token(" + addTokenNamespace((String) label + tail);
        } else {
          retval += "jj_consume_token(" + e_nrw.ordinal + tail;
        }
      } else {
        retval += "jj_consume_token(" + addTokenNamespace(e_nrw.label + tail);
      }

      if (Options.getStopOnFirstError()) {
        retval += "\n    { if (hasError) { return __ERROR_RET__; } }\n";
      }

    } else if (e instanceof NonTerminal) {
      final NonTerminal e_nrw = (NonTerminal) e;
      retval += "\n";
      if (e_nrw.getLhsTokens().size() != 0) {
        ccb.printTokenSetup(e_nrw.getLhsTokens().get(0));
        for (final Iterator<Token> it = e_nrw.getLhsTokens().iterator(); it.hasNext(); ) {
          t = it.next();
          retval += CodeBuilder.toString(t);
        }
        retval += ccb.getTrailingComments(t);
        retval += " = ";
      }
      retval += e_nrw.getName() + "(";
      if (e_nrw.getArgumentTokens().size() != 0) {
        ccb.printTokenSetup(e_nrw.getArgumentTokens().get(0));
        for (final Iterator<Token> it = e_nrw.getArgumentTokens().iterator(); it.hasNext(); ) {
          t = it.next();
          retval += CodeBuilder.toString(t);
        }
        retval += ccb.getTrailingComments(t);
      }
      retval += ");";
      if (Options.getStopOnFirstError()) {
        retval += "\n    { if (hasError) { return __ERROR_RET__; } }\n";
      }
    } else if (e instanceof Action) {
      final Action e_nrw = (Action) e;
      retval += "\u0003\n";
      if (!Options.booleanValue(Options.UO__IGNORE_ACTIONS)
          && (e_nrw.getActionTokens().size() != 0)) {
        ccb.printTokenSetup(e_nrw.getActionTokens().get(0));
        for (final Iterator<Token> it = e_nrw.getActionTokens().iterator(); it.hasNext(); ) {
          t = it.next();
          retval += CodeBuilder.toString(t);
        }
        retval += ccb.getTrailingComments(t);
      }
      retval += "\u0004";
    } else if (e instanceof Choice) {
      final Choice e_nrw = (Choice) e;
      conds = new Lookahead[e_nrw.getChoices().size()];
      actions = new String[e_nrw.getChoices().size() + 1];
      actions[e_nrw.getChoices().size()] =
          "\n"
              + "jj_consume_token(-1);\n"
              + "errorHandler->parseError(token, getToken(1), __FUNCTION__), hasError = true;"
              + (Options.getStopOnFirstError() ? "\nreturn __ERROR_RET__;" : "");
      // In previous line, the "throw" never throws an exception since the evaluation of
      //  jj_consume_token(-1) causes ParseException to be thrown first.
      Sequence nestedSeq;
      for (int i = 0; i < e_nrw.getChoices().size(); i++) {
        nestedSeq = (Sequence) e_nrw.getChoices().get(i);
        actions[i] = phase1ExpansionGen(nestedSeq);
        conds[i] = (Lookahead) nestedSeq.units.get(0);
      }
      retval = buildLookaheadChecker(conds, actions);
    } else if (e instanceof Sequence) {
      final Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the Lookahead object.
      for (int i = 1; i < e_nrw.units.size(); i++) {
        // For C++, since we are not using exceptions, we will protect all the
        //  expansion choices with if (!error)
        boolean wrap_in_block = false;
        if (!context.globals().jjtreeGenerated) {
          // for the last one, if it's an action, we will not protect it.
          final Expansion elem = e_nrw.units.get(i);
          if (!(elem instanceof Action)
              || !(e.parent instanceof BNFProduction)
              || (i != (e_nrw.units.size() - 1))) {
            wrap_in_block = true;
            retval += "\nif (!hasError) {\u0001";
          }
        }
        retval += phase1ExpansionGen(e_nrw.units.get(i));
        if (wrap_in_block) {
          retval += "\u0002\n}";
        }
      }
    } else if (e instanceof OneOrMore) {
      final OneOrMore e_nrw = (OneOrMore) e;
      final Expansion nested_e = e_nrw.getExpansion();
      Lookahead la;
      if (nested_e instanceof Sequence) {
        la = (Lookahead) ((Sequence) nested_e).units.get(0);
      } else {
        la = new Lookahead();
        la.setAmount(Options.getLookahead());
        la.setLaExpansion(nested_e);
      }
      retval += "\n";
      final int labelIndex = ++gensymindex;
      retval += "while (!hasError) {\u0001";
      retval += phase1ExpansionGen(nested_e);
      conds = new Lookahead[1];
      conds[0] = la;
      actions = new String[2];
      actions[0] = "\n;";
      actions[1] = "\ngoto end_label_" + labelIndex + ";";

      retval += buildLookaheadChecker(conds, actions);
      retval += "\u0002\n" + "}";
      retval += "\nend_label_" + labelIndex + ": ;";
    } else if (e instanceof ZeroOrMore) {
      final ZeroOrMore e_nrw = (ZeroOrMore) e;
      final Expansion nested_e = e_nrw.getExpansion();
      Lookahead la;
      if (nested_e instanceof Sequence) {
        la = (Lookahead) ((Sequence) nested_e).units.get(0);
      } else {
        la = new Lookahead();
        la.setAmount(Options.getLookahead());
        la.setLaExpansion(nested_e);
      }
      retval += "\n";
      final int labelIndex = ++gensymindex;
      retval += "while (!hasError) {\u0001";
      conds = new Lookahead[1];
      conds[0] = la;
      actions = new String[2];
      actions[0] = "\n;";
      actions[1] = "\ngoto end_label_" + labelIndex + ";";
      retval += buildLookaheadChecker(conds, actions);
      retval += phase1ExpansionGen(nested_e);
      retval += "\u0002\n" + "}";
      retval += "\nend_label_" + labelIndex + ": ;";
    } else if (e instanceof ZeroOrOne) {
      final ZeroOrOne e_nrw = (ZeroOrOne) e;
      final Expansion nested_e = e_nrw.getExpansion();
      Lookahead la;
      if (nested_e instanceof Sequence) {
        la = (Lookahead) ((Sequence) nested_e).units.get(0);
      } else {
        la = new Lookahead();
        la.setAmount(Options.getLookahead());
        la.setLaExpansion(nested_e);
      }
      conds = new Lookahead[1];
      conds[0] = la;
      actions = new String[2];
      actions[0] = phase1ExpansionGen(nested_e);
      actions[1] = "\n;";
      retval += buildLookaheadChecker(conds, actions);
    } else if (e instanceof TryBlock) {
      final TryBlock e_nrw = (TryBlock) e;
      final Expansion nested_e = e_nrw.exp;
      List<Token> list;
      retval += "\n";
      retval += "try {\u0001";
      retval += phase1ExpansionGen(nested_e);
      retval += "\u0002\n" + "}";
      for (int i = 0; i < e_nrw.catchblks.size(); i++) {
        retval += " catch (";
        list = e_nrw.types.get(i);
        if (list.size() != 0) {
          ccb.printTokenSetup(list.get(0));
          for (final Iterator<Token> it = list.iterator(); it.hasNext(); ) {
            t = it.next();
            retval += CodeBuilder.toString(t);
          }
          retval += ccb.getTrailingComments(t);
        }
        // retval += " ";
        // t = (Token)(e_nrw.ids.get(i));
        // codeGen.printTokenSetup(t);
        // retval += codeGen.getStringToPrint(t);
        // retval += codeGen.getTrailingComments(t);
        // retval += ") {\u0003\n";
        list = e_nrw.catchblks.get(i);
        if (list.size() != 0) {
          ccb.printTokenSetup((list.get(0)));
          for (final Iterator<Token> it = list.iterator(); it.hasNext(); ) {
            t = it.next();
            retval += CodeBuilder.toString(t);
          }
          retval += ccb.getTrailingComments(t);
        }
        retval += "\u0004\n" + "}";
      }
      if (e_nrw.finallyblk != null) {
        retval += " finally {\u0003\n";

        if (e_nrw.finallyblk.size() != 0) {
          ccb.printTokenSetup(e_nrw.finallyblk.get(0));
          for (final Iterator<Token> it = e_nrw.finallyblk.iterator(); it.hasNext(); ) {
            t = it.next();
            retval += CodeBuilder.toString(t);
          }
          retval += ccb.getTrailingComments(t);
        }
        retval += "\u0004\n" + "}";
      }
    }
    return retval;
  }

  private void buildPhase2Routine(final Lookahead la) {
    final Expansion e = la.getLaExpansion();
    ccb.println("  inline bool ", "jj_2" + internalNames.get(e) + "(int xla) {");
    ccb.println("    jj_la = xla; jj_lastpos = jj_scanpos = token;");

    String ret_suffix = "";
    if (Options.getDepthLimit() > 0) {
      ret_suffix = " && !jj_depth_error";
    }

    ccb.println("    jj_done = false;");
    ccb.println("    return (!jj_3" + internalNames.get(e) + "() || jj_done)" + ret_suffix + ";");
    if (Options.getErrorReporting()) {
      ccb.println(
          "    jj_save(" + (Integer.parseInt(internalNames.get(e).substring(1)) - 1) + ", xla);");
    }
    ccb.println("  }");
    ccb.println();
    final Phase3Data p3d = new Phase3Data(e, la.getAmount());
    phase3list.add(p3d);
    phase3table.put(e, p3d);
  }

  private boolean xsp_declared;

  private Expansion jj3_expansion;

  private String genReturn(final boolean value) {
    final String retval = value ? "true" : "false";
    if (Options.getDebugLookahead() && (jj3_expansion != null)) {
      String ind = "";
      for (int i = 0; i < indentamt; i++) {
        ind += " ";
      }
      String str = "";
      String tracecode =
          "trace_la_return(\""
              + ccb.escapeToUnicode(((NormalProduction) jj3_expansion.parent).getLhs())
              + "(LOOKAHEAD "
              + (value ? "FAILED" : "SUCCEEDED")
              + ")\");";
      if (Options.getErrorReporting()) {
        tracecode = "if (!jj_rescan) " + tracecode;
      }
      str += "{\n  " + ind;
      str += tracecode + "\n  " + ind;
      str += "return " + retval + ";\n" + ind;
      str += "}";
      return str;
    } else {
      return "return " + retval + ";";
    }
  }

  private static String getTokenImages() {
    return addTokenNamespace("tokenImages");
  }

  private static String getTokenLabels() {
    return addTokenNamespace("tokenLabels");
  }

  private void generate3R(final Expansion e, final Phase3Data inf) {
    Expansion seq = e;
    if (!internalNames.containsKey(e) || internalNames.get(e).equals("")) {
      while (true) {
        if ((seq instanceof Sequence) && (((Sequence) seq).units.size() == 2)) {
          seq = ((Sequence) seq).units.get(1);
        } else if (seq instanceof NonTerminal) {
          final NonTerminal e_nrw = (NonTerminal) seq;
          final NormalProduction ntprod = context.globals().production_table.get(e_nrw.getName());
          if (ntprod instanceof CodeProduction) {
            break; // nothing to do here
          } else {
            seq = ntprod.getExpansion();
          }
        } else {
          break;
        }
      }

      if (seq instanceof RegularExpression) {
        final RegularExpression re = (RegularExpression) seq;
        String jj_scan_token = "jj_scan_token";
        if (re.label.equals("")) {
          final Object label = context.globals().names_of_tokens.get(Integer.valueOf(re.ordinal));
          if (label != null) {
            jj_scan_token += "(" + addTokenNamespace((String) label) + ")";
          } else {
            jj_scan_token += "(" + re.ordinal + ")";
          }
        } else {
          jj_scan_token += "(" + addTokenNamespace(re.label) + ")";
        }
        internalNames.put(e, jj_scan_token);
        return;
      }

      gensymindex++;
      // if (gensymindex == 100)
      // {
      // new Error().codeGen.printStackTrace();
      // System.out.println(" ***** seq: " + seq.internal_name + "; size: " +
      // ((Sequence)seq).units.size());
      // }
      internalNames.put(
          e,
          "R_"
              + e.getProductionName()
              + "_"
              + e.getLine()
              + "_"
              + e.getColumn()
              + "_"
              + gensymindex);
      internalIndexes.put(e, gensymindex);
    }
    Phase3Data p3d = phase3table.get(e);
    if ((p3d == null) || (p3d.count < inf.count)) {
      p3d = new Phase3Data(e, inf.count);
      phase3list.add(p3d);
      phase3table.put(e, p3d);
    }
  }

  private void setupPhase3Builds(final Phase3Data inf) {
    final Expansion e = inf.exp;
    if (e instanceof RegularExpression) {
      // nothing to here
    } else if (e instanceof NonTerminal) {
      // All expansions of non-terminals have the "name" fields set.
      // So there's no need to check it below for "e_nrw" and "ntexp".
      // We rely here on the fact that the "name" fields of both these variables are the same.
      final NonTerminal e_nrw = (NonTerminal) e;
      final NormalProduction ntprod = context.globals().production_table.get(e_nrw.getName());
      if (ntprod instanceof CodeProduction) {
        // nothing to do here
      } else {
        generate3R(ntprod.getExpansion(), inf);
      }
    } else if (e instanceof Choice) {
      final Choice e_nrw = (Choice) e;
      for (final Expansion element : e_nrw.getChoices()) {
        generate3R(element, inf);
      }
    } else if (e instanceof Sequence) {
      final Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the Lookahead object.
      int cnt = inf.count;
      for (int i = 1; i < e_nrw.units.size(); i++) {
        final Expansion eseq = e_nrw.units.get(i);
        setupPhase3Builds(new Phase3Data(eseq, cnt));
        cnt -= minimumSize(eseq);
        if (cnt <= 0) {
          break;
        }
      }
    } else if (e instanceof TryBlock) {
      final TryBlock e_nrw = (TryBlock) e;
      setupPhase3Builds(new Phase3Data(e_nrw.exp, inf.count));
    } else if (e instanceof OneOrMore) {
      final OneOrMore e_nrw = (OneOrMore) e;
      generate3R(e_nrw.getExpansion(), inf);
    } else if (e instanceof ZeroOrMore) {
      final ZeroOrMore e_nrw = (ZeroOrMore) e;
      generate3R(e_nrw.getExpansion(), inf);
    } else if (e instanceof ZeroOrOne) {
      final ZeroOrOne e_nrw = (ZeroOrOne) e;
      generate3R(e_nrw.getExpansion(), inf);
    }
  }

  private static String addTokenNamespace(String token) {
    if (token != null) {
      if (!Options.getTokenConstantsNamespace().isEmpty()) {
        token = Options.getTokenConstantsNamespace() + "::" + token;
      }
    }
    return token;
  }

  private String getTokenType() {
    String type = "Token";
    if (Options.getTokenClass().isEmpty()) {
      type = "Token";
    } else {
      type = Options.getTokenClass();
    }

    if (!Options.getTokenNamespace().isEmpty()) {
      type = Options.getTokenNamespace() + "::" + type;
    }
    return type;
  }

  @SuppressWarnings("unused")
  private String getTokenTypePointer() {
    return getTokenType() + "*";
  }

  private String genjj_3Call(final Expansion e) {
    if (internalNames.containsKey(e) && internalNames.get(e).startsWith("jj_scan_token")) {
      return internalNames.get(e);
    } else {
      return "jj_3" + internalNames.get(e) + "()";
    }
  }

  private void buildPhase3Routine(final Phase3Data inf, final boolean recursive_call) {
    final Expansion e = inf.exp;
    Token t = null;
    if (internalNames.containsKey(e) && internalNames.get(e).startsWith("jj_scan_token")) {
      return;
    }

    if (!recursive_call) {
      ccb.println("  inline bool ", "jj_3" + internalNames.get(e) + "() {");
      ccb.println("    if (jj_done) return true;");
      if (Options.getDepthLimit() > 0) {
        ccb.println("#define __ERROR_RET__ true");
      }
      genStackCheck(false);
      xsp_declared = false;
      if (Options.getDebugLookahead() && (e.parent instanceof NormalProduction)) {
        ccb.print("    ");
        if (Options.getErrorReporting()) {
          ccb.print("if (!jj_rescan) ");
        }
        ccb.println(
            "trace_la_call(\""
                + ccb.escapeToUnicode(((NormalProduction) e.parent).getLhs())
                + "(LOOKING AHEAD...)\");");
        jj3_expansion = e;
      } else {
        jj3_expansion = null;
      }
    }
    if (e instanceof RegularExpression) {
      final RegularExpression e_nrw = (RegularExpression) e;
      if (e_nrw.label.equals("")) {
        final Object label = context.globals().names_of_tokens.get(Integer.valueOf(e_nrw.ordinal));
        if (label != null) {
          indentamt += 2;
          ccb.println(
              "    if (jj_scan_token("
                  + addTokenNamespace((String) label)
                  + ")) "
                  + genReturn(true));
          indentamt -= 2;
        } else {
          indentamt += 2;
          ccb.println("    if (jj_scan_token(" + e_nrw.ordinal + ")) " + genReturn(true));
          indentamt -= 2;
        }
      } else {
        indentamt += 2;
        ccb.println(
            "    if (jj_scan_token(" + addTokenNamespace(e_nrw.label) + ")) " + genReturn(true));
        indentamt -= 2;
      }
      // codeGen.genCodeLine(" if (jj_la == 0 && jj_scanpos == jj_lastpos) " + genReturn(false));
    } else if (e instanceof NonTerminal) {
      // All expansions of non-terminals have the "name" fields set.
      // So there's no need to check it below for "e_nrw" and "ntexp".
      // We rely here on the fact that the "name" fields of both these variables are the same.
      final NonTerminal e_nrw = (NonTerminal) e;
      final NormalProduction ntprod = context.globals().production_table.get(e_nrw.getName());
      if (ntprod instanceof CodeProduction) {
        ccb.println(
            "    if (true) { jj_la = 0; jj_scanpos = jj_lastpos; " + genReturn(false) + "}");
      } else {
        final Expansion ntexp = ntprod.getExpansion();
        // codeGen.genCodeLine(" if (jj_3" + ntexp.internal_name + "()) " + genReturn(true));
        ccb.println("    if (" + genjj_3Call(ntexp) + ") " + genReturn(true));
        // codeGen.genCodeLine(" if (jj_la == 0 && jj_scanpos == jj_lastpos) " + genReturn(false));
      }
    } else if (e instanceof Choice) {
      Sequence nested_seq;
      final Choice e_nrw = (Choice) e;
      if (e_nrw.getChoices().size() != 1) {
        if (!xsp_declared) {
          xsp_declared = true;
          ccb.println("    Token* xsp;");
        }
        ccb.println("    xsp = jj_scanpos;");
      }
      for (int i = 0; i < e_nrw.getChoices().size(); i++) {
        nested_seq = (Sequence) e_nrw.getChoices().get(i);
        final Lookahead la = (Lookahead) nested_seq.units.get(0);
        if (la.getActionTokens().size() != 0) {
          // We have semantic lookahead that must be evaluated.
          context.globals().lookaheadNeeded = true;
          ccb.println("    jj_lookingAhead = true;");
          ccb.print("    jj_semLA = ");
          ccb.printTokenSetup(la.getActionTokens().get(0));
          for (final Iterator<Token> it = la.getActionTokens().iterator(); it.hasNext(); ) {
            t = it.next();
            ccb.printToken(t);
          }
          ccb.printTrailingComments(t);
          ccb.println(";");
          ccb.println("    jj_lookingAhead = false;");
        }
        for (int k = 0; k < indentamt; k++) {
          ccb.print(' ');
        }
        ccb.print("  if (");
        if (la.getActionTokens().size() != 0) {
          ccb.print("!jj_semLA || ");
        }
        if (i != (e_nrw.getChoices().size() - 1)) {
          // codeGen.genCodeLine("jj_3" + nested_seq.internal_name + "())
          // {");
          // codeGen.println(genjj_3Call(nested_seq) + ") {");
          ccb.println(genjj_3Call(nested_seq) + ") {");
          indentamt += 2;
          ccb.println("      jj_scanpos = xsp;");
        } else {
          // codeGen.genCodeLine("jj_3" + nested_seq.internal_name + "())
          // " + genReturn(true));
          ccb.print(genjj_3Call(nested_seq) + ") ");
          indentamt += 2;
          ccb.println(genReturn(true));
          indentamt -= 2;
          // codeGen.genCodeLine(" if (jj_la == 0 && jj_scanpos ==
          // jj_lastpos) " + genReturn(false));
        }
      }
      for (int i = 1; i < e_nrw.getChoices().size(); i++) {
        // codeGen.genCodeLine(" } else if (jj_la == 0 && jj_scanpos ==
        // jj_lastpos) " + genReturn(false));
        indentamt -= 2;
        for (int k = 0; k < indentamt; k++) {
          ccb.print(' ');
        }
        ccb.println("  }");
      }
    } else if (e instanceof Sequence) {
      final Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the
      // Lookahead object.
      int cnt = inf.count;
      for (int i = 1; i < e_nrw.units.size(); i++) {
        final Expansion eseq = e_nrw.units.get(i);
        buildPhase3Routine(new Phase3Data(eseq, cnt), true);

        // System.out.println("minimumSize: line: " + eseq.line + ", column: " +
        // eseq.column + ": " +
        // minimumSize(eseq));//Test Code

        cnt -= minimumSize(eseq);
        if (cnt <= 0) {
          break;
        }
      }
    } else if (e instanceof TryBlock) {
      final TryBlock e_nrw = (TryBlock) e;
      buildPhase3Routine(new Phase3Data(e_nrw.exp, inf.count), true);
    } else if (e instanceof OneOrMore) {
      if (!xsp_declared) {
        xsp_declared = true;
        ccb.println("    Token* xsp;");
      }
      final OneOrMore e_nrw = (OneOrMore) e;
      final Expansion nested_e = e_nrw.getExpansion();
      // codeGen.genCodeLine(" if (jj_3" + nested_internalNames.get(e) +
      // "()) " + genReturn(true));
      ccb.println("    if (" + genjj_3Call(nested_e) + ") " + genReturn(true));
      // codeGen.genCodeLine(" if (jj_la == 0 && jj_scanpos == jj_lastpos)
      // " + genReturn(false));
      ccb.println("    while (true) {");
      ccb.println("      xsp = jj_scanpos;");
      // codeGen.genCodeLine(" if (jj_3" + nested_internalNames.get(e) +
      // "()) { jj_scanpos = xsp; break; }");
      ccb.println("      if (" + genjj_3Call(nested_e) + ") { jj_scanpos = xsp; break; }");
      // codeGen.genCodeLine(" if (jj_la == 0 && jj_scanpos == jj_lastpos)
      // " + genReturn(false));
      ccb.println("    }");
    } else if (e instanceof ZeroOrMore) {
      if (!xsp_declared) {
        xsp_declared = true;
        ccb.println("    Token* xsp;");
      }
      final ZeroOrMore e_nrw = (ZeroOrMore) e;
      final Expansion nested_e = e_nrw.getExpansion();
      ccb.println("    while (true) {");
      ccb.println("      xsp = jj_scanpos;");
      // codeGen.genCodeLine(" if (jj_3" + nested_internalNames.get(e) +
      // "()) { jj_scanpos = xsp; break; }");
      ccb.println("      if (" + genjj_3Call(nested_e) + ") { jj_scanpos = xsp; break; }");
      // codeGen.genCodeLine(" if (jj_la == 0 && jj_scanpos == jj_lastpos)
      // " + genReturn(false));
      ccb.println("    }");
    } else if (e instanceof ZeroOrOne) {
      if (!xsp_declared) {
        xsp_declared = true;
        ccb.println("    Token* xsp;");
      }
      final ZeroOrOne e_nrw = (ZeroOrOne) e;
      final Expansion nested_e = e_nrw.getExpansion();
      ccb.println("    xsp = jj_scanpos;");
      // codeGen.genCodeLine(" if (jj_3" + nested_internalNames.get(e) +
      // "()) jj_scanpos = xsp;");
      ccb.println("    if (" + genjj_3Call(nested_e) + ") jj_scanpos = xsp;");
      // codeGen.genCodeLine(" else if (jj_la == 0 && jj_scanpos ==
      // jj_lastpos) " + genReturn(false));
    }
    if (!recursive_call) {
      indentamt += 2;
      ccb.println("    " + genReturn(false));
      if (Options.getDepthLimit() > 0) {
        ccb.println("#undef __ERROR_RET__");
      }
      indentamt -= 2;
      ccb.println("  }");
      ccb.println();
    }
  }

  private int minimumSize(final Expansion e) {
    return minimumSize(e, Integer.MAX_VALUE);
  }

  /** Returns the minimum number of tokens that can parse to this expansion. */
  private int minimumSize(final Expansion e, final int oldMin) {
    int retval = 0; // should never be used. Will be bad if it is.
    if (e.inMinimumSize) {
      // recursive search for minimum size unnecessary.
      return Integer.MAX_VALUE;
    }
    e.inMinimumSize = true;
    if (e instanceof RegularExpression) {
      retval = 1;
    } else if (e instanceof NonTerminal) {
      final NonTerminal e_nrw = (NonTerminal) e;
      final NormalProduction ntprod = context.globals().production_table.get(e_nrw.getName());
      if (ntprod instanceof CodeProduction) {
        retval = Integer.MAX_VALUE;
        // Make caller think this is unending (for we do not go beyond JAVACODE
        // during
        // phase3 execution).
      } else {
        final Expansion ntexp = ntprod.getExpansion();
        retval = minimumSize(ntexp);
      }
    } else if (e instanceof Choice) {
      int min = oldMin;
      Expansion nested_e;
      final Choice e_nrw = (Choice) e;
      for (int i = 0; (min > 1) && (i < e_nrw.getChoices().size()); i++) {
        nested_e = e_nrw.getChoices().get(i);
        final int min1 = minimumSize(nested_e, min);
        if (min > min1) {
          min = min1;
        }
      }
      retval = min;
    } else if (e instanceof Sequence) {
      int min = 0;
      final Sequence e_nrw = (Sequence) e;
      // We skip the first element in the following iteration since it is the
      // Lookahead object.
      for (int i = 1; i < e_nrw.units.size(); i++) {
        final Expansion eseq = e_nrw.units.get(i);
        final int mineseq = minimumSize(eseq);
        if ((min == Integer.MAX_VALUE) || (mineseq == Integer.MAX_VALUE)) {
          min = Integer.MAX_VALUE; // Adding infinity to something results in
          // infinity.
        } else {
          min += mineseq;
          if (min > oldMin) {
            break;
          }
        }
      }
      retval = min;
    } else if (e instanceof TryBlock) {
      final TryBlock e_nrw = (TryBlock) e;
      retval = minimumSize(e_nrw.exp);
    } else if (e instanceof OneOrMore) {
      final OneOrMore e_nrw = (OneOrMore) e;
      retval = minimumSize(e_nrw.getExpansion());
    } else if (e instanceof ZeroOrMore) {
      retval = 0;
    } else if (e instanceof ZeroOrOne) {
      retval = 0;
    } else if (e instanceof Lookahead) {
      retval = 0;
    } else if (e instanceof Action) {
      retval = 0;
    }
    e.inMinimumSize = false;
    return retval;
  }

  private void genStackCheck(final boolean voidReturn) {
    if (Options.getDepthLimit() > 0) {
      if (!voidReturn) {
        ccb.println("if (jj_depth_error) { return __ERROR_RET__; }");
      } else {
        ccb.println("if (jj_depth_error) { return; }");
      }
      ccb.println("__jj_depth_inc __jj_depth_counter(this);");
      ccb.println("if (jj_depth > " + Options.getDepthLimit() + ") {");
      ccb.println("  jj_depth_error = true;");
      ccb.println("  jj_consume_token(-1);");
      ccb.println("  errorHandler->parseError(token, getToken(1), __FUNCTION__), hasError = true;");
      if (!voidReturn) {
        ccb.println("  return __ERROR_RET__;"); // Non-recoverable
        // error
      } else {
        ccb.println("  return;"); // Non-recoverable error
      }
      ccb.println("}");
    }
  }

  private void build() {
    NormalProduction p;
    CppCodeProduction cp;

    for (final Iterator<NormalProduction> prodIterator =
            context.globals().bnfproductions.iterator();
        prodIterator.hasNext(); ) {
      p = prodIterator.next();
      if (p instanceof CppCodeProduction) {
        cp = (CppCodeProduction) p;

        final StringBuffer sig = new StringBuffer();
        String ret, params;
        Token t = null;

        p.getLhs();

        for (final Token element : p.getReturnTypeTokens()) {
          t = element;
          CodeBuilder.toString(t);
          sig.append(t.toString());
          sig.append(" ");
        }

        if (t != null) {
          ccb.getTrailingComments(t);
        }
        ret = sig.toString();

        sig.setLength(0);
        sig.append("(");
        if (p.getParameterListTokens().size() != 0) {
          ccb.printTokenSetup(p.getParameterListTokens().get(0));
          for (final Iterator<Token> it = p.getParameterListTokens().iterator(); it.hasNext(); ) {
            t = it.next();
            sig.append(CodeBuilder.toString(t));
          }
          sig.append(ccb.getTrailingComments(t));
        }
        sig.append(")");
        params = sig.toString();

        // For now, just ignore comments
        ccb.generateMethodDefHeader(
            ret, context.globals().cu_name, p.getLhs() + params, sig.toString());
        ccb.println(" {");
        if (Options.getDebugParser()) {
          ccb.println();
          ccb.println(
              "    JJEnter<std::function<void()>> jjenter([this]() {trace_call  (\""
                  + ccb.escapeToUnicode(cp.getLhs())
                  + "\"); });");
          ccb.println(
              "    JJExit <std::function<void()>> jjexit ([this]() {trace_return(\""
                  + ccb.escapeToUnicode(cp.getLhs())
                  + "\"); });");
          ccb.println("    try {");
        }
        if (cp.getCodeTokens().size() != 0) {
          ccb.printTokenSetup(cp.getCodeTokens().get(0));
          ccb.printTokenList(cp.getCodeTokens());
        }
        ccb.println();
        if (Options.getDebugParser()) {
          ccb.println("    } catch(...) { }");
        }
        ccb.println("  }");
        ccb.println();
      } else if (p instanceof JavaCodeProduction) {
        context.errors().semantic_error("Cannot use JAVACODE productions with C++ output (yet).");
        continue;
      } else {
        buildPhase1Routine((BNFProduction) p);
      }
    }

    ccb.switchToIncludeFile();
    for (final Lookahead element : phase2list) {
      buildPhase2Routine(element);
    }

    int phase3index = 0;

    while (phase3index < phase3list.size()) {
      for (; phase3index < phase3list.size(); phase3index++) {
        setupPhase3Builds(phase3list.get(phase3index));
      }
    }

    for (final Enumeration<Phase3Data> enumeration = phase3table.elements();
        enumeration.hasMoreElements(); ) {
      buildPhase3Routine(enumeration.nextElement(), false);
    }
    ccb.switchToMainFile();
  }
}

/** This class stores information to pass from phase 2 to phase 3. */
class Phase3Data {

  /** The expansion to generate the jj3 method for. */
  Expansion exp;

  /**
   * The number of tokens that can still be consumed.<br>
   * This number is used to limit the number of jj3 methods generated.
   */
  int count;

  Phase3Data(final Expansion e, final int c) {
    exp = e;
    count = c;
  }
}
