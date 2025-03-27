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
#include <iostream>
#include <string>
#include <stdlib.h>
#include "IDLParser.h"

#include "JavaCC.h"
#include "IDLParserTokenManager.h"
#include "ParseException.h"
#include "StreamReader.h"
#include "DefaultCharStream.h"
using namespace std;
using namespace IDL;

static void usage(int argc, char **argv) {
  cerr << "IDL in out err" << endl;
}

int main(int argc, char **argv) {
  istream *input = &cin;
  ostream *output = &cout;
  ostream *error = &cerr;
  ifstream ifs;
  ofstream ofs;
  ofstream efs;
  StreamReader *sr = nullptr;
  CharStream *cs = nullptr;

  try {
    if (argc == 4) {
      ifs.open(argv[1]);
      ofs.open(argv[2]);
      efs.open(argv[3]);
      if (ifs.is_open() && ofs.is_open() && efs.is_open()) {
        input = &ifs;
        output = &ofs;
        error = &efs;
        sr = new StreamReader(ifs);
        cs = new DefaultCharStream(sr);
      } else {
        cerr << "cannot open in or out or err file" << endl;
        return 8;
      }
    } else {
      usage(argc, argv);
      return 0;
    }
    *output << "IDL Parser Version 0.1:  Reading from file " << argv[1] << " . . ." << endl;
    TokenManager *scanner = new IDLParserTokenManager(cs);
    IDLParser parser(scanner);
    parser.specification();
    *output << "IDL Parser Version 0.1:  IDL file parsed successfully." << endl;
  } catch (const ParseException &e) {
    clog << e.expectedTokenSequences << endl;
  } catch (...) {

  }
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
