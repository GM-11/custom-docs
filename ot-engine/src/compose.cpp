#include "../include/compose.h"

std::optional<Operation> composeTransformation(const Operation &op1,
                                               const Operation &op2) {
  if (op1.type == OperationType::INSERT && op2.type == OperationType::INSERT) {
    InsertData data1 = std::get<InsertData>(op1.data);
    InsertData data2 = std::get<InsertData>(op2.data);
    if (op2.position == op1.position + data1.text.size()) {
      data1.text.append(data2.text);
      return Operation(op1.type, op1.position, data1, op1.version,
                       op1.clientId);
    }
    return std::nullopt;
  }

  if (op1.type == OperationType::DELETE && op2.type == OperationType::DELETE) {
    DeleteData data1 = std::get<DeleteData>(op1.data);
    DeleteData data2 = std::get<DeleteData>(op2.data);
    if (op2.position == op1.position) {
      data1.length += data2.length;
      return Operation(op1.type, op1.position, data1, op1.version,
                       op1.clientId);
    }
    return std::nullopt;
  }

  if (op1.type == OperationType::INSERT && op2.type == OperationType::DELETE) {
    InsertData insertData = std::get<InsertData>(op1.data);
    DeleteData deleteData = std::get<DeleteData>(op2.data);
    if (op2.position == op1.position &&
        deleteData.length == insertData.text.size()) {
      return std::nullopt;
    }
    return std::nullopt;
  }

  return std::nullopt;
}
