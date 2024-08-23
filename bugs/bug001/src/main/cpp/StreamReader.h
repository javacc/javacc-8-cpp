#ifndef STREAM_READER_H_
#define STREAM_READER_H_

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

#endif
