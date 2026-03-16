#include "../include/operation.h"

Operation::Operation(OperationType type, int position,
                     std::variant<InsertData, DeleteData> data, int version,
                     const std::string &clientId)
    : type(type), position(position), data(data), version(version),
      clientId(clientId) {}
