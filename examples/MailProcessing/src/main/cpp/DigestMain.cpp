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

#include "DigestMain.h" // not included in Digest.h
#include "Digest.h"
#include "DigestTokenManager.h"

#define MYPARSER Digest
#define MYTM DigestTokenManager
#define MYENTRY MailFile

using namespace std;

void DigestMain::main(int argc, char *argv[]) {
  if (argc != 3) {
    cerr << "Error: bad number of arguments (" << argc - 1 << " instead of 2)" << endl;
    cerr << "Usage: Digest infile outfile" << endl;
    exit(4);
  }
  DigestMain dm;
  dm.doMain(argc, argv);
}

void DigestMain::doMain(int argc, char *argv[]) {

  ifstream ifs;
  ofstream digpw;

  StreamReader *sr = nullptr;
  CharStream *cs = nullptr;

  try {
    // open files
    switch (argc) {
      case 3:
        digpw.open(argv[2], ofstream::binary);
      case 2:
        ifs.open(argv[1], ifstream::binary);
    }
    if (ifs.is_open()) {
      sr = new StreamReader(ifs);
      cs = new CHARSTREAM(sr);
    } else {
      cerr << "Cannot open input file " << argv[1] << endl;
      exit(8);
    }
    if (digpw.is_open()) {
    } else {
      cerr << "Cannot open output file " << argv[2] << endl;
      exit(8);
    }

    // parse
    MYPARSER parser(new MYTM(cs));
    parser.setDigpw(&digpw);
    digpw << "DIGEST OF RECENT MESSAGES FROM THE JAVACC MAILING LIST" << endl;
    digpw << "----------------------------------------------------------------------" << endl;
    digpw << endl;
    digpw << "MESSAGE SUMMARY:" << endl;
    digpw << endl;
    digpw.flush();
    JJString buffer = parser.MYENTRY();
    if (buffer.empty()) {
      digpw << "There have been no messages since the last digest posting." << endl;
      digpw << endl;
      digpw << "----------------------------------------------------------------------" << endl;
    } else {
      digpw << endl;
      digpw << "----------------------------------------------------------------------" << endl;
      digpw << endl;
      digpw << buffer << endl;
    }
    digpw.flush();
  } catch (const ParseException &e) {
    cerr << "Error parsing input file:" << endl;
    clog << e.expectedTokenSequences << endl;
  } catch (...) {
  }

// close files & others
  if (ifs.is_open())
    ifs.close();
  if (digpw.is_open())
    digpw.close();
  if (cs)
    delete cs;
  if (sr)
    delete sr;
}

/*
 package dig;

 import java.io.*;

 public class DigestMain {

 PrintWriter digpw;

 public static void main(String[] args) throws ParseException, IOException, FileNotFoundException {
 if (args.length < 1) {
 System.err.println("Error: bad number of arguments (" + args.length + " instead of 2)");
 System.err.println("Usage: DigestMain infile outfile");
 System.exit(4);
 }
 DigestMain dm = new DigestMain();
 dm.doMain(args);
 }

 void doMain(String[] args) throws ParseException, IOException, FileNotFoundException {
 digpw = new PrintWriter(new FileWriter(args[1]));
 Digest parser = new Digest(new FileInputStream(args[0]));
 parser.setDigpw(digpw);
 digpw.println("DIGEST OF RECENT MESSAGES FROM THE JAVACC MAILING LIST");
 digpw.println("----------------------------------------------------------------------");
 digpw.println("");
 digpw.println("MESSAGE SUMMARY:");
 digpw.println("");
 String buffer = parser.MailFile();
 if (buffer.length() == 0) {
 digpw.println("There have been no messages since the last digest posting.");
 digpw.println("");
 digpw.println("----------------------------------------------------------------------");
 } else {
 digpw.println("");
 digpw.println("----------------------------------------------------------------------");
 digpw.println("");
 digpw.println(buffer);
 }
 digpw.close();
 }
 }
 */
