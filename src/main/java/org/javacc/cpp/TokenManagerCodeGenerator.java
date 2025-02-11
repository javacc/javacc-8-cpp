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
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.Context;
import org.javacc.parser.Options;
import org.javacc.parser.TokenizerData;

/** Class that implements a table driven code generator for the token manager in C++. */
class TokenManagerCodeGenerator implements org.javacc.parser.TokenManagerCodeGenerator {

  private static final String tokenManagerTemplateCC =
      "/templates/cpp/TokenManagerDriver.cc.template";
  private static final String tokenManagerTemplateH =
      "/templates/cpp/TokenManagerDriver.h.template";

  private final Context context;
  private CppCodeBuilder ccb;

  TokenManagerCodeGenerator(final Context context) {
    this.context = context;
  }

  @Override
  public void generateCode(
      final CodeGeneratorSettings settings, final TokenizerData tokenizerData) {

    settings.putAll(Options.getOptions());

    settings.put(
        Options.NUO__PARSER_NAME_UPPER_CASE, tokenizerData.parserName.toUpperCase());

    settings.put("maxOrdinal", tokenizerData.allMatches.size());
    settings.put("firstLexState", tokenizerData.lexStateNames[0]);
    settings.put(
        "lastLexState", tokenizerData.lexStateNames[tokenizerData.lexStateNames.length - 1]);
    settings.put("nfaSize", tokenizerData.nfa.size());
    settings.put("charsVectorSize", ((Character.MAX_VALUE >> 6) + 1));
    settings.put("stateSetSize", tokenizerData.nfa.size());
    settings.put("parserName", tokenizerData.parserName);
    settings.put("maxLongs", (tokenizerData.allMatches.size() / 64) + 1);
    settings.put("parserName", tokenizerData.parserName);
    settings.put("charStreamName", Options.getCharStreamName());
    settings.put("defaultLexState", tokenizerData.defaultLexState);
    settings.put("decls", tokenizerData.decls);
    settings.put("generatedStates", tokenizerData.nfa.size());

    settings.put("noDfa", Options.getNoDfa());

    if (Options.getTokenClass().isEmpty()) {
      settings.put("tokenClass", "Token");
    } else {
      settings.put("tokenClass", Options.getTokenClass());
    }
    if (Options.getTokenInclude().isEmpty()) {
      settings.put("tokenInclude", "Token.h");
    } else {
      settings.put("tokenInclude", Options.getTokenInclude());
    }

    try {
      final File file =
          new File(Options.getOutputDirectory(), tokenizerData.parserName + "TokenManager.cc");
      ccb = CppCodeBuilder.of(context, settings).setFile(file);

      if (Options.hasNamespace()) {
        ccb.println(
            "namespace " + Options.stringValue("NAMESPACE_OPEN") + " // user defined option");
        ccb.println();
      }

      ccb.println("/* Beginning of code from " + tokenManagerTemplateCC + " */");
      ccb.println();
      ccb.printTemplate(tokenManagerTemplateCC);
      ccb.println();
      ccb.println("/* End of code from " + tokenManagerTemplateCC + " */");
      ccb.println();

      ccb.switchToIncludeFile(); // remaining variables
      ccb.println("/* Beginning of code from " + tokenManagerTemplateH + " */");
      ccb.println();
      ccb.printTemplate(tokenManagerTemplateH, settings);
      ccb.println();
      ccb.println("/* End of code from " + tokenManagerTemplateH + " */");
      ccb.println();

      ccb.switchToStaticsFile();
      ccb.println("#include \"TokenManagerError.h\"");
      ccb.println("#include \"DefaultTokenManagerErrorHandler.h\"");

      final String tmi = Options.getTokenManagerInclude();
      if (!tmi.isEmpty()) {
        if (tmi.charAt(0) == '<') {
          ccb.println("#include " + tmi + " // user defined option");
        } else {
          ccb.println("#include \"" + tmi + "\" // user defined option");
        }
      }

      if (!Options.getNoDfa()) {
        ccb.println();
        ccb.println("/* no user defined NO_DFA option */");
        dumpDfaTables(ccb, tokenizerData);
      }

      dumpNfaTables(ccb, tokenizerData);
      dumpMatchInfo(ccb, tokenizerData);

    } catch (final IOException ioe) {
      ioe.printStackTrace();
      assert (false);
    }
  }

  @Override
  public void finish(final CodeGeneratorSettings settings, final TokenizerData tokenizerData) {
    if (!Options.getBuildTokenManager()) {
      return;
    }

    if (Options.stringValue(Options.UO__NAMESPACE).length() > 0) {
      ccb.switchToMainFile();
      ccb.println(Options.stringValue("NAMESPACE_CLOSE"));
    }

    try {
      ccb.close();
    } catch (final IOException ioe) {
      ioe.printStackTrace();
      throw new Error(ioe);
    }
  }

