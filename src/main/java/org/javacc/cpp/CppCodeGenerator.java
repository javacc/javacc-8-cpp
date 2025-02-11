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
import org.javacc.jjtree.DefaultJJTreeVisitor;
import org.javacc.jjtree.JJTreeContext;
import org.javacc.parser.CodeGenerator;
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.Context;
import org.javacc.parser.JavaCCGlobals;
import org.javacc.parser.Options;
import org.javacc.parser.TokenizerData;

public class CppCodeGenerator implements CodeGenerator {

  static final boolean IS_DEBUG = true;

  /** The name of the C# code generator. */
  @Override
  public final String getName() {
    return "C++";
  }

  /** Generate any other support files you need. */
  @Override
  public final boolean generateHelpers(
      final Context context,
      final CodeGeneratorSettings settings,
      final TokenizerData tokenizerData) {
    try {
      try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, settings)) {
        ccb.setFile(new File((String) settings.get("OUTPUT_DIRECTORY"), "CharStream.h"));
        ccb.addTools(JavaCCGlobals.toolName);

        ccb.switchToIncludeFile();
        ccb.printTemplate("/templates/cpp/CharStream.h.template");
      }

      try (CppCodeBuilder ccb = CppCodeBuilder.of(context, settings)) {
        ccb.setFile(new File((String) settings.get("OUTPUT_DIRECTORY"), "DefaultCharStream.cc"));
        ccb.addTools(JavaCCGlobals.toolName);
        ccb.addOption(
            Options.UO__STATIC, Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);

        ccb.printTemplate("/templates/cpp/DefaultCharStream.cc.template");
        ccb.switchToIncludeFile();
        ccb.printTemplate("/templates/cpp/DefaultCharStream.h.template");
      }

      try (CppCodeBuilder ccb = CppCodeBuilder.of(context, settings)) {
        ccb.setFile(new File((String) settings.get("OUTPUT_DIRECTORY"), "TokenManagerError.cc"));
        ccb.addTools(JavaCCGlobals.toolName);
        ccb.addOption(
            Options.UO__STATIC, Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);

        ccb.printTemplate("/templates/cpp/TokenManagerError.cc.template");
        ccb.switchToIncludeFile();
        ccb.printTemplate("/templates/cpp/TokenManagerError.h.template");
      }

      try (CppCodeBuilder ccb = CppCodeBuilder.of(context, settings)) {
        ccb.setFile(new File((String) settings.get("OUTPUT_DIRECTORY"), "ParseException.cc"));
        ccb.addTools(JavaCCGlobals.toolName);
        ccb.addOption(
            Options.UO__STATIC, Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);

        ccb.printTemplate("/templates/cpp/ParseException.cc.template");
        ccb.switchToIncludeFile();
        ccb.printTemplate("/templates/cpp/ParseException.h.template");
      }

      try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, settings)) {
        ccb.setFile(new File((String) settings.get("OUTPUT_DIRECTORY"), "TokenManager.h"));
        ccb.addTools(JavaCCGlobals.toolName);
        ccb.addOption(
            Options.UO__STATIC, Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);

        ccb.printTemplate("/templates/cpp/TokenManager.h.template");
      }

      try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, settings)) {
        ccb.setFile(new File((String) settings.get("OUTPUT_DIRECTORY"), "JavaCC.h"));
        ccb.addTools(JavaCCGlobals.toolName);
        ccb.addOption(
            Options.UO__STATIC, Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);

        ccb.printTemplate("/templates/cpp/JavaCC.h.template");
      }

      if (!Options.getLibrary().isEmpty()) {
        try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, settings)) {
          ccb.setFile(new File((String) settings.get("OUTPUT_DIRECTORY"), "ImportExport.h"));
          ccb.addTools(JavaCCGlobals.toolName);
          ccb.addOption(Options.UO__LIBRARY);
          ccb.printTemplate("/templates/cpp/ImportExport.h.template");
        }
      }

      try (CppCodeBuilder ccb = CppCodeBuilder.of(context, settings)) {
        ccb.setFile(
            new File((String) settings.get("OUTPUT_DIRECTORY"), "DefaultParserErrorHandler.cc"));
        ccb.addTools(JavaCCGlobals.toolName);
        ccb.addOption(
            Options.UO__STATIC, Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);
        ccb.addOption(
            Options.UO__STATIC,
            Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC,
            Options.UO__BUILD_PARSER,
            Options.UO__BUILD_TOKEN_MANAGER);

        ccb.printTemplate("/templates/cpp/DefaultParserErrorHandler.cc.template");
        ccb.switchToIncludeFile();
        ccb.printTemplate("/templates/cpp/DefaultParserErrorHandler.h.template");
      }

      try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, settings)) {
        ccb.setFile(new File((String) settings.get("OUTPUT_DIRECTORY"), "ParserErrorHandler.h"));
        ccb.addTools(JavaCCGlobals.toolName);
        ccb.addOption(
            Options.UO__STATIC,
            Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC,
            Options.UO__BUILD_PARSER,
            Options.UO__BUILD_TOKEN_MANAGER);

        ccb.printTemplate("/templates/cpp/ParserErrorHandler.h.template");
      }

      try (CppCodeBuilder ccb = CppCodeBuilder.of(context, settings)) {
        ccb.setFile(
            new File(
                (String) settings.get("OUTPUT_DIRECTORY"), "DefaultTokenManagerErrorHandler.cc"));
        ccb.addTools(JavaCCGlobals.toolName);
        ccb.addOption(
            Options.UO__STATIC, Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC);
        ccb.addOption(
            Options.UO__STATIC,
            Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC,
            Options.UO__BUILD_PARSER,
            Options.UO__BUILD_TOKEN_MANAGER);

        ccb.printTemplate("/templates/cpp/DefaultTokenManagerErrorHandler.cc.template");
        ccb.switchToIncludeFile();
        ccb.printTemplate("/templates/cpp/DefaultTokenManagerErrorHandler.h.template");
      }

      try (CppCodeBuilder ccb = CppCodeBuilder.ofHeader(context, settings)) {
        ccb.setFile(
            new File((String) settings.get("OUTPUT_DIRECTORY"), "TokenManagerErrorHandler.h"));
        ccb.addTools(JavaCCGlobals.toolName);
        ccb.addOption(
            Options.UO__STATIC,
            Options.UO__SUPPORT_CLASS_VISIBILITY_PUBLIC,
            Options.UO__BUILD_PARSER,
            Options.UO__BUILD_TOKEN_MANAGER);

        ccb.printTemplate("/templates/cpp/TokenManagerErrorHandler.h.template");
      }

      OtherFilesGenCPP.start(context, tokenizerData);
    } catch (final Exception e) {
      e.printStackTrace();
      return false;
    }

    return true;
  }

  /** The Token class generator. */
  @Override
  public final TokenCodeGenerator getTokenCodeGenerator(final Context context) {
    return new TokenCodeGenerator(context);
  }

  /** The TokenManager class generator. */
  @Override
  public final TokenManagerCodeGenerator getTokenManagerCodeGenerator(final Context context) {
    return new TokenManagerCodeGenerator(context);
  }

  /** The Parser class generator. */
  @Override
  public final ParserCodeGenerator getParserCodeGenerator(final Context context) {
    return new ParserCodeGenerator(context);
  }

  /**
   * TODO(sreeni): Fix this when we do tree annotations in the parser code generator. The JJTree
   * preprocesor.
   */
  @Override
  public final DefaultJJTreeVisitor getJJTreeCodeGenerator(final JJTreeContext context) {
    return new JJTreeCodeGenerator(context);
  }
}
