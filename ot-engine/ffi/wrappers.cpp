#include "wrappers.h"
#include "../include/apply.h"
#include "../include/pipeline.h"
#include "../include/transform.h"
#include <cstdlib> // free, malloc
#include <cstring> // strdup
#include <string>
#include <variant>
#include <vector>

OperationC convertOperationToOperationC(const Operation op) {
  OperationC oc{};
  oc.type = static_cast<int>(op.type);
  oc.position = op.position;
  oc.version = op.version;
  oc.clientId = nullptr;
  // initialize union to a safe default
  oc.data.text = nullptr;

  // copy clientId
  if (!op.clientId.empty()) {
    oc.clientId = strdup(op.clientId.c_str());
  } else {
    oc.clientId = strdup("");
  }

  // convert data based on operation type
  if (op.type == INSERT) {
    if (std::holds_alternative<InsertData>(op.data)) {
      const InsertData &ins = std::get<InsertData>(op.data);
      oc.data.text = strdup(ins.text.c_str());
    } else {
      // defensive: empty string if variant doesn't hold InsertData
      oc.data.text = strdup("");
    }
  } else { // DELETE
    if (std::holds_alternative<DeleteData>(op.data)) {
      const DeleteData &del = std::get<DeleteData>(op.data);
      oc.data.length = del.length;
    } else {
      oc.data.length = 0;
    }
  }

  return oc;
}

Operation convertOperationCToOperation(const OperationC opC) {
  OperationType type = static_cast<OperationType>(opC.type);
  std::string clientIdStr;
  if (opC.clientId != nullptr) {
    clientIdStr = std::string(opC.clientId);
  } else {
    clientIdStr = std::string();
  }

  if (type == INSERT) {
    InsertData ins;
    ins.text = opC.data.text ? std::string(opC.data.text) : std::string();
    std::variant<InsertData, DeleteData> var = ins;
    return Operation(type, opC.position, var, opC.version, clientIdStr);
  } else { // DELETE
    DeleteData del;
    del.length = opC.data.length;
    std::variant<InsertData, DeleteData> var = del;
    return Operation(type, opC.position, var, opC.version, clientIdStr);
  }
}

OperationC *performTransformation(const OperationC *op1, const OperationC *op2,
                                  int *outsize) {
  if (op1 == nullptr || op2 == nullptr || outsize == nullptr) {
    return nullptr;
  }

  Operation operation1 = convertOperationCToOperation(*op1);
  Operation operation2 = convertOperationCToOperation(*op2);

  std::optional<std::vector<Operation>> result =
      performTrasnsformation(operation1, operation2);

  if (!result.has_value()) {
    *outsize = 0;
    return nullptr;
  }

  const std::vector<Operation> &operations = result.value();
  *outsize = operations.size();

  OperationC *resultArray =
      static_cast<OperationC *>(malloc(operations.size() * sizeof(OperationC)));

  if (resultArray == nullptr) {
    *outsize = 0;
    return nullptr;
  }

  for (size_t i = 0; i < operations.size(); ++i) {
    resultArray[i] = convertOperationToOperationC(operations[i]);
  }

  return resultArray;
}

char *applyTransformations(char *currentDocument, OperationC *latestOperation) {
  if (currentDocument == nullptr || latestOperation == nullptr) {
    return nullptr;
  }

  std::string document(currentDocument);
  Operation operation = convertOperationCToOperation(*latestOperation);

  std::string result = applyTransformations(document, operation);

  char *resultStr = strdup(result.c_str());
  return resultStr;
}

OperationC *transformPipeLine(const OperationC *operations,
                              const OperationC *incomingOperation, int *outsize,
                              int *operationsSize) {
  if (operations == nullptr || incomingOperation == nullptr ||
      outsize == nullptr || operationsSize == nullptr) {
    return nullptr;
  }

  std::vector<Operation> operationsVec;
  for (int i = 0; i < *operationsSize; ++i) {
    operationsVec.push_back(convertOperationCToOperation(operations[i]));
  }

  Operation incomingOp = convertOperationCToOperation(*incomingOperation);

  std::vector<Operation> result = transformPipeline(operationsVec, incomingOp);

  *outsize = result.size();

  OperationC *resultArray =
      static_cast<OperationC *>(malloc(result.size() * sizeof(OperationC)));

  if (resultArray == nullptr) {
    *outsize = 0;
    return nullptr;
  }

  for (size_t i = 0; i < result.size(); ++i) {
    resultArray[i] = convertOperationToOperationC(result[i]);
  }

  return resultArray;
}

void freeOperations(OperationC *ops, int size) {
  if (ops == nullptr) {
    return;
  }

  for (int i = 0; i < size; ++i) {
    if (ops[i].clientId != nullptr) {
      free(ops[i].clientId);
    }

    if (ops[i].type == INSERT) {
      if (ops[i].data.text != nullptr) {
        free(ops[i].data.text);
      }
    }
  }

  free(ops);
}

void freeDocument(char *doc) {
  if (doc != nullptr) {
    free(doc);
  }
}