  private static void dumpDfaTables(final CppCodeBuilder ccb, final TokenizerData tokenizerData) {
    final Map<Integer, int[]> startAndSize = new HashMap<>();
    int i = 0;

    ccb.println("static const long long stringLiterals[] = {");
    for (final int key : tokenizerData.literalSequence.keySet()) {
      final int[] arr = new int[2];
      final List<String> l = tokenizerData.literalSequence.get(key);
      final List<Integer> kinds = tokenizerData.literalKinds.get(key);
      arr[0] = i;
      arr[1] = l.size();
      int j = 0;
      if (i > 0) {
        ccb.println(",");
      }
      for (final String s : l) {
        if (j > 0) {
          ccb.println(", ");
        }
        final int kind = kinds.get(j);
        final boolean ignoreCase = tokenizerData.ignoreCaseKinds.contains(kind);
        ccb.print("  ");
        ccb.print(s.length() + "LL");
        ccb.print(", ");
        ccb.print(ignoreCase ? 1 : 0);
        for (int k = 0; k < s.length(); k++) {
          ccb.print(", ");
          ccb.print((int) s.charAt(k) + "LL");
          i++;
        }
        if (ignoreCase) {
          for (int k = 0; k < s.length(); k++) {
            ccb.print(", ");
            ccb.print((int) s.toUpperCase().charAt(k) + "LL");
            i++;
          }
        }
        ccb.print(", " + kind + "LL");
        ccb.print(", " + tokenizerData.kindToNfaStartState.get(kind) + "LL");
        i += 4;
        j++;
      }
      startAndSize.put(key, arr);
    }
    ccb.println();
    ccb.println("};");
    ccb.println();

    ccb.switchToMainFile();
    // Token actions.
    ccb.println(
        "int "
            + tokenizerData.parserName
            + "TokenManager::getStartAndSize(int index, int isCount)\n{");
    ccb.println("  switch(index) {");
    for (final int key : tokenizerData.literalSequence.keySet()) {
      final int[] arr = startAndSize.get(key);
      ccb.println(
          "    case " + key + ": { return (isCount == 0) ? " + arr[0] + " : " + arr[1] + ";}");
    }
    ccb.println("  }");
    ccb.println("  return -1;");
    ccb.println("}");
    ccb.println();

    ccb.switchToStaticsFile();
  }

