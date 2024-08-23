#include <iostream>
#include "JavaCC.h"

using namespace std;

class StreamReader: public ReaderStream {

public:
  StreamReader(istream &is);
  virtual ~StreamReader();

  virtual size_t read(JJChar *buffer, int offset, size_t len);
  virtual bool endOfInput();

private:
  istream &is;

};

StreamReader::StreamReader(istream &is) :
    is(is) {
}

StreamReader::~StreamReader() {
}

size_t StreamReader::read(JJChar *buffer, int offset, size_t len) {
  is.read(buffer + offset, len);
  return is.gcount();
}

bool StreamReader::endOfInput() {
  return is.eof();
}
