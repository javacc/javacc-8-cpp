/* Generated By:JJTree: Do not edit this line. ASTIntConstNode.h Version 7.0 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
#pragma once

#include "Node.h"

class SPLParser;

class ASTIntConstNode : public Node {
public: 
           ASTIntConstNode(int id);
           ASTIntConstNode(SPLParser *parser, int id);
  virtual ~ASTIntConstNode();

  /** Accept the visitor. **/
  virtual void jjtAccept(SPLParserVisitor *visitor, void* data) const ;

  friend class Interpret;
  friend class SPLParser;

private:
  int val;
};