  private static void dumpNfaTables(final CppCodeBuilder ccb, final TokenizerData tokenizerData) {
    // WE do the following for java so that the generated code is reasonable
    // size and can be compiled. May not be needed for other languages.
    final Map<Integer, TokenizerData.NfaState> nfa = tokenizerData.nfa;

    int length = 0;
    final int lengths[] = new int[nfa.size()];
    for (int i = 0; i < nfa.size(); i++) {
      final TokenizerData.NfaState tmp = nfa.get(i);
      if (tmp != null) {
        final BitSet bits = new BitSet();
        for (final char c : tmp.characters) {
          bits.set(c);
        }

        lengths[i] = 0;
        final long[] longs = bits.toLongArray();
        for (int k = 0; k < longs.length; k++) {
          int rep = 1;
          while (((k + rep) < longs.length) && (longs[k + rep] == longs[k])) {
            rep++;
          }
          k += rep - 1;
          lengths[i] = lengths[i] + 2;
        }
        length = Math.max(length, lengths[i]);
      }
    }

    if (Options.getCppUseArray()) {
      ccb.print("static const Array<");
      ccb.print(length + 1);
      ccb.println(", long long> jjCharData[] = {");
    } else {
      ccb.println("static const long long jjCharData[][" + length + 1 + "] = {");
    }

    for (int i = 0; i < nfa.size(); i++) {
      final TokenizerData.NfaState tmp = nfa.get(i);
      if (i > 0) {
        ccb.println(",");
      }
      if (tmp == null) {
        ccb.print("  {}");
        continue;
      }
      ccb.print("  {");
      final BitSet bits = new BitSet();
      for (final char c : tmp.characters) {
        bits.set(c);
      }
      final long[] longs = bits.toLongArray();
      ccb.print(lengths[i] + "LL");
      for (int k = 0; k < longs.length; k++) {
        int rep = 1;
        while (((k + rep) < longs.length) && (longs[k + rep] == longs[k])) {
          rep++;
        }
        ccb.print(", ", rep + "LL, ");
        if (longs[k] == Long.MIN_VALUE) {
          ccb.print("LLONG_MIN");
        } else {
          ccb.print("" + Long.toString(longs[k]) + "LL");
        }
        k += rep - 1;
      }
      ccb.print("}");
    }
    ccb.println();
    ccb.println("};");
    ccb.println();

    length = 0;
    for (int i = 0; i < nfa.size(); i++) {
      final TokenizerData.NfaState tmp = nfa.get(i);
      if (tmp == null) {
        continue;
      }
      length = Math.max(length, tmp.compositeStates.size());
    }
    if (Options.getCppUseArray()) {
      ccb.print("static const Array<");
      ccb.print(length);
      ccb.println(", int> jjcompositeState[] = {");
    } else {
      ccb.println("static const int jjcompositeState[][" + length + "] = {");
    }
    for (int i = 0; i < nfa.size(); i++) {
      final TokenizerData.NfaState tmp = nfa.get(i);
      if (i > 0) {
        ccb.println(", ");
      }
      if (tmp == null) {
        ccb.print("  {}");
        continue;
      }
      ccb.print("  {");
      int k = 0;
      for (final int st : tmp.compositeStates) {
        if (k++ > 0) {
          ccb.print(", ");
        }
        ccb.print(st);
      }
      ccb.print("}");
    }
    ccb.println();
    ccb.println("};");
    ccb.println();

    ccb.println("static const int jjmatchKinds[] = {");
    for (int i = 0; i < nfa.size(); i++) {
      final TokenizerData.NfaState tmp = nfa.get(i);
      if (i > 0) {
        ccb.println(", ");
      }
      // TODO(sreeni) : Fix this mess.
      if (tmp == null) {
        ccb.print("  ALLBITSUP");
        continue;
      }
      ccb.print("  " + tmp.kind);
    }
    ccb.println();
    ccb.println("};");
    ccb.println();

    length = 0;
    for (int i = 0; i < nfa.size(); i++) {
      final TokenizerData.NfaState tmp = nfa.get(i);
      if (tmp == null) {
        continue;
      }
      length = Math.max(length, tmp.nextStates.size());
    }
    if (Options.getCppUseArray()) {
      ccb.print("static const Array<");
      ccb.print(length + 1);
      ccb.println(", int> jjnextStateSet[] = {");
    } else {
      ccb.println("static const int jjnextStateSet[][" + (length + 1) + "] = {");
    }
    for (int i = 0; i < nfa.size(); i++) {
      final TokenizerData.NfaState tmp = nfa.get(i);
      if (i > 0) {
        ccb.println(", ");
      }
      if (tmp == null) {
        ccb.print("  {0}");
        continue;
      }
      ccb.print("  {");
      ccb.print(tmp.nextStates.size());
      for (final int s : tmp.nextStates) {
        ccb.print(", ");
        ccb.print(s);
      }
      ccb.print("}");
    }
    ccb.println();
    ccb.println("};");
    ccb.println();

    ccb.println("static const int jjInitStates[]  = {");
    int k = 0;
    for (final int i : tokenizerData.initialStates.keySet()) {
      if (k++ > 0) {
        ccb.print(", ");
      } else {
        ccb.print("  ");
      }
      ccb.print(tokenizerData.initialStates.get(i));
    }
    ccb.println("};");
    ccb.println();

    ccb.println("static const int canMatchAnyChar[] = {");
    k = 0;
    for (int i = 0; i < tokenizerData.wildcardKind.size(); i++) {
      if (k++ > 0) {
        ccb.print(", ");
      } else {
        ccb.print("  ");
      }
      ccb.print(tokenizerData.wildcardKind.get(i));
    }
    ccb.println("};");
    ccb.println();
  }

  private static void printWide(final CppCodeBuilder ccb, final String image) {
    if (image != null) {
      ccb.print("JJWIDE(");
      for (int j = 0; j < image.length(); j++) {
        if (image.charAt(j) <= 0xff) {
          ccb.print("\\" + Integer.toOctalString(image.charAt(j)));
        } else {
          String hexVal = Integer.toHexString(image.charAt(j));
          if (hexVal.length() == 3) {
            hexVal = "0" + hexVal;
          }
          ccb.print("\\u" + hexVal);
        }
      }
      ccb.print(")");
    } else {
      ccb.print("JJEMPTY");
    }
  }

