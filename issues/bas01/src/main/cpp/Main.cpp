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
#include <iostream>
#include <fstream>
#include <iomanip>

#include "JavaCC.h"
#include "ComplexLineCommentTokenManager.h"
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
#include "ComplexLineComment.h"

using namespace std;

#define MYPARSER ComplexLineComment
#define MYTM ComplexLineCommentTokenManager
#define MYENTRY Input

int main(int argc, char **argv) {

  if (argc > 4) {
    cerr << "Error: invalid number of arguments (" << (argc - 1) << ")" << endl;
    cerr << "Usage: ComplexLineComment [ inputfile [ outputfile [ errorfile ] ] ]" << endl;
    return 4;
  }

  // see https://stackoverflow.com/questions/10150468/how-to-redirect-cin-and-cout-to-files
  ifstream ifs;
  ofstream ofs;
  ofstream efs;
  streambuf *cinbuf;
  streambuf *coutbuf;
  streambuf *cerrbuf;

  StreamReader *sr = nullptr;
  CharStream *cs = nullptr;

  try {
    // open files and redirect standard streams to them
    switch (argc) {
      case 4:
        efs.open(argv[3]);
      case 3:
        ofs.open(argv[2]);
      case 2:
        ifs.open(argv[1], ifstream::binary);
    }
    if (ifs.is_open()) {
      sr = new StreamReader(ifs);
      cs = new CHARSTREAM(sr);
      cinbuf = cin.rdbuf();
      cin.rdbuf(ifs.rdbuf());
    } else {
      cerr << "Cannot open input file " << argv[1] << endl;
      return 8;
    }
    if (ofs.is_open()) {
      coutbuf = cout.rdbuf();
      cout.rdbuf(ofs.rdbuf());
    } else {
      cerr << "Cannot open output file " << argv[2] << endl;
      return 8;
    }
    if (efs.is_open()) {
      cerrbuf = cerr.rdbuf();
      cerr.rdbuf(efs.rdbuf());
    } else {
      cerr << "Cannot open error file " << argv[3] << endl;
      return 8;
    }

    // parse
    MYPARSER parser(new MYTM(cs));
//    MYTM *scanner = new MYTM(cs);
//    scanner->disable_tracing();
//    MYPARSER parser(scanner);
    parser.MYENTRY();
    cerr << "Input file parsed successfully" << endl;
  } catch (const ParseException &e) {
    cerr << "ParseException parsing input file:" << endl;
    cerr << e.expectedTokenSequences << endl;
  } catch (...) {
  }

// restore standard streams
  cin.rdbuf(cinbuf);
  cout.rdbuf(coutbuf);
  cerr.rdbuf(cerrbuf);
// close files & others
  if (ifs.is_open())
    ifs.close();
  if (ofs.is_open())
    ofs.close();
  if (efs.is_open())
    efs.close();
  if (cs)
    delete cs;
  if (sr)
    delete sr;

  return 0;
}
