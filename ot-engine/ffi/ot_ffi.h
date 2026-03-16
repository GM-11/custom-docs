#pragma once

#ifdef __cplusplus
extern "C" {
#endif

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

#ifdef __cplusplus
}
#endif