  private static void dumpMatchInfo(final CppCodeBuilder ccb, final TokenizerData tokenizerData) {
    final Map<Integer, TokenizerData.MatchInfo> allMatches = tokenizerData.allMatches;

    // A bit ugly.

    final BitSet toSkip = new BitSet(allMatches.size());
    final BitSet toSpecial = new BitSet(allMatches.size());
    final BitSet toMore = new BitSet(allMatches.size());
    final BitSet toToken = new BitSet(allMatches.size());
    final int[] newStates = new int[allMatches.size()];
    toSkip.set(allMatches.size() + 1, true);
    toToken.set(allMatches.size() + 1, true);
    toMore.set(allMatches.size() + 1, true);
    toSpecial.set(allMatches.size() + 1, true);
    // Kind map.
    ccb.println("static const JJString jjstrLiteralImages[] = {");
    int k = 0;
    for (final int i : allMatches.keySet()) {
      final TokenizerData.MatchInfo matchInfo = allMatches.get(i);
      switch (matchInfo.matchType) {
        case SKIP:
          toSkip.set(i);
          break;
        case SPECIAL_TOKEN:
          toSpecial.set(i);
          break;
        case MORE:
          toMore.set(i);
          break;
        case TOKEN:
          toToken.set(i);
          break;
      }
      newStates[i] = matchInfo.newLexState;
      final String image = matchInfo.image;
      if (k++ > 0) {
        ccb.println(",");
      }
      ccb.print("  ");
      printWide(ccb, image);
    }
    ccb.println();
    ccb.println("};");
    ccb.println();

    // Now generate the bit masks.
    generateBitVector(ccb, "jjtoSkip", toSkip);
    ccb.println();
    generateBitVector(ccb, "jjtoSpecial", toSpecial);
    ccb.println();
    generateBitVector(ccb, "jjtoMore", toMore);
    ccb.println();
    generateBitVector(ccb, "jjtoToken", toToken);
    ccb.println();

    ccb.println("static const int jjnewLexState[] = {");
    for (int i = 0; i < newStates.length; i++) {
      if (i > 0) {
        ccb.print(", ");
      } else {
        ccb.print("  ");
      }
      // codeGenerator.genCode("0x" + Integer.toHexString(newStates[i]));
      ccb.print(Integer.toString(newStates[i]));
    }
    ccb.println();
    ccb.println("};");
    ccb.println();

    // Action functions.

    // Token actions.
    ccb.switchToMainFile();
    ccb.println(
        "void "
            + tokenizerData.parserName
            + "TokenManager::tokenLexicalActions(Token* matchedToken) {");
    dumpLexicalActions(ccb, allMatches, TokenizerData.MatchType.TOKEN, "matchedToken->kind()");
    ccb.println("}");
    ccb.println();

    ccb.println(
        "void "
            + tokenizerData.parserName
            + "TokenManager::skipLexicalActions(const Token* matchedToken) {");
    dumpLexicalActions(ccb, allMatches, TokenizerData.MatchType.SKIP, "jjmatchedKind");
    dumpLexicalActions(ccb, allMatches, TokenizerData.MatchType.SPECIAL_TOKEN, "jjmatchedKind");
    ccb.println("}");
    ccb.println();

    // More actions.
    ccb.println("void " + tokenizerData.parserName + "TokenManager::moreLexicalActions() {");
    ccb.println("jjimageLen += (lengthOfMatch = jjmatchedPos + 1);");
    dumpLexicalActions(ccb, allMatches, TokenizerData.MatchType.MORE, "jjmatchedKind");
    ccb.println("}");
    ccb.println();

    ccb.switchToStaticsFile();
    ccb.printLiteralArray("lexStateNames", tokenizerData.lexStateNames);
  }

  private static void dumpLexicalActions(
      final CppCodeBuilder ccb,
      final Map<Integer, TokenizerData.MatchInfo> allMatches,
      final TokenizerData.MatchType matchType,
      final String kindString) {
    switch (matchType) {
      case SKIP:
        break;
        // codeGenerator.println("  if (curLexState == DEFAULT || curLexState == " + {");
      case SPECIAL_TOKEN:
        break;
      default:
        break;
    }
    ccb.println("  switch(" + kindString + ") {");
    for (final int i : allMatches.keySet()) {
      final TokenizerData.MatchInfo matchInfo = allMatches.get(i);
      if ((matchInfo.action == null) || (matchInfo.matchType != matchType)) {
        continue;
      }
      ccb.println("    case " + i + ": {");
      ccb.println("      " + matchInfo.action.trim());
      ccb.println("      break;");
      ccb.println("    }");
    }
    ccb.println("    default: break;");
    ccb.println("  }");
  }

  private static void generateBitVector(
      final CppCodeBuilder ccb, final String name, final BitSet bits) {
    ccb.println("static const unsigned long long " + name + "[] = {");
    final long[] longs = bits.toLongArray();
    for (int i = 0; i < longs.length; i++) {
      if (i > 0) {
        ccb.print(", ");
      }
      ccb.print("  " + Long.toUnsignedString(longs[i]) + "ULL");
    }
    ccb.println();
    ccb.println("};");
  }
}
