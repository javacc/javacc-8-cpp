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
/*
 * Not found any way to have the maven nar plugin produce more than one executable from a single
 *  sources structure,
 * so a main() cannot be in both classes DigestMain and FaqMain, so we put it outside
 *  and made it direct to the right Main.
 */
#include "JavaCC.h"
#include "DigestMain.h"
#include "FaqMain.h"

void usage() {
  cerr << "Usage: main (infile outfile Digest | index infile outdir Faq)" << endl;
  exit(4);
}

int main(int argc, char *argv[]) {

//  cerr << "argc = " << argc << ", argv[argc-1] = " << argv[argc - 1] << endl;

  if (argc < 3) {
    cerr << "Error: bad number of arguments (" << argc << ")" << endl;
    usage();
  }
  JJString pg(argv[argc - 1]);
  if (pg == "Digest") {
    DigestMain::main(argc - 1, argv);
  } else if (pg == "Faq") {
    FaqMain faqmain;
    faqmain.main(argc - 1, argv);
  } else {
    cerr << "Error: bad program name (" << pg << ")" << endl;
    usage();
  }
  return 0;
}
