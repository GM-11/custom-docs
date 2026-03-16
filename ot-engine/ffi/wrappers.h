#include "../include/operation.h"
extern "C" {

struct OperationC {
  int type;
  int position;
  int version;
  char *clientId;
  union {
    char *text;
    int length;
  } data;
};

OperationC *performTransformation(const OperationC *op1, const OperationC *op2,
                                  int *outsize);
char *applyTransformations(char *currentDocument, OperationC *latestOperation);
OperationC *transformPipeLine(const OperationC *operations,
                              const OperationC *incomingOperation, int *outsize,
                              int *operationsSize);

void freeOperations(OperationC *ops, int size);
void freeDocument(char *doc);
}

OperationC convertOperationToOperationC(const Operation op);
Operation convertOperationCToOperation(const OperationC op);
