#include <iostream>
#include <fstream>
#include <iomanip>
#include <string>

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

  if (argc != 4) {
    cerr << "Error: invalid number of arguments (" << (argc - 1) << ")" << endl;
    cerr << "Usage: MYPARSER [ inputfile [ outputfile [ errorfile ] ] ]" << endl;
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
      cerr << "Cannot open input file" << endl;
      return 8;
    }
    if (ofs.is_open()) {
      coutbuf = cout.rdbuf();
      cout.rdbuf(ofs.rdbuf());
    } else {
      cerr << "Cannot open output file" << endl;
      return 8;
    }
    if (efs.is_open()) {
      cerrbuf = cerr.rdbuf();
      cerr.rdbuf(efs.rdbuf());
    }

    // parse
    MYPARSER parser(new MYTM(cs));
//    MYTM *scanner = new MYTM(cs);
//    scanner->disable_tracing();
//    MYPARSER parser(scanner);
    parser.MYENTRY();
    cerr << "Input file parsed successfully" << endl;
  } catch (const ParseException &e) {
    cerr << "Error parsing input file:" << endl;
    clog << e.expectedTokenSequences << endl;
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
