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
import org.javacc.parser.CodeGeneratorSettings;
import org.javacc.parser.Context;
import org.javacc.parser.Options;
import org.javacc.utils.CodeBuilder;

/** The {@link CppCodeBuilder} class. */
class CppCodeBuilder extends CodeBuilder<CppCodeBuilder> {

  private enum Buffer {
    Main,
    Include,
    Static;
  }

  private final boolean headeOnly;
  private final StringBuffer mainBuffer = new StringBuffer();
  private final StringBuffer includeBuffer = new StringBuffer();
  private final StringBuffer staticsBuffer = new StringBuffer();

  private Buffer kind;

  /**
   * Constructs an instance of {@link CodeBuilder}.
   *
   * @param options
   */
  private CppCodeBuilder(
      final Context context, final CodeGeneratorSettings options, final boolean headeOnly) {
    super(context, options);
    this.headeOnly = headeOnly;
    kind = headeOnly ? Buffer.Include : Buffer.Main;
  }

  /** Get the {@link StringBuffer} */
  @Override
  protected final StringBuffer getBuffer() {
    switch (kind) {
      case Include:
        return includeBuffer;
      case Static:
        return staticsBuffer;
      default:
    }
    return mainBuffer;
  }

  /**
   * Generate a class with a given name, an array of superclass and another array of super interfaes
   */
  void genClassStart(
      final String mod,
      final String name,
      final String[] superClasses,
      final String[] superInterfaces) {
    print("class ");
    if (!Options.getLibrary().isEmpty()) {
      print(name.toUpperCase() + "_API ");
    }
    print(name);
    if ((superClasses.length > 0) || (superInterfaces.length > 0)) {
      print(" : ");
    }

    genCommaSeperatedString(superClasses);
    genCommaSeperatedString(superInterfaces);
    println(" {");
    println();
    println("public:");
  }

  @Override
  protected final void build() {
    final String includeFileName = getFile().getName().replace(".cc", ".h");
    final File includeFile = new File(getFile().getParentFile(), includeFileName);

    fixupLongLiterals(includeBuffer);
    store(includeFile, includeBuffer);

    if (headeOnly) {
      return;
    }

    mainBuffer.insert(0, staticsBuffer);

    mainBuffer.insert(0, "#include \"" + includeFileName + "\"\n");

    fixupLongLiterals(mainBuffer);
    store(getFile(), mainBuffer);
  }

  void generateMethodDefHeader(
      final String modsAndRetType, final String className, final String nameAndParams) {
    generateMethodDefHeader(modsAndRetType, className, nameAndParams, null);
  }

  void generateMethodDefHeader(
      String qualifiedModsAndRetType,
      final String className,
      final String nameAndParams,
      final String exceptions) {
    // for C++, we generate the signature in the header file and body in main file
    includeBuffer.append("  ");
    if (qualifiedModsAndRetType != null && qualifiedModsAndRetType.length() > 0) {
      includeBuffer.append(qualifiedModsAndRetType).append(' ');
    }
    includeBuffer.append(nameAndParams);
    // if (exceptions != null)
    // includeBuffer.append(" throw(" + exceptions + ")");
    includeBuffer.append(";\n\n");

    String modsAndRetType = null;
    int i = qualifiedModsAndRetType.lastIndexOf(':');
    if (i >= 0) {
      modsAndRetType = qualifiedModsAndRetType.substring(i + 1);
    }

    if (modsAndRetType != null) {
      i = modsAndRetType.lastIndexOf("virtual");
      if (i >= 0) {
        modsAndRetType = modsAndRetType.substring(i + "virtual".length());
      }
    }
    if (qualifiedModsAndRetType != null) {
      i = qualifiedModsAndRetType.lastIndexOf("virtual");
      if (i >= 0) {
        qualifiedModsAndRetType = qualifiedModsAndRetType.substring(i + "virtual".length());
      }
    }
    final String qualifierClass = (className == null) ? "" : className + "::";
    mainBuffer.append((qualifiedModsAndRetType + " " + qualifierClass + nameAndParams).trim());
    // if (exceptions != null)
    // mainBuffer.append(" throw( " + exceptions + ")");
    switchToMainFile();
  }

  // HACK
  private void fixupLongLiterals(final StringBuffer sb) {
    for (int i = 0; i < (sb.length() - 1); i++) {
      // int beg = i;
      final char c1 = sb.charAt(i);
      final char c2 = sb.charAt(i + 1);
      if (Character.isDigit(c1) || ((c1 == '0') && (c2 == 'x'))) {
        i += c1 == '0' ? 2 : 1;
        while (CppCodeBuilder.isHexDigit(sb.charAt(i))) {
          i++;
        }
        // Avoid replacing long long (LL) with unsigned long long (ULL)
        if ((sb.charAt(i) == 'L') && (sb.charAt(i + 1) != 'L') && (sb.charAt(i - 1) != 'L')) {
          // if (sb.charAt(i) == 'L' && (i >= sb.length() || sb.charAt(i + 1) !=
          // 'L')) {
          sb.insert(i, "UL");
        }
        i++;
      }
    }
  }

  /**
   * Return <code>true</code> if the char is a hex digit.
   *
   * @param c
   */
  private static boolean isHexDigit(final char c) {
    return ((c >= '0') && (c <= '9')) || ((c >= 'a') && (c <= 'f')) || ((c >= 'A') && (c <= 'F'));
  }

  private final void genCommaSeperatedString(final String[] strings) {
    for (int i = 0; i < strings.length; i++) {
      if (i > 0) {
        print(", ");
      }
      print(strings[i]);
    }
  }

  // Used by the CPP code generatror
  final CppCodeBuilder printCharArray(final String s) {
    print("{");
    for (final char c : s.toCharArray()) {
      print("0x" + Integer.toHexString(c) + ", ");
    }
    print("0}");
    return this;
  }

  public void printLiteralArray(final String varName, final String[] arr) {
    // First generate char array vars
    for (int i = 0; i < arr.length; i++) {
      println("static const JJChar " + varName + "_arr_" + i + "[] = ");
      print("  ");
      printCharArray(arr[i]);
      println();
      println(";");
      println();
    }

    println("static const JJString " + varName + "[] = {");
    for (int i = 0; i < arr.length; i++) {
      print("  " + varName + "_arr_" + i);
      if ((i + 1) < arr.length) {
        print(", ");
      }
      println();
    }
    println("};");
    println();
  }

  @Override
  public final String escapeToUnicode(final String text) {
    return text;
  }

  void switchToMainFile() {
    kind = Buffer.Main;
  }

  void switchToIncludeFile() {
    kind = Buffer.Include;
  }

  void switchToStaticsFile() {
    kind = Buffer.Static;
  }

  /**
   * Constructs an instance of {@link CppCodeBuilder}.
   *
   * @param options
   */
  static CppCodeBuilder of(final Context context, final CodeGeneratorSettings options) {
    return new CppCodeBuilder(context, options, false);
  }

  /**
   * Constructs an instance of {@link CppCodeBuilder}.
   *
   * @param options
   */
  static CppCodeBuilder ofHeader(final Context context, final CodeGeneratorSettings options) {
    return new CppCodeBuilder(context, options, true);
  }
}
