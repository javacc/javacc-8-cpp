/* Generated By:JJTree: Do not edit this line. ASTWriteStatement.cc Version 8.0 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=false,TRACK_TOKENS=true,NODE_PREFIX=AST,NODE_EXTENDS=MyNode,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
#include "ASTWriteStatement.h"

  
  ASTWriteStatement::ASTWriteStatement(int id) : Node(id) {
  }
  ASTWriteStatement::~ASTWriteStatement() {
  }
  void ASTWriteStatement::interpret()
  {
#ifdef FIXME
     if (symtab.get(name) == null)
        System.err.println("Undefined variable : " + name);
    else
      try {
        out.write("Value of " + name + " : " + symtab.get(name));
        out.flush();
      } catch (IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
#endif
  }

/* JavaCC - OriginalChecksum=4f6c8ef87636878290537b2084a44958 (do not edit this line) */
