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
 *     * Neither the name of the Sun Microsystems, Inc. nor the names of its
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
#include <fstream>
#include <stdexcept>
#include <iomanip>

#include "JavaCC.h"
#include "ParseException.h"
#include "StreamReader.h"
#if !defined(JJ8) && !defined(JJ7)
#define JJ8
#endif
#if defined(JJ8)
#include "DefaultCharStream.h"
#define CHARSTREAM DefaultCharStream
#elif defined(JJ7)
#include "CharStream.h"
#define CHARSTREAM CharStream
#endif

#include "Faq.h" // includes FaqMain.h
#include "FaqTokenManager.h"

#define MYPARSER Faq
#define MYTM FaqTokenManager
#define MYENTRY MailFile

using namespace std;

JJString FaqMain::fix(JJString s) {
  JJString retval = "";
  for (int i = 0; i < s.length(); i++) {
    char c = s[i];
    if (c == '<') {
      retval += "&lt;";
    } else if (c == '>') {
      retval += "&gt;";
    } else {
      retval += c;
    }
  }
  return retval;
}

void FaqMain::main(int argc, char *argv[]) {
  if (argc != 4) {
    cerr << "Error: bad number of arguments (" << argc - 1 << " instead of 3)" << endl;
    cerr << "Usage: Faq index infile outdir" << endl;
    exit(4);
  }

  ifstream ifs;
#define ofs indexpw
  ofstream ofs;

  StreamReader *sr = nullptr;
  CharStream *cs = nullptr;

  try {
    // open files
    switch (argc) {
      case 4: {
        JJString dir(argv[3]);
        indexpw.open(dir + "/index.out.html", ofstream::binary);
      }
      case 3:
        ifs.open(argv[2], ifstream::binary);
      case 2:
        beginAt = stoi(argv[1]);
    }
    if (ifs.is_open()) {
      sr = new StreamReader(ifs);
      cs = new CHARSTREAM(sr);
    } else {
      cerr << "Cannot open input file " << argv[2] << endl;
      exit(8);
    }
    if (indexpw.is_open()) {
    } else {
      cerr << "Cannot open output file " << argv[3] << "/index.out.html" << endl;
      exit(8);
    }

    // parse
    indexpw << "<title>Selected list of emails from the JavaCC mailing list</title>" << endl;
    indexpw << "<h2>Selected list of emails from the JavaCC mailing list</h2>" << endl;
    MYPARSER parser(new MYTM(cs));
    parser.count = 0;
    parser.beginAt = beginAt;
    parser.outdir = outdir;
    parser.indexpw = &indexpw;
    parser.MYENTRY();
  } catch (const ParseException &e) {
    cerr << "Error parsing input file:" << endl;
    clog << e.expectedTokenSequences << endl;
  } catch (...) {
  }

  // close files & others
  if (ifs.is_open())
    ifs.close();
  if (indexpw.is_open())
    indexpw.close();
  if (cs)
    delete cs;
  if (sr)
    delete sr;
}

/*
 package faq;

 import java.io.*;

 public class FaqMain {

 static int count = 0;

 static int beginAt = 1;

 static String outdir;

 static PrintWriter indexpw;

 static String fix(String s) {
 String retval = "";
 for (int i = 0; i < s.length(); i++) {
 char c = s.charAt(i);
 if (c == '<') {
 retval += "&lt;";
 } else if (c == '>') {
 retval += "&gt;";
 } else {
 retval += c;
 }
 }
 return retval;
 }

 public static void main(String args[]) throws ParseException, IOException, FileNotFoundException {
 if (args.length < 2) {
 System.err.println("Error: bad number of arguments (" + args.length + " instead of 3)");
 System.err.println("Usage: FaqMain index infile outdir");
 System.exit(4);
 }
 beginAt = Integer.parseInt(args[0]);
 outdir = args[2];
 indexpw = new PrintWriter(new FileWriter(outdir + "/index.out.html"));
 indexpw.println("<title>Selected list of emails from the JavaCC mailing list</title>");
 indexpw.println("<h2>Selected list of emails from the JavaCC mailing list</h2>");
 Faq parser = new Faq(new FileInputStream(args[1]));
 parser.MailFile();
 }
 }
 */
