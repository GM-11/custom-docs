#include "../include/transform.h"
#include <optional>
#include <vector>

std::optional<std::vector<Operation>>
performTrasnsformation(const Operation &op1, const Operation &op2) {

  if (op1.type == OperationType::INSERT && op2.type == OperationType::INSERT) {

    InsertData insertData = std::get<InsertData>(op1.data);
    if (op2.position >= op1.position) {
      return std::vector<Operation>{Operation(
          OperationType::INSERT, op2.position + insertData.text.size(),
          op2.data, op2.version, op2.clientId)};
    } else {
      return std::vector<Operation>{op2};
    }
  } else if (op1.type == OperationType::DELETE &&
             op2.type == OperationType::DELETE) {
    DeleteData deleteData = std::get<DeleteData>(op1.data);
    DeleteData deleteDataOp2 = std::get<DeleteData>(op2.data);
    if (op2.position > op1.position + deleteData.length) {

      return std::vector<Operation>{
          Operation(op2.type, op2.position - deleteData.length, op2.data,
                    op2.version, op2.clientId)};
    } else if (op2.position < op1.position) {
      return std::vector<Operation>{op2};
    } else if (op2.position >= op1.position &&
               op2.position + deleteDataOp2.length <=
                   op1.position + deleteData.length) {
      return std::nullopt;
    } else if (op2.position > op1.position &&
               op2.position < op1.position + deleteData.length) {
      deleteDataOp2.length = (op2.position + deleteDataOp2.length) -
                             (op1.position + deleteData.length);
      return std::vector<Operation>{Operation(
          op2.type, op1.position, deleteDataOp2, op2.version, op2.clientId)};
    }
  } else if (op1.type == OperationType::INSERT &&
             op2.type == OperationType::DELETE) {
    InsertData insertData = std::get<InsertData>(op1.data);
    DeleteData deleteData = std::get<DeleteData>(op2.data);

    if (op1.position > op2.position &&
        op1.position > op2.position + deleteData.length) {
      return std::vector<Operation>{op2};
    } else if (op1.position < op2.position) {
      return std::vector<Operation>{
          Operation(op2.type, op2.position + insertData.text.size(), deleteData,
                    op2.version, op2.clientId)};
    } else if (op1.position >= op2.position &&
               op1.position <= op2.position + deleteData.length) {
      DeleteData split1Data = deleteData;
      split1Data.length = op1.position - op2.position;

      DeleteData split2Data = deleteData;
      int newOp2Position = op1.position + insertData.text.size();
      split2Data.length = deleteData.length - split1Data.length;

      Operation split1 = Operation(op2.type, op2.position, split1Data,
                                   op2.version, op2.clientId);

      Operation split2 = Operation(op2.type, newOp2Position, split2Data,
                                   op2.version, op2.clientId);

      return std::vector<Operation>{split1, split2};
    }
  } else {
    DeleteData deleteData = std::get<DeleteData>(op1.data);
    InsertData insertData = std::get<InsertData>(op2.data);

    if (op2.position > op1.position &&
        op2.position > op1.position + deleteData.length) {
      return std::vector<Operation>{
          Operation(op2.type, op2.position - deleteData.length, insertData,
                    op2.version, op2.clientId)};
    } else if (op2.position < op1.position) {
      return std::vector<Operation>{op2};
    } else if (op2.position >= op1.position &&
               op2.position <= op1.position + deleteData.length) {
      return std::vector<Operation>{Operation(
          op2.type, op1.position, insertData, op2.version, op2.clientId)};
    }
  }
  return std::nullopt;
}
